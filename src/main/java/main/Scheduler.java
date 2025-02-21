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

    private final Queue<Message> pendingFires = new LinkedList<>();

    private final int totalFires;
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
                if (!incidentQueue.isEmpty()) {
                    Message fireEvt = incidentQueue.take();
                    Logger.log("[Scheduler]", "Received Fire Event: " + fireEvt);
                    pendingFires.add(fireEvt);
                }

                if (!droneCompletionQueue.isEmpty()) {
                    Message update = droneCompletionQueue.take();
                    handleDroneUpdate(update);
                }

                dispatchIfPossible();

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
                                "All fires done & drones idle => End Program.");
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
                Logger.log("[Scheduler]", "Partial coverage => leftover= "
                        + m.getRemainingFoamNeeded());
                if (m.getRemainingFoamNeeded() > 0.0) {
                    pendingFires.add(m);
                }
                break;

            case "DRONE_IDLE":
                droneStatus.put(m.getDroneID(), DroneState.IDLE);
                Logger.log("[Scheduler]",
                        "Drone " + m.getDroneID() + " => IDLE");
                break;

            case "DRONE_EN_ROUTE":
                droneStatus.put(m.getDroneID(), DroneState.EN_ROUTE);
                break;

            case "DRONE_DROPPING":
                droneStatus.put(m.getDroneID(), DroneState.DROPPING_AGENT);
                break;

            default:
                Logger.log("[Scheduler]", "Unknown => " + m.getType());
        }
    }

    private void dispatchIfPossible() throws InterruptedException {
        if (pendingFires.isEmpty()) return;

        // find idle drone
        int idleDrone = -1;
        for (Map.Entry<Integer, DroneState> e : droneStatus.entrySet()) {
            if (e.getValue() == DroneState.IDLE) {
                idleDrone = e.getKey();
                break;
            }
        }
        if (idleDrone == -1) return;

        // pop next fire
        Message fireEvt = pendingFires.poll();

        droneStatus.put(idleDrone, DroneState.EN_ROUTE);

        Message dispatchMsg = new Message(
                fireEvt.getType(),
                idleDrone,
                fireEvt.getZoneID(),
                fireEvt.getSeverity(),
                fireEvt.getEventTime(),
                fireEvt.getEventTimeString(),
                fireEvt.getCenterX(),
                fireEvt.getCenterY(),
                fireEvt.getRemainingFoamNeeded()
        );

        Logger.log("[Scheduler]",
                "Dispatched Drone " + idleDrone + " => " + fireEvt);
        dronesQueue.put(dispatchMsg);
    }
}