package main;

import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Scheduler:
 * - Receives events (fires) from FireIncidentSubsystem.
 * - Dispatches them to drones if one is IDLE.
 * - Tracks drone states in a Map<Integer, DroneState>.
 * - On receiving FIRE_EXTINGUISHED => forwards to FireIncidentSubsystem (incidentCompletionQueue).
 * - Ends program once all fires are extinguished and all drones have returned to base.
 */
public class Scheduler implements Runnable {

    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;

    private final Map<Integer, DroneState> droneStatus;
    private final int numDrones;

    // queue of partial or new fire events
    private final Queue<Message> pendingFires = new LinkedList<>();

    private final int totalFires; // total distinct events
    private int extinguishedCount = 0;
    private boolean allFiresDone = false;

    public Scheduler(
            BlockingQueue<Message> incidentQueue,
            BlockingQueue<Message> dronesQueue,
            BlockingQueue<Message> droneCompletionQueue,
            BlockingQueue<Message> incidentCompletionQueue,
            int numDrones,
            int totalFires
    ) {
        this.incidentQueue = incidentQueue;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
        this.numDrones = numDrones;
        this.totalFires = totalFires;

        this.droneStatus = new HashMap<>();
        for (int i = 0; i < numDrones; i++) {
            droneStatus.put(i, DroneState.IDLE);
        }
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 1) ingest new fires
                if (!incidentQueue.isEmpty()) {
                    Message newFire = incidentQueue.take();
                    Logger.log("[Scheduler]", "Received Fire Event: " + newFire);
                    pendingFires.add(newFire);
                }

                // 2) handle drone updates
                if (!droneCompletionQueue.isEmpty()) {
                    Message update = droneCompletionQueue.take();
                    handleDroneUpdate(update);
                }

                // 3) attempt dispatch
                dispatchIfPossible();

                // 4) If all done, check if all drones idle => end
                if (allFiresDone) {
                    boolean allIdle = true;
                    for (DroneState st : droneStatus.values()) {
                        if (st != DroneState.IDLE) {
                            allIdle = false;
                            break;
                        }
                    }
                    if (allIdle) {
                        Logger.log("[Scheduler]",
                                "All fires done + all drones idle => End Program!");
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

        switch (m.getType()) {
            case "FIRE_EXTINGUISHED":
                extinguishedCount++;
                Logger.log("[Scheduler]",
                        "Fire extinguished => " + extinguishedCount + "/" + totalFires);

                incidentCompletionQueue.put(m);
                if (extinguishedCount >= totalFires) {
                    allFiresDone = true;
                }
                break;

            case "PARTIAL_COVERAGE":
                // leftover foam needed
                Logger.log("[Scheduler]", "Partial coverage => leftover= "
                        + m.getRemainingFoamNeeded());
                // re-queue the same event
                if (m.getRemainingFoamNeeded() > 0.0) {
                    pendingFires.add(m);
                }
                break;

            case "DRONE_IDLE":
                droneStatus.put(m.getDroneID(), DroneState.IDLE);
                Logger.log("[Scheduler]", "Drone " + m.getDroneID() + " => IDLE");
                break;

            case "DRONE_EN_ROUTE":
                droneStatus.put(m.getDroneID(), DroneState.EN_ROUTE);
                break;

            case "DRONE_DROPPING":
                droneStatus.put(m.getDroneID(), DroneState.DROPPING_AGENT);
                break;

            default:
                Logger.log("[Scheduler]", "Unknown update => " + m.getType());
        }
    }

    private void dispatchIfPossible() throws InterruptedException {
        if (pendingFires.isEmpty()) return;

        // find an idle drone
        int idleDrone = -1;
        for (Map.Entry<Integer, DroneState> e : droneStatus.entrySet()) {
            if (e.getValue() == DroneState.IDLE) {
                idleDrone = e.getKey();
                break;
            }
        }
        if (idleDrone == -1) return; // no idle drone

        // pick next pending
        Message fireEvt = pendingFires.poll();

        // mark drone EN_ROUTE
        droneStatus.put(idleDrone, DroneState.EN_ROUTE);

        // build dispatch with droneID
        Message dispatchMsg = new Message(
                fireEvt.getType(),
                idleDrone,
                fireEvt.getZoneID(),
                fireEvt.getSeverity(),
                fireEvt.getEventTime(),
                fireEvt.getEventTimeString(),
                fireEvt.getCenterX(),
                fireEvt.getCenterY(),
                fireEvt.getRemainingFoamNeeded() // carry leftover
        );

        Logger.log("[Scheduler]", "Dispatched Drone " + idleDrone + " => " + fireEvt);
        dronesQueue.put(dispatchMsg);
    }
}