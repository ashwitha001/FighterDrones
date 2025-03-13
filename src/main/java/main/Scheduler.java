package main;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Scheduler
 * 1. Receives "ACTIVE_FIRE" events from FireIncidentSubsystem => stores in `pendingFires`.
 * 2. Dispatches them to IDLE drones if available.
 * 3. Listens for drone updates (DRONE_IDLE, PARTIAL_COVERAGE, FIRE_EXTINGUISHED, etc.).
 * 4. On PARTIAL_COVERAGE => re-queues the same fire event with leftover needed.
 * 5. Tracks how many fires (totalFires) => increments extinguishedCount when FIRE_EXTINGUISHED.
 * 6. Ends the program via System.exit(0) once all fires are extinguished & all drones are IDLE.
 */
public class Scheduler implements Runnable {

    private final BlockingQueue<Message> incidentQueue;          // from FireIncidentSubsystem
    private final BlockingQueue<Message> dronesQueue;            // to DroneSubsystem
    private final BlockingQueue<Message> droneCompletionQueue;   // from DroneSubsystem
    private final BlockingQueue<Message> incidentCompletionQueue;// to FireIncidentSubsystem

    // Instead of old enum DroneState, we store string statuses:
    // "IDLE", "EN_ROUTE", "DROPPING", "BUSY", etc.
    private final Map<Integer, String> droneStatus;
    private final int numDrones;

    // queue of fires
    private final Queue<Message> pendingFires = new LinkedList<>();

    // totalFires = how many lines in events.csv, for end condition
    private final int totalFires;
    private int extinguishedCount = 0;
    private boolean allFiresDone  = false;

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
                // 1) Check if new fires arrived
                if (!incidentQueue.isEmpty()) {
                    Message newFire = incidentQueue.take();
                    Logger.log("[Scheduler]", "Received Fire Event: " + newFire);
                    pendingFires.add(newFire);
                }

                // 2) Check for drone updates
                if (!droneCompletionQueue.isEmpty()) {
                    Message update = droneCompletionQueue.take();
                    handleDroneUpdate(update);
                }

                // 3) Attempt to dispatch if any IDLE drone & pending fire
                dispatchIfPossible();

                // 4) If allFiresDone => check if all drones are "IDLE" => end program
                if (allFiresDone) {
                    boolean allIdle = true;
                    for (String status : droneStatus.values()) {
                        if (!"IDLE".equals(status)) {
                            allIdle = false;
                            break;
                        }
                    }
                    if (allIdle) {
                        Logger.log("[Scheduler]",
                                "All fires done + drones idle => System.exit(0).");
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

        switch (m.getType()) {
            case "DRONE_EN_ROUTE":
                droneStatus.put(dID, "EN_ROUTE");
                break;

            case "DRONE_DROPPING":
                droneStatus.put(dID, "DROPPING");
                break;

            case "FIRE_EXTINGUISHED":
                extinguishedCount++;
                Logger.log("[Scheduler]",
                        "Fire Extinguished => " + extinguishedCount + "/" + totalFires);
                incidentCompletionQueue.put(m);

                if (extinguishedCount >= totalFires) {
                    allFiresDone = true;
                }
                break;

            case "PARTIAL_COVERAGE":
                double remainingFoam = m.getRemainingFoamNeeded();
                if (remainingFoam > 0) {
                    Logger.log("[Scheduler]", "Requeueing fire " + m.getZoneID() + " with " + remainingFoam + " foam needed.");
                    pendingFires.add(m);
                }
                break;

            case "DRONE_IDLE":
                droneStatus.put(dID, "IDLE");
                Logger.log("[Scheduler]", "Drone " + dID + " => IDLE");
                break;

            default:
                Logger.log("[Scheduler]",
                        "Unhandled drone update => " + m.getType());
        }
    }

    private void dispatchIfPossible() throws InterruptedException {
        while (!pendingFires.isEmpty()) {
            Message fireEvt = pendingFires.poll();
            List<Integer> availableDrones = getAvailableDrones(); // get a list of all idle drones

            if (availableDrones.isEmpty()) {
                pendingFires.add(fireEvt); // no drones available, requeue fire
                return;
            }

            double foamNeeded = fireEvt.getRemainingFoamNeeded();
            double foamPerDrone = DroneSubsystem.getFoamCapacity();
            int requiredDrones = (int) Math.ceil(foamNeeded / foamPerDrone);  // calculates number of drones needed
            List<Integer> assignedDrones = new ArrayList<>();

            for (int i = 0; i < Math.min(requiredDrones, availableDrones.size()); i++) {
                assignedDrones.add(availableDrones.get(i));
            }

            for (int droneID : assignedDrones) {
                droneStatus.put(droneID, "EN_ROUTE");

                double foamToUse = Math.min(foamPerDrone, foamNeeded); // Each drone drops at most its capacity
                foamNeeded -= foamToUse; // Reduce the remaining foam needed

                // build dispatch message w/ droneID
                Message dispatchMsg = new Message(
                        fireEvt.getType(),
                        droneID,
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamToUse
                );

                Logger.log("[Scheduler]",
                        "Dispatched Drone " + droneID + " => " + fireEvt);

                dronesQueue.put(dispatchMsg);
            }

            if (foamNeeded > 0) {
                Message remainingFire = new Message(
                        fireEvt.getType(),
                        fireEvt.getDroneID(),
                        fireEvt.getZoneID(),
                        fireEvt.getSeverity(),
                        fireEvt.getEventTime(),
                        fireEvt.getEventTimeString(),
                        fireEvt.getCenterX(),
                        fireEvt.getCenterY(),
                        foamNeeded
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