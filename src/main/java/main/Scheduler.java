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
                fireTotalFoamNeeded.put(zoneId, m.getRemainingFoamNeeded());
                fireFoamApplied.put(zoneId, 0.0);
                ui.updateFireStatus(zoneId, m.getSeverity());
                break;

            case "FIRE_EXTINGUISHED":
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
                PerformanceLogger.recordEventStart(m.getEventID());
                // Only add to pending fires if not already extinguished
                if (!extinguishedFires.contains(m.getEventID())) {
                    pendingFires.add(m);
                    // Initialize foam tracking for new fire
                    fireTotalFoamNeeded.put(m.getZoneID(), m.getRemainingFoamNeeded());
                    fireFoamApplied.put(m.getZoneID(), 0.0);
                }
                break;
            case "FIRE_EXTINGUISHED":
                // Mark fire as extinguished
                extinguishedFires.add(m.getEventID());
                // Update foam tracking
                fireFoamApplied.put(m.getZoneID(), fireTotalFoamNeeded.getOrDefault(m.getZoneID(), 0.0));
                // Remove from pending fires if present
                pendingFires.removeIf(fire -> fire.getEventID().equals(m.getEventID()));
                break;
            case "FOAM_FINISHED":
            case "PARTIAL_COVERAGE":
                // Update foam tracking
                double currentApplied = fireFoamApplied.getOrDefault(m.getZoneID(), 0.0);
                double newApplied = currentApplied + m.getRemainingFoamNeeded();
                fireFoamApplied.put(m.getZoneID(), newApplied);
                
                // Check if fire is now extinguished
                double totalNeeded = fireTotalFoamNeeded.getOrDefault(m.getZoneID(), 0.0);
                if (newApplied >= totalNeeded) {
                    extinguishedFires.add(m.getEventID());
                    pendingFires.removeIf(fire -> fire.getEventID().equals(m.getEventID()));
                }
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

                // Check if there's still foam needed for this fire and requeue if necessary
                double totalFoamNeeded = fireTotalFoamNeeded.getOrDefault(m.getZoneID(), 0.0);
                double foamApplied = fireFoamApplied.getOrDefault(m.getZoneID(), 0.0);
                double remainingFoam = totalFoamNeeded - foamApplied;
                
                if (remainingFoam > 0) {
                    Logger.log("[Scheduler]", "Fire " + m.getZoneID() + " still needs " + remainingFoam + " foam. Requeuing for another drone.");
                    Message requeueFire = new Message(
                        "ACTIVE_FIRE",
                        m.getZoneID(),
                        m.getSeverity(),
                        m.getEventTime(),
                        m.getEventTimeString(),
                        m.getCenterX(),
                        m.getCenterY(),
                        remainingFoam,
                        m.getEventID(),
                        "",
                        0.0
                    );
                    pendingFires.add(requeueFire);
                }

                switch (m.getFaultType()) {
                    case "NOZZLE_JAM":
                        Logger.log("[Scheduler]", "Drone " + dID + " has a nozzle jam. Requesting return to base for repairs.");
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
                        Logger.log("[Scheduler]", "Drone " + dID + " is stuck en route. Marking as stuck and requiring manual intervention.");
                        // For STUCK_EN_ROUTE, we don't send any commands - the drone stays where it is
                        // Update UI to show drone is stuck, but don't track its location
                        if (ui != null) {
                            ui.updateDroneLocation(dID, m.getCenterX(), m.getCenterY(), "STUCK");
                        }
                        // Remove the drone from tracking since it's stuck
                        droneStatus.remove(dID);
                        droneLocations.remove(dID);
                        droneFoamMap.remove(dID);
                        break;
                    default:
                        Logger.log("[Scheduler]", "Unknown fault type for drone " + dID + ": " + m.getFaultType());
                        break;
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

        List<Integer> availableDrones = getAvailableDrones();
        if (availableDrones.isEmpty()) return;

        Message nextFire = pendingFires.peek();
        if (nextFire == null) return;

        // Check if fire is already extinguished
        if (extinguishedFires.contains(nextFire.getEventID())) {
            pendingFires.poll(); // Remove from queue
            return;
        }

        // prioritize fires: HIGH > MODERATE > LOW
        List<Message> prioritizedFires = new ArrayList<>(pendingFires);
        prioritizedFires.sort((f1, f2) -> {
            int s1 = getSeverityValue(f1.getSeverity());
            int s2 = getSeverityValue(f2.getSeverity());
            return s2 - s1; // Higher severity first
        });

        for (Message fire : prioritizedFires) {
            // Skip if no drones available
            List<Integer> availableDronesForFire = getAvailableDrones();
            if (availableDronesForFire.isEmpty()) {
                pendingFires.add(fire);
                continue;
            }

            // First try to use nearby drones that are returning with foam
            for (Integer droneId : availableDronesForFire) {
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
                if (timeToReachFromHere < timeToReachFromBase || availableFoam >= fire.getRemainingFoamNeeded()) {
                    PerformanceLogger.recordDispatchTime(fire.getEventID());
                    double foamToUse = Math.min(availableFoam, fire.getRemainingFoamNeeded());
                    sendDispatchMessage(droneId, fire, "DIVERT", foamToUse);
                    fire.setRemainingFoamNeeded(fire.getRemainingFoamNeeded() - foamToUse);
                    
                    if (fire.getRemainingFoamNeeded() <= 0) break;
                }
            }

            // If fire still needs foam, use idle drones from base
            if (fire.getRemainingFoamNeeded() > 0) {
                PerformanceLogger.recordDispatchTime(fire.getEventID());
                for (Integer droneId : availableDronesForFire) {
                    if (!"IDLE".equals(droneStatus.get(droneId))) continue;

                    double foamToUse = Math.min(DroneSubsystem.getFoamCapacity(), fire.getRemainingFoamNeeded());
                    sendDispatchMessage(droneId, fire, "DISPATCH_RECEIVED", foamToUse);
                    fire.setRemainingFoamNeeded(fire.getRemainingFoamNeeded() - foamToUse);
                    
                    if (fire.getRemainingFoamNeeded() <= 0) break;
                }
            }

            // If fire still not fully covered, requeue it
            if (fire.getRemainingFoamNeeded() > 0) {
                // Calculate new severity based on remaining foam
                String newSeverity;
                if (fire.getRemainingFoamNeeded() <= 10) {
                    newSeverity = "LOW";
                } else if (fire.getRemainingFoamNeeded() <= 20) {
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
                    fire.getRemainingFoamNeeded(),
                    fire.getEventID(),
                    "",
                    0.0
                ));
                Logger.log("[Scheduler]", "Fire " + fire.getZoneID() + 
                    " still needs " + fire.getRemainingFoamNeeded() + " foam (severity: " + newSeverity + "), requeueing.");
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
            fire.getFaultType(),
            fire.getFaultTime()
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
