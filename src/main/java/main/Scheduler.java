package main;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Scheduler implements Runnable {
    // The Scheduler listens on a fixed port specified by schedulerAddress.
    private final InetSocketAddress schedulerAddress;
    private final DatagramSocket socket;

    // Dynamically built mapping: droneID -> drone's InetSocketAddress (from registration)
    private final Map<Integer, InetSocketAddress> droneAddressesMap = new HashMap<>();
    // Internal state for each drone.
    private final Map<Integer, String> droneStatus = new HashMap<>();
    private final Map<Integer, Coordinates> droneLocations = new HashMap<>();
    private final Map<Integer, Double> droneFoamMap = new HashMap<>();

    // Queue for pending fire events and duplicate filtering for FIRE_EXTINGUISHED messages.
    private final Queue<Message> pendingFires = new LinkedList<>();
    private final Set<String> extinguishedFires = new HashSet<>();

    // (We no longer know the number of fires before runtime.)
    // Termination logic can be added later if desired.

    public Scheduler(InetSocketAddress schedulerAddress) {
        // totalFires parameter is ignored because the Scheduler doesn't know fires until they arrive.
        this.schedulerAddress = schedulerAddress;
        try {
            socket = new DatagramSocket(schedulerAddress.getPort());
            Logger.log("[Scheduler]", "Listening on port " + schedulerAddress.getPort());
        } catch (SocketException e) {
            throw new RuntimeException("Could not bind Scheduler socket on port " + schedulerAddress.getPort(), e);
        }
    }

    @Override
    public void run() {
        // Start UDPReceiver to process incoming UDP messages.
        Thread receiverThread = new Thread(new UDPReceiver(socket, this::handleMessage), "SchedulerReceiver");
        receiverThread.start();

        // Main loop: check for pending fire events and dispatch if possible.
        while (true) {
            try {
                dispatchIfPossible();
                // Optionally, termination conditions can be added here.
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Logger.log("[Scheduler]", "Interrupted => shutting down...");
                break;
            }
        }
    }

    private void handleMessage(Message m) {
        Logger.log("[Scheduler]", "Received message => " + m);
        int dID = m.getDroneID();
        String type = m.getType();
        switch (type) {
            case "ACTIVE_FIRE":
                // A fire event has been received from the FireIncidentSubsystem.
                pendingFires.add(m);
                break;
            case "DRONE_REGISTRATION":
                // Registration message: we use the centerX field to carry the ephemeral port.
                int ephemeralPort = m.getCenterX();
                InetSocketAddress droneAddr = new InetSocketAddress("localhost", ephemeralPort);
                droneAddressesMap.put(dID, droneAddr);
                droneStatus.put(dID, "IDLE");
                Logger.log("[Scheduler]", "Registered drone " + dID + " at " + droneAddr);
                break;
            case "DRONE_EN_ROUTE":
                droneStatus.put(dID, "EN_ROUTE");
                break;
            case "DRONE_DROPPING":
                droneStatus.put(dID, "DROPPING");
                break;
            case "DRONE_RETURNING":
                droneStatus.put(dID, "DIVERTIBLE");
                // Save the drone’s current location and remaining foam.
                droneLocations.put(dID, new Coordinates(m.getCenterX(), m.getCenterY()));
                droneFoamMap.put(dID, m.getRemainingFoamNeeded());
                Logger.log("[Scheduler]", "Drone " + dID + " is divertible from " +
                        droneLocations.get(dID) + " with foam " + droneFoamMap.get(dID));
                break;
            case "DRONE_IDLE":
                droneStatus.put(dID, "IDLE");
                droneLocations.remove(dID);
                droneFoamMap.remove(dID);
                Logger.log("[Scheduler]", "Drone " + dID + " => IDLE");
                break;
            case "FIRE_EXTINGUISHED":
                // Log the extinguished event.
                Logger.log("[Scheduler]", "FIRE_EXTINGUISHED received: " + m);
                // (Optionally, you could update termination state here.)
                break;
            case "PARTIAL_COVERAGE":
                if (m.getRemainingFoamNeeded() > 0) {
                    pendingFires.add(m);
                }
                break;
            default:
                Logger.log("[Scheduler]", "Unhandled message type: " + type);
        }
    }

    private void dispatchIfPossible() throws InterruptedException {
        // Process fire events from the queue.
        while (!pendingFires.isEmpty()) {
            Message fireEvt = pendingFires.poll();
            List<Integer> availableDrones = getAvailableDrones();
            if (availableDrones.isEmpty()) {
                pendingFires.add(fireEvt);
                break;
            }
            double foamNeeded = fireEvt.getRemainingFoamNeeded();
            double foamPerDrone = DroneSubsystem.getFoamCapacity();
            int requiredDrones = (int) Math.ceil(foamNeeded / foamPerDrone);
            List<Integer> assignedDrones = new ArrayList<>();
            for (int i = 0; i < Math.min(requiredDrones, availableDrones.size()); i++) {
                assignedDrones.add(availableDrones.get(i));
            }
            for (int droneID : assignedDrones) {
                String currentStatus = droneStatus.get(droneID);
                double foamToUse = Math.min(foamPerDrone, foamNeeded);
                foamNeeded -= foamToUse;
                String dispatchType = "DISPATCH_RECEIVED"; // default

                if ("DIVERTIBLE".equals(currentStatus)) {
                    double availableFoam = droneFoamMap.getOrDefault(droneID, 0.0);
                    Coordinates currLoc = droneLocations.get(droneID);
                    double tDivert = Utility.computeTravelTime(currLoc.getX1(), currLoc.getY1(), fireEvt.getCenterX(), fireEvt.getCenterY());
                    double tFromBase = Utility.computeTravelTime(0, 0, fireEvt.getCenterX(), fireEvt.getCenterY());
                    // If the drone can reach faster from its current location or its foam nearly matches the event, use DIVERT.
                    if (tDivert < tFromBase || Math.abs(availableFoam - fireEvt.getRemainingFoamNeeded()) <= 0.1) {
                        dispatchType = "DIVERT";
                    } else {
                        continue; // skip this drone for diversion
                    }
                }

                Message dispatchMsg = new Message(
                        dispatchType,
                        droneID,
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamToUse,
                        fireEvt.getEventID()
                );
                Logger.log("[Scheduler]", dispatchType + " Drone " + droneID + " => " + fireEvt);
                droneStatus.put(droneID, "EN_ROUTE");
                // Look up the drone’s registered address.
                InetSocketAddress target = droneAddressesMap.get(droneID);
                if (target != null) {
                    try {
                        UDPUtil.sendMessage(dispatchMsg, target);
                    } catch (IOException e) {
                        Logger.log("[Scheduler]", "Error sending dispatch to drone " + droneID + ": " + e.getMessage());
                    }
                } else {
                    Logger.log("[Scheduler]", "No registered address for drone " + droneID);
                }
            }
            if (foamNeeded > 0) {
                // Requeue the fire event with the remaining foam.
                Message leftoverFire = new Message(
                        fireEvt.getType(),
                        fireEvt.getDroneID(),  // might be ignored
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamNeeded,
                        fireEvt.getEventID()
                );
                pendingFires.add(leftoverFire);
                Logger.log("[Scheduler]", "Fire " + fireEvt.getZoneID() + " still needs " + foamNeeded + " foam, requeueing.");
            }
        }
    }

    private List<Integer> getAvailableDrones() {
        List<Integer> available = new ArrayList<>();
        for (Map.Entry<Integer, String> entry : droneStatus.entrySet()) {
            String status = entry.getValue();
            if ("IDLE".equals(status) || "DIVERTIBLE".equals(status)) {
                available.add(entry.getKey());
            }
        }
        return available;
    }
}