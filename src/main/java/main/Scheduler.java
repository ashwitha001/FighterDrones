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
    // Track total foam needed and applied for each fire
    private final Map<Integer, Double> fireTotalFoamNeeded = new HashMap<>();
    private final Map<Integer, Double> fireFoamApplied = new HashMap<>();

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

        int zoneId = m.getZoneID();

        switch (m.getType()) {
            case "ACTIVE_FIRE":
                // Initialize foam tracking
                PerformanceLogger.recordEventStart(m.getEventID());
                fireTotalFoamNeeded.put(zoneId, m.getRemainingFoamNeeded());
                fireFoamApplied.put(zoneId, 0.0);
                ui.updateFireStatus(zoneId, m.getSeverity());
                break;

            case "FIRE_EXTINGUISHED":
                PerformanceLogger.recordEventCompletion(m.getEventID());
                ui.updateFireStatus(zoneId, "EXTINGUISHED");
                break;

            case "FOAM_FINISHED":
            case "PARTIAL_COVERAGE": {
                // Ensure total foam and applied foam are tracked
                double totalNeeded = fireTotalFoamNeeded.getOrDefault(zoneId, 0.0);
                double applied = fireFoamApplied.getOrDefault(zoneId, 0.0);

                // Recalculate severity
                double remaining = Math.max(0, totalNeeded - applied);

                String severity;
                if (remaining == 0) {
                    severity = "EXTINGUISHED";
                } else if (remaining <= 10) {
                    severity = "LOW";
                } else if (remaining <= 20) {
                    severity = "MODERATE";
                } else {
                    severity = "HIGH";
                }

                ui.updateFireStatus(zoneId, severity);
                break;
            }

            // === Drone position/status ===
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
                Coordinates updated = new Coordinates(m.getCenterX(), m.getCenterY());
                droneLocations.put(m.getDroneID(), updated);
                ui.updateDroneLocation(m.getDroneID(), updated.getX1(), updated.getY1(), "EN_ROUTE");
                break;
        }
    }



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
                // Initialize foam tracking for new fire
                fireTotalFoamNeeded.put(m.getZoneID(), m.getRemainingFoamNeeded());
                fireFoamApplied.put(m.getZoneID(), 0.0);
                break;
            case "DRONE_REGISTRATION":
                int droneId = m.getDroneID();
                int dronePort = m.getCenterX(); // Drone's ephemeral port is passed in centerX
                InetSocketAddress droneAddress = new InetSocketAddress("localhost", dronePort);

                // Store the drone's address
                droneAddressesMap.put(droneId, droneAddress);

                // Initialize drone status and location
                droneStatus.put(droneId, "IDLE");
                droneLocations.put(droneId, new Coordinates(0, 0));
                droneFoamMap.put(droneId, DroneSubsystem.getFoamCapacity());

                // Update UI
                ui.updateDroneLocation(droneId, 0, 0, "READY");
                Logger.log("[Scheduler]", "Registered Drone " + droneId + " on port " + dronePort);
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

            case "FOAM_FINISHED":
                Logger.log("[Scheduler]", "Drone " + m.getDroneID() + " finished foam drop on fire " + m.getZoneID());

                // Determine total foam needed based on original fire severity
                double totalFoam = fireTotalFoamNeeded.getOrDefault(m.getZoneID(), 0.0);
                double droneDrop = DroneSubsystem.getFoamCapacity() - m.getRemainingFoamNeeded();

                // Update foam applied
                double appliedSoFar = fireFoamApplied.getOrDefault(m.getZoneID(), 0.0);
                appliedSoFar += droneDrop;
                fireFoamApplied.put(m.getZoneID(), appliedSoFar);

                // Determine remaining foam
                double remaining = totalFoam - appliedSoFar;
                remaining = Math.max(0.0, remaining);  // ensure non-negative

                String severity;
                if (remaining == 0.0) {
                    severity = "EXTINGUISHED";
                    extinguishedFires.add(String.valueOf(m.getZoneID()));

                    Message extinguishedMsg = new Message(
                            "FIRE_EXTINGUISHED",
                            m.getDroneID(),
                            m.getZoneID(),
                            severity,
                            m.getEventTime(),
                            m.getEventTimeString(),
                            m.getCenterX(),
                            m.getCenterY(),
                            0.0,
                            m.getEventID(),
                            "",
                            0.0
                    );

                    try {
                        UDPUtil.sendMessage(extinguishedMsg, fireIncidentAddress);
                        Logger.log("[Scheduler]", "Fire " + m.getZoneID() + " extinguished.");
                    } catch (IOException e) {
                        Logger.log("[Scheduler]", "Error notifying FIRE_EXTINGUISHED: " + e.getMessage());
                    }

                } else {
                    // Partial coverage
                    if (remaining <= 10.0) {
                        severity = "LOW";
                    } else if (remaining <= 20.0) {
                        severity = "MODERATE";
                    } else {
                        severity = "HIGH";
                    }

                    // Requeue fire for further coverage
                    Message requeue = new Message(
                            "PARTIAL_COVERAGE",
                            m.getDroneID(),
                            m.getZoneID(),
                            severity,
                            m.getEventTime(),
                            m.getEventTimeString(),
                            m.getCenterX(),
                            m.getCenterY(),
                            remaining,
                            m.getEventID(),
                            "",
                            0.0
                    );
                    pendingFires.add(requeue);
                    Logger.log("[Scheduler]", "Requeued fire " + m.getZoneID() + " with " + remaining + "L foam left (" + severity + ")");
                }

                // UI update with new severity
                ui.updateFireStatus(m.getZoneID(), severity);

                // Mark drone as available for diversion
                droneStatus.put(m.getDroneID(), "DIVERTIBLE");
                droneLocations.put(m.getDroneID(), new Coordinates(m.getCenterX(), m.getCenterY()));
                droneFoamMap.put(m.getDroneID(), m.getRemainingFoamNeeded());
                Logger.log("[Scheduler]", "Drone " + m.getDroneID() + " is now DIVERTIBLE at (" + m.getCenterX() + ", " + m.getCenterY() + ") with foam: " + m.getRemainingFoamNeeded());

                break;

            case "PARTIAL_COVERAGE":
                // Get the total foam needed for this fire based on initial severity
                double totalFoamNeededPC;
                switch (m.getSeverity()) {
                    case "HIGH":
                        totalFoamNeededPC = 30.0;
                        break;
                    case "MODERATE":
                        totalFoamNeededPC = 20.0;
                        break;
                    case "LOW":
                        totalFoamNeededPC = 10.0;
                        break;
                    default:
                        totalFoamNeededPC = 0.0;
                }

                // Get the foam applied so far
                double foamAppliedSoFarPC = fireFoamApplied.getOrDefault(m.getZoneID(), 0.0);

                // Calculate remaining foam needed
                double remainingFoamPC = totalFoamNeededPC - foamAppliedSoFarPC;
                String severityPC;

                // Calculate severity based on remaining foam needed
                if (remainingFoamPC <= 0) {
                    severityPC = "EXTINGUISHED";
                } else if (remainingFoamPC <= 10) {
                    severityPC = "LOW";
                } else if (remainingFoamPC <= 20) {
                    severityPC = "MODERATE";
                } else {
                    severityPC = "HIGH";
                }

                // Update the UI with the new severity
                ui.updateFireStatus(m.getZoneID(), severityPC);

                // Requeue the fire if it still needs foam
                if (remainingFoamPC > 0) {
                    Message updatedFire = new Message(
                            m.getType(),
                            m.getDroneID(),
                            m.getZoneID(),
                            severityPC,
                            m.getEventTime(),
                            m.getEventTimeString(),
                            m.getCenterX(),
                            m.getCenterY(),
                            remainingFoamPC,
                            m.getEventID(),
                            "",
                            0.0
                    );
                    pendingFires.add(updatedFire);
                    Logger.log("[Scheduler]", "Requeuing fire " + m.getZoneID() +
                            " with " + remainingFoamPC + " foam needed (severity: " + severityPC + ")");
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

            double totalNeeded = switch (fire.getSeverity()) {
                case "HIGH" -> 30.0;
                case "MODERATE" -> 20.0;
                case "LOW" -> 10.0;
                default -> 0.0;
            };

            double applied = fireFoamApplied.getOrDefault(fire.getZoneID(), 0.0);
            double remainingFoam = totalNeeded - applied;
            remainingFoam = Math.max(0.0, remainingFoam);

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
                    PerformanceLogger.recordDispatchTime(fire.getEventID());
                    sendDispatchMessage(droneId, fire, "DISPATCH_RECEIVED", foamToUse);
                    remainingFoam -= foamToUse;

                    if (remainingFoam <= 0) break;
                }
            }

            // If fire still not fully covered, requeue it
            if (remainingFoam > 0) {
                // Calculate new severity based on remaining foam
                String newSeverity;
                if (remainingFoam <= 10) {
                    newSeverity = "LOW";
                } else if (remainingFoam <= 20) {
                    newSeverity = "MODERATE";
                } else {
                    newSeverity = "HIGH";
                }

                pendingFires.add(new Message(
                        fire.getType(),
                        fire.getDroneID(),
                        fire.getZoneID(),
                        newSeverity,  // Use the new severity based on remaining foam
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
                        " still needs " + remainingFoam + " foam (severity: " + newSeverity + "), requeueing.");
            }
        }
    }

    // Convert fire severity to int
    private int getSeverityValue(String severity) {
        double needed;
        switch (severity) {
            case "LOW":       needed = 10.0; break;
            case "MODERATE":  needed = 20.0; break;
            case "HIGH":      needed = 30.0; break;
            default:          needed = 10.0;
        }
        return (int) Math.round(needed);
    }

    // Send dispatch command to a drone
    private void sendDispatchMessage(int droneId, Message fire, String dispatchType, double foamAmount) {
        // Calculate severity based on remaining foam needed
        String severity;
        if (fire.getRemainingFoamNeeded() <= 0) {
            severity = "EXTINGUISHED";
        } else if (fire.getRemainingFoamNeeded() <= 10) {
            severity = "LOW";
        } else if (fire.getRemainingFoamNeeded() <= 20) {
            severity = "MODERATE";
        } else {
            severity = "HIGH";
        }

        Message dispatch = new Message(
                dispatchType,
                droneId,
                fire.getZoneID(),
                severity,
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