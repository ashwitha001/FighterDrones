package main;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Scheduler
 * 1. Receives "ACTIVE_FIRE" events from FireIncidentSubsystem and stores them in pendingFires.
 * 2. Dispatches them to available (IDLE) drones—splitting a fire across multiple drones if needed.
 * 3. Listens for drone updates (DRONE_IDLE, PARTIAL_COVERAGE, FIRE_EXTINGUISHED, etc.).
 * 4. On PARTIAL_COVERAGE, re-queues the same fire event with the leftover foam needed.
 * 5. Tracks how many fires (totalFires) by incrementing extinguishedCount when FIRE_EXTINGUISHED is received,
 *    with duplicate messages filtered using event IDs.
 * 6. Ends the program via System.exit(0) once all fires are extinguished and all drones are IDLE.
 */
public class Scheduler implements Runnable {

    private final BlockingQueue<Message> incidentQueue;           // from FireIncidentSubsystem
    private final BlockingQueue<Message> dronesQueue;             // to DroneSubsystem
    private final BlockingQueue<Message> droneCompletionQueue;    // from DroneSubsystem
    private final BlockingQueue<Message> incidentCompletionQueue; // to FireIncidentSubsystem

    // Internal drone status: "IDLE", "EN_ROUTE", "DROPPING", "DIVERTIBLE", etc.
    private final Map<Integer, String> droneStatus;
    private final int numDrones;
    // Map to store current location for DIVERTIBLE drones.
    private final Map<Integer, Coordinates> droneLocations;
    // Map to store the remaining foam for DIVERTIBLE drones.
    private final Map<Integer, Double> droneFoamMap;

    // Duplicate filtering for FIRE_EXTINGUISHED messages.
    private final Set<String> extinguishedFires = new HashSet<>();

    // Queue for pending fire events.
    private final Queue<Message> pendingFires = new LinkedList<>();

    private final int totalFires;
    private int extinguishedCount = 0;
    private boolean allFiresDone = false;

    public Scheduler(BlockingQueue<Message> incidentQueue,
                     BlockingQueue<Message> dronesQueue,
                     BlockingQueue<Message> droneCompletionQueue,
                     BlockingQueue<Message> incidentCompletionQueue,
                     int numDrones,
                     int totalFires) {
        this.incidentQueue = incidentQueue;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
        this.numDrones = numDrones;
        this.totalFires = totalFires;

        droneStatus = new HashMap<>();
        droneLocations = new HashMap<>();
        droneFoamMap = new HashMap<>();
        for (int i = 0; i < numDrones; i++) {
            droneStatus.put(i, "IDLE");
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 1) Check for new fire events.
                if (!incidentQueue.isEmpty()) {
                    Message newFire = incidentQueue.take();
                    Logger.log("[Scheduler]", "Received Fire Event: " + newFire);
                    pendingFires.add(newFire);
                }

                // 2) Process drone update messages.
                if (!droneCompletionQueue.isEmpty()) {
                    Message update = droneCompletionQueue.take();
                    handleDroneUpdate(update);
                }

                // 3) Attempt to dispatch fire events.
                dispatchIfPossible();

                // 4) Termination check: if all fires are done and all drones are IDLE.
                if (allFiresDone) {
                    boolean allIdle = true;
                    for (String status : droneStatus.values()) {
                        if (!"IDLE".equals(status)) {
                            allIdle = false;
                            break;
                        }
                    }
                    if (allIdle) {
                        Logger.log("[Scheduler]", "All fires done + drones idle => System.exit(0).");
                        System.exit(0);
                    }
                }
                Thread.sleep(50);
            } catch (InterruptedException e) {
                Logger.log("[Scheduler]", "Interrupted => shutting down...");
                break;
            }
        }
    }

    private void handleDroneUpdate(Message m) throws InterruptedException {
        Logger.log("[Scheduler]", "Drone update => " + m);
        int dID = m.getDroneID();
        String type = m.getType();
        switch (type) {
            case "DRONE_EN_ROUTE":
                droneStatus.put(dID, "EN_ROUTE");
                break;
            case "DRONE_DROPPING":
                droneStatus.put(dID, "DROPPING");
                break;
            case "DRONE_RETURNING":
                droneStatus.put(dID, "DIVERTIBLE");
                // Record the drone's current location and foam remaining (as reported in the message)
                droneLocations.put(dID, new Coordinates(m.getCenterX(), m.getCenterY()));
                droneFoamMap.put(dID, m.getRemainingFoamNeeded());
                Logger.log("[Scheduler]", "Drone " + dID + " is divertible (returning with foam) from location "
                        + droneLocations.get(dID) + " with foam " + droneFoamMap.get(dID));
                break;
            case "DRONE_IDLE":
                droneStatus.put(dID, "IDLE");
                droneLocations.remove(dID);
                droneFoamMap.remove(dID);
                Logger.log("[Scheduler]", "Drone " + dID + " => IDLE");
                break;
            case "FIRE_EXTINGUISHED":
                synchronized (extinguishedFires) {
                    if (!extinguishedFires.contains(m.getEventID())) {
                        extinguishedCount++;
                        extinguishedFires.add(m.getEventID());
                        Logger.log("[Scheduler]", "Fire Extinguished => " + extinguishedCount + "/" + totalFires);
                        if (extinguishedCount >= totalFires) {
                            allFiresDone = true;
                        }
                    }
                }
                incidentCompletionQueue.put(m);
                break;
            case "PARTIAL_COVERAGE":
                if (m.getRemainingFoamNeeded() > 0) {
                    pendingFires.add(m);
                }
                break;
            default:
                Logger.log("[Scheduler]", "Unhandled drone update => " + m.getType());
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
                String dispatchType = "DISPATCH_RECEIVED"; // default command

                if ("DIVERTIBLE".equals(currentStatus)) {
                    // Get the drone's current remaining foam from our map.
                    double availableFoam = droneFoamMap.getOrDefault(droneID, 0.0);
                    // Get the drone's current location.
                    Coordinates currentLoc = droneLocations.get(droneID);
                    // Compute travel time from the drone's current location to the fire event.
                    double tDivert = Utility.computeTravelTime(currentLoc.getX1(), currentLoc.getY1(), fireEvt.getCenterX(), fireEvt.getCenterY());
                    // Compute travel time from base (assumed (0,0)) to the fire event.
                    double tFromBase = Utility.computeTravelTime(0, 0, fireEvt.getCenterX(), fireEvt.getCenterY());
                    // If diversion saves time or if the drone's available foam matches the fire's requirement, then divert.
                    // (Here we use a tolerance of 0.1 kg for foam match.)
                    if (tDivert < tFromBase || Math.abs(availableFoam - fireEvt.getRemainingFoamNeeded()) <= 15) {
                        dispatchType = "DIVERT";
                    } else {
                        // If diversion isn't beneficial, skip this drone for diversion.
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
                        fireEvt.getEventID()
                );
                Logger.log("[Scheduler]", dispatchType + " Drone " + droneID + " => " + fireEvt);
                droneStatus.put(droneID, "EN_ROUTE");
                dronesQueue.put(dispatchMsg);
            }
            if (foamNeeded > 0) {
                Message remainingFire = new Message(
                        fireEvt.getType(),
                        fireEvt.getDroneID(), // original droneID if applicable; may be ignored
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamNeeded,
                        fireEvt.getEventID()
                );
                pendingFires.add(remainingFire);
                Logger.log("[Scheduler]", "Fire " + fireEvt.getZoneID() + " still needs " + foamNeeded + " foam, requeueing.");
            }
        }
    }

    private List<Integer> getAvailableDrones() {
        List<Integer> available = new ArrayList<>();
        for (Map.Entry<Integer, String> e : droneStatus.entrySet()) {
            String status = e.getValue();
            if ("IDLE".equals(status) || "DIVERTIBLE".equals(status)) {
                available.add(e.getKey());
            }
        }
        return available;
    }
}