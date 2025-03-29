package main;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Scheduler implements Runnable {
    private final InetSocketAddress schedulerAddress;
    private final DatagramSocket socket;

    // Mapping from droneID to its InetSocketAddress.
    private final Map<Integer, InetSocketAddress> droneAddressesMap = new HashMap<>();
    // Drone statuses.
    private final Map<Integer, String> droneStatus = new HashMap<>();
    private final Map<Integer, Coordinates> droneLocations = new HashMap<>();
    private final Map<Integer, Double> droneFoamMap = new HashMap<>();

    // Queue for pending fire events.
    private final Queue<Message> pendingFires = new LinkedList<>();
    private final Set<String> extinguishedFires = new HashSet<>();

    // FireIncidentSubsystem is assumed to run at localhost:5001.
    private final InetSocketAddress fireIncidentAddress = new InetSocketAddress("localhost", 5001);

    public Scheduler(InetSocketAddress schedulerAddress) {
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
        Thread receiverThread = new Thread(new UDPReceiver(socket, this::handleMessage), "SchedulerReceiver");
        receiverThread.start();

        while (true) {
            try {
                dispatchIfPossible();
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
                pendingFires.add(m);
                break;
            case "DRONE_REGISTRATION":
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
            case "DRONE_FAULT":
                Logger.log("[Scheduler]", "Fault reported by drone " + dID + ": " + m.getFaultType());
                droneStatus.put(dID, "OFFLINE");
                Logger.log("[Scheduler]", "Drone " + dID + " marked as OFFLINE.");
                // Send a shutdown command to the faulty drone.
                Message shutdownMsg = new Message(
                        "SHUTDOWN",
                        dID,
                        0,
                        "SHUTDOWN",
                        m.getEventTime(),
                        m.getEventTimeString(),
                        0,
                        0,
                        0.0,
                        m.getEventID(),
                        "", 0.0
                );
                InetSocketAddress target = droneAddressesMap.get(dID);
                if (target != null) {
                    try {
                        UDPUtil.sendMessage(shutdownMsg, target);
                        Logger.log("[Scheduler]", "Sent SHUTDOWN command to drone " + dID);
                        Message fire = new Message(
                                "ACTIVE_FIRE",
                                m.getZoneID(),
                                m.getSeverity(),
                                m.getEventTime(),
                                m.getEventTimeString(),
                                m.getCenterX(),
                                m.getCenterY(),
                                m.getRemainingFoamNeeded(),
                                m.getEventID(),
                                "",
                                0
                        );
                        if (!(m.getRemainingFoamNeeded() == 0)) {
                            Logger.log("[Scheduler]", "Requeue fire, drone shutdown");
                            pendingFires.add(fire);
                        }
                    } catch (IOException e) {
                        Logger.log("[Scheduler]", "Error sending SHUTDOWN to drone " + dID + ": " + e.getMessage());
                    }
                }
                break;
            case "FIRE_EXTINGUISHED":
                Logger.log("[Scheduler]", "FIRE_EXTINGUISHED received: " + m);
                try {
                    UDPUtil.sendMessage(m, fireIncidentAddress);
                    Logger.log("[Scheduler]", "Forwarded FIRE_EXTINGUISHED to FireIncidentSubsystem.");
                } catch (IOException e) {
                    Logger.log("[Scheduler]", "Error forwarding FIRE_EXTINGUISHED: " + e.getMessage());
                }
                break;
            case "PARTIAL_COVERAGE":
                if (m.getRemainingFoamNeeded() > 0) {
                    pendingFires.add(m);
                }
                break;
            case "INCIDENT_CONFIRMED":
                Logger.log("[Scheduler]", "Received INCIDENT_CONFIRMED: " + m);
                break;
            default:
                Logger.log("[Scheduler]", "Unhandled message type: " + type);
        }
    }

    private void dispatchIfPossible() throws InterruptedException {
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
                String dispatchType = "DISPATCH_RECEIVED";

                if ("DIVERTIBLE".equals(currentStatus)) {
                    double availableFoam = droneFoamMap.getOrDefault(droneID, 0.0);
                    Coordinates currLoc = droneLocations.get(droneID);
                    double tDivert = Utility.computeTravelTime(currLoc.getX1(), currLoc.getY1(), fireEvt.getCenterX(), fireEvt.getCenterY());
                    double tFromBase = Utility.computeTravelTime(0, 0, fireEvt.getCenterX(), fireEvt.getCenterY());
                    if (tDivert < tFromBase || Math.abs(availableFoam - fireEvt.getRemainingFoamNeeded()) <= 0.1) {
                        dispatchType = "DIVERT";
                    } else {
                        continue;
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
                        fireEvt.getEventID(),
                        fireEvt.getFaultType(),
                        fireEvt.getFaultTime()
                );
                Logger.log("[Scheduler]", dispatchType + " Drone " + droneID + " => " + fireEvt);
                droneStatus.put(droneID, "EN_ROUTE");
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
                                // If this fire has a fault, requeue it once with cleared fault info
                if (fireEvt.getFaultType() != null && !fireEvt.getFaultType().isEmpty() && foamNeeded > 0) {
                    Message retryFire = new Message(
                            fireEvt.getType(),
                            0,
                            fireEvt.getZoneID(),
                            fireEvt.getSeverity(),
                            fireEvt.getEventTime(),
                            fireEvt.getEventTimeString(),
                            fireEvt.getCenterX(),
                            fireEvt.getCenterY(),
                            foamNeeded,  // Add back full foam needed
                            fireEvt.getEventID(),
                            "", // Clear the fault type
                            fireEvt.getFaultTime()
                    );
                    pendingFires.add(retryFire);
                    Logger.log("[Scheduler]", "Requeued fire with cleared faultType: " + retryFire);
                    break;
                }
            }
            if (foamNeeded > 0 && fireEvt.getFaultType().isEmpty()) {
                Message leftoverFire = new Message(
                        fireEvt.getType(),
                        fireEvt.getDroneID(),
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamNeeded,
                        fireEvt.getEventID(),
                        fireEvt.getFaultType(),
                        fireEvt.getFaultTime()
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
