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

    // Drone status map (our internal view): "IDLE", "EN_ROUTE", "DROPPING", etc.
    private final Map<Integer, String> droneStatus;
    private final int numDrones;

    // Set to track processed event IDs to filter duplicate FIRE_EXTINGUISHED messages.
    private final Set<String> extinguishedFires = new HashSet<>();

    // Queue of pending fire events
    private final Queue<Message> pendingFires = new LinkedList<>();

    // Total number of fires (from, e.g., events.csv)
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

        this.droneStatus = new HashMap<>();
        for (int i = 0; i < numDrones; i++) {
            droneStatus.put(i, "IDLE");
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 1) Check for new fire events from the FireIncidentSubsystem.
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

                // 3) Attempt to dispatch fire events to available drones.
                dispatchIfPossible();

                // 4) Termination check: if all fires are done and all drones are IDLE, exit.
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
        String eventID = m.getEventID(); // Assumes Message provides this

        switch (m.getType()) {
            case "DRONE_EN_ROUTE":
                droneStatus.put(dID, "EN_ROUTE");
                break;

            case "DRONE_DROPPING":
                droneStatus.put(dID, "DROPPING");
                break;

            case "FIRE_EXTINGUISHED":
                synchronized (extinguishedFires) {
                    if (!extinguishedFires.contains(eventID)) {
                        extinguishedCount++;
                        extinguishedFires.add(eventID);
                        Logger.log("[Scheduler]", "Fire Extinguished => " + extinguishedCount + "/" + totalFires);
                        if (extinguishedCount >= totalFires) {
                            allFiresDone = true;
                        }
                    }
                }
                incidentCompletionQueue.put(m);
                break;

            case "PARTIAL_COVERAGE":
                double remainingFoam = m.getRemainingFoamNeeded();
                // If there is still foam needed, re-queue the fire event.
                if (remainingFoam > 0) {
                    pendingFires.add(m);
                }
                break;

            case "DRONE_IDLE":
                droneStatus.put(dID, "IDLE");
                Logger.log("[Scheduler]", "Drone " + dID + " => IDLE");
                break;

            default:
                Logger.log("[Scheduler]", "Unhandled drone update => " + m.getType());
        }
    }

    private void dispatchIfPossible() throws InterruptedException {
        while (!pendingFires.isEmpty()) {
            Message fireEvt = pendingFires.poll();
            // Get a list of all available (IDLE) drones.
            List<Integer> availableDrones = getAvailableDrones();

            // If no drones are available, re-queue the fire event and exit the loop.
            if (availableDrones.isEmpty()) {
                pendingFires.add(fireEvt);
                break;
            }

            double foamNeeded = fireEvt.getRemainingFoamNeeded();
            double foamPerDrone = DroneSubsystem.getFoamCapacity();
            int requiredDrones = (int) Math.ceil(foamNeeded / foamPerDrone);
            List<Integer> assignedDrones = new ArrayList<>();

            // Assign as many drones as possible (up to requiredDrones).
            for (int i = 0; i < Math.min(requiredDrones, availableDrones.size()); i++) {
                assignedDrones.add(availableDrones.get(i));
            }

            // Dispatch each assigned drone.
            for (int droneID : assignedDrones) {
                droneStatus.put(droneID, "EN_ROUTE");
                double foamToUse = Math.min(foamPerDrone, foamNeeded);
                foamNeeded -= foamToUse;
                // Build dispatch message with type "DISPATCH_RECEIVED"
                Message dispatchMsg = new Message(
                        "DISPATCH_RECEIVED",  // fixed type for compatibility with DroneSubsystem
                        droneID,
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamToUse,
                        fireEvt.getEventID()  // include eventID for duplicate handling
                );
                Logger.log("[Scheduler]", "Dispatched Drone " + droneID + " => " + fireEvt);
                dronesQueue.put(dispatchMsg);
            }

            // If there is leftover foam needed, re-queue the fire event with the remaining foam.
            if (foamNeeded > 0) {
                Message remainingFire = new Message(
                        fireEvt.getType(),
                        fireEvt.getDroneID(), // you may use the original droneID if applicable
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
        List<Integer> idleDrones = new ArrayList<>();
        for (Map.Entry<Integer, String> e : droneStatus.entrySet()) {
            if ("IDLE".equals(e.getValue())) {
                idleDrones.add(e.getKey());
            }
        }
        return idleDrones;
    }
}
