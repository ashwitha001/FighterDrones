package main;

import java.io.IOException;
import java.net.*;
import java.util.*;

public class Scheduler implements Runnable {
    private final InetSocketAddress schedulerAddress;
    private final DatagramSocket socket;
    private final SimulationUI ui;

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

    public Scheduler(InetSocketAddress schedulerAddress, SimulationUI ui) {
        this.schedulerAddress = schedulerAddress;
        this.ui = ui;
        try {
            socket = new DatagramSocket(schedulerAddress.getPort());
            Logger.log("[Scheduler]", "Listening on port " + schedulerAddress.getPort());
        } catch (SocketException e) {
            throw new RuntimeException("Could not bind Scheduler socket on port " + schedulerAddress.getPort(), e);
        }
    }
    private void updateUI(Message m) {
        if (m == null) return;

        switch (m.getType()) {
            case "ACTIVE_FIRE":
                ui.updateFireStatus(m.getZoneID(), m.getSeverity());
                break;

            case "FIRE_EXTINGUISHED":
                ui.updateFireStatus(m.getZoneID(), "EXTINGUISHED");
                break;

            // === DRONE STATE UPDATES ===

            case "DRONE_REGISTERED":
            case "DRONE_READY":
                ui.updateDroneLocation(m.getDroneID(), m.getCenterX(), m.getCenterY(), "READY");
                break;

            case "DISPATCH_RECEIVED":
                ui.updateDroneLocation(m.getDroneID(), m.getCenterX(), m.getCenterY(), "OUTBOUND");
                break;

            case "EXTINGUISHING":
                ui.updateDroneLocation(m.getDroneID(), m.getCenterX(), m.getCenterY(), "EXTINGUISHING");
                break;

            case "RETURNING":
                ui.updateDroneLocation(m.getDroneID(), m.getCenterX(), m.getCenterY(), "RETURNING");
                break;

            case "DIVERT":
                ui.updateDroneLocation(m.getDroneID(), m.getCenterX(), m.getCenterY(), "DIVERTED");
                break;

            case "DRONE_COORD_UPDATE":
                // Store latest location in map
                Coordinates updated = new Coordinates(m.getCenterX(), m.getCenterY());
                droneLocations.put(m.getDroneID(), updated);

                // Update Simulation UI
                if (ui != null) {
                    ui.updateDroneLocation(m.getDroneID(), updated.getX1(), updated.getY1(), "EN_ROUTE");
                }

                Logger.log("[Scheduler]", "Updated position for drone " + m.getDroneID() + ": " + updated);
                break;
        }
    }


//    private void updateUI(Message m) {
//        if (m == null) return;
//
//        switch (m.getType()) {
//            case "ACTIVE_FIRE":
//                ui.updateFireStatus(m.getZoneID(), m.getSeverity());
//                break;
//            case "FIRE_EXTINGUISHED":
//                ui.updateFireStatus(m.getZoneID(), "EXTINGUISHED");
//                break;
//            // have not implemented drone ui yet...
//            case "DRONE_REGISTERED":
//            case "DRONE_READY":
//            case "DISPATCH_RECEIVED":
//            case "DIVERT":
//            case "RETURNING":
//            case "EXTINGUISHING":
//            case "DRONE_COORD_UDPATING":
//                break;
//        }
//    }

    @Override
    public void run() {
        Thread receiverThread = new Thread(new UDPReceiver(socket, m -> {
            handleMessage(m);
            updateUI(m);
        }), "SchedulerReceiver");
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
                switch (m.getFaultType()) {
                    case "NOZZLE_JAM":
                        Logger.log ("[Scheduler]", "Drone " + dID + " has a nozzle jam. Requesting return to base for repairs.");
                        Message returnMsg = new Message(
                                "RETURN_TO_BASE",
                                dID,
                                0,
                                "RETURNING",
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
                                UDPUtil.sendMessage(returnMsg, target);
                                Logger.log("[Scheduler]", "Sent RETURN_TO_BASE command to drone " + dID);
                            } catch (IOException e) {
                                Logger.log("[Scheduler]", "Error sending RETURN_TO_BASE to drone " + dID + ": " + e.getMessage());
                            }
                        }
                        break;
                    case "STUCK_EN_ROUTE":
                        Logger.log("[Scheduler]", "Drone " + dID + " is stuck en route. Shutting down for manual intervention.");
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
                        target = droneAddressesMap.get(dID);
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
                    case "COMMS_FAULT":
                        Logger.log("[Scheduler]", "Drone " + dID + " has a comms fault. Resetting connection.");
                        Message resetMsg = new Message(
                                "RESET_CONNECTION",
                                dID,
                                0,
                                "RESET",
                                m.getEventTime(),
                                m.getEventTimeString(),
                                0,
                                0,
                                0.0,
                                m.getEventID(),
                                "", 0.0
                        );
                        target = droneAddressesMap.get(dID);
                        if (target != null) {
                            try {
                                UDPUtil.sendMessage(resetMsg, target);
                                Logger.log("[Scheduler]", "Sent RESET_CONNECTION command to drone " + dID);
                            } catch (IOException e) {
                                Logger.log("[Scheduler]", "Error sending RESET_CONNECTION to drone " + dID + ": " + e.getMessage());
                            }
                        }
                        break;
                    default:
                        Logger.log("[Scheduler]", "Unknown fault type for drone " + dID + ": " + m.getFaultType());
                        break;
                }

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
        if (pendingFires.isEmpty()) return;

        // prioritize fires: HIGH > MODERATE > LOW
        List<Message> prioritizedFires = new ArrayList<>();
        while (!pendingFires.isEmpty()) {
            prioritizedFires.add(pendingFires.poll());
        }
        prioritizedFires.sort((a, b) -> getSeverityValue(b.getSeverity()) - getSeverityValue(a.getSeverity()));

        // Process each fire in priority order
        for (Message fire : prioritizedFires) {
            // Skip if no drones available
            List<Integer> availableDrones = getAvailableDrones();
            if (availableDrones.isEmpty()) {
                pendingFires.add(fire);
                continue;
            }

            double remainingFoam = fire.getRemainingFoamNeeded();

            // First try to use nearby drones that are returning with foam
            for (Integer droneId : availableDrones) {
                if (!"DIVERTIBLE".equals(droneStatus.get(droneId))) continue;

                // Check if diversion is efficient
                Coordinates droneLoc = droneLocations.get(droneId);
                double timeToReachFromHere = Utility.computeTravelTime(
                    droneLoc.getX1(), droneLoc.getY1(), 
                    fire.getCenterX(), fire.getCenterY()
                );
                double timeToReachFromBase = Utility.computeTravelTime(
                    0, 0, 
                    fire.getCenterX(), fire.getCenterY()
                );
                double availableFoam = droneFoamMap.getOrDefault(droneId, 0.0);

                // Divert drone if it's closer or has enough foam
                if (timeToReachFromHere < timeToReachFromBase || availableFoam >= remainingFoam) {
                    double foamToUse = Math.min(availableFoam, remainingFoam);
                    sendDispatchMessage(droneId, fire, "DIVERT", foamToUse);
                    remainingFoam -= foamToUse;
                    
                    if (remainingFoam <= 0) break;
                }
            }

            // If fire still needs foam, use idle drones from base
            if (remainingFoam > 0) {
                for (Integer droneId : availableDrones) {
                    if (!"IDLE".equals(droneStatus.get(droneId))) continue;

                    double foamToUse = Math.min(DroneSubsystem.getFoamCapacity(), remainingFoam);
                    sendDispatchMessage(droneId, fire, "DISPATCH_RECEIVED", foamToUse);
                    remainingFoam -= foamToUse;
                    
                    if (remainingFoam <= 0) break;
                }
            }

            // If fire still not fully covered, requeue it
            if (remainingFoam > 0) {
                pendingFires.add(new Message(
                    fire.getType(),
                    fire.getDroneID(),
                    fire.getZoneID(),
                    fire.getSeverity(),
                    fire.getEventTime(),
                    fire.getEventTimeString(),
                    fire.getCenterX(),
                    fire.getCenterY(),
                    remainingFoam,
                    fire.getEventID(),
                    "",
                    0.0
                ));
                Logger.log("[Scheduler]", "Fire " + fire.getZoneID() + 
                    " still needs " + remainingFoam + " foam, requeueing.");
            }
        }
    }

    // Convert fire severity to int
    private int getSeverityValue(String severity) {
        return switch (severity) {
            case "HIGH" -> 3;
            case "MODERATE" -> 2;
            case "LOW" -> 1;
            default -> 0;
        };
    }

    // Send dispatch command to a drone
    private void sendDispatchMessage(int droneId, Message fire, String dispatchType, double foamAmount) {
        Message dispatch = new Message(
            dispatchType,
            droneId,
            fire.getZoneID(),
            fire.getSeverity(),
            fire.getEventTime(),
            fire.getEventTimeString(),
            fire.getCenterX(),
            fire.getCenterY(),
            foamAmount,
            fire.getEventID(),
            "",
            0.0
        );
        
        Logger.log("[Scheduler]", dispatchType + " Drone " + droneId + " => " + fire);
        droneStatus.put(droneId, "EN_ROUTE");
        
        InetSocketAddress target = droneAddressesMap.get(droneId);
        if (target != null) {
            try {
                UDPUtil.sendMessage(dispatch, target);
            } catch (IOException e) {
                Logger.log("[Scheduler]", "Error sending dispatch to drone " + droneId + ": " + e.getMessage());
            }
        } else {
            Logger.log("[Scheduler]", "No registered address for drone " + droneId);
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

    public SimulationUI getUI() {
        return ui;
    }

}
