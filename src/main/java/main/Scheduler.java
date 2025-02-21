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

    private final BlockingQueue<Message> incidentQueue;         // from FireIncidentSubsystem
    private final BlockingQueue<Message> dronesQueue;           // to DroneSubsystem
    private final BlockingQueue<Message> droneCompletionQueue;  // from DroneSubsystem
    private final BlockingQueue<Message> incidentCompletionQueue; // to FireIncidentSubsystem

    private final Map<Integer, DroneState> droneStatus;
    private final int numDrones;

    private final Queue<Message> pendingFires = new LinkedList<>();

    // totalFires => how many lines were in events.csv
    private final int totalFires;
    private int extinguishedCount = 0;
    private boolean allFiresDone  = false;

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
                // 1) Check if new events from FireIncidentSubsystem
                if (!incidentQueue.isEmpty()) {
                    Message newFire = incidentQueue.take();
                    Logger.log("[Scheduler]", "Received Fire Event: " + newFire);
                    pendingFires.add(newFire);
                }

                // 2) Check for updates from DroneSubsystem
                if (!droneCompletionQueue.isEmpty()) {
                    Message update = droneCompletionQueue.take();
                    handleDroneUpdate(update);
                }

                // 3) Attempt to dispatch if we have IDLE drone & a pending fire
                dispatchIfPossible();

                // 4) If allFiresDone => check if all drones IDLE => end
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
                                "All fires done & drones idle => End Program!");
                        System.exit(0);
                    }
                }

                // small sleep to avoid busy loop
                Thread.sleep(50);

            } catch (InterruptedException e) {
                Logger.log("[Scheduler]", "Interrupted => shutting down...");
                break;
            }
        }
    }

    /**
     * Handles messages from drones:
     * - FIRE_EXTINGUISHED => increments count, forward to FireIncidentSubsystem
     * - PARTIAL_COVERAGE => leftover needed => re-add to pendingFires
     * - DRONE_IDLE => mark drone idle
     * - DRONE_EN_ROUTE => mark drone en route
     * - DRONE_DROPPING => mark drone dropping
     */
    private void handleDroneUpdate(Message m) throws InterruptedException {
        Logger.log("[Scheduler]", "Drone update => " + m);

        switch (m.getType()) {
            case "FIRE_EXTINGUISHED":
                extinguishedCount++;
                Logger.log("[Scheduler]",
                        "Fire extinguished => " + extinguishedCount + "/" + totalFires);
                // forward to FireIncidentSubsystem
                incidentCompletionQueue.put(m);

                if (extinguishedCount >= totalFires) {
                    allFiresDone = true;
                }
                break;

            case "PARTIAL_COVERAGE":
                Logger.log("[Scheduler]",
                        "Partial coverage => leftover foam needed= "
                                + m.getRemainingFoamNeeded()
                );
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
                Logger.log("[Scheduler]",
                        "Unknown update => " + m.getType());
        }
    }

    /**
     * If we have any pending fire & an IDLE drone => dispatch immediately.
     */
    private void dispatchIfPossible() throws InterruptedException {
        if (pendingFires.isEmpty()) return;

        // find an IDLE drone
        int idleDrone = -1;
        for (Map.Entry<Integer, DroneState> e : droneStatus.entrySet()) {
            if (e.getValue() == DroneState.IDLE) {
                idleDrone = e.getKey();
                break;
            }
        }
        if (idleDrone == -1) return; // no IDLE drone

        // remove next pending
        Message fireEvt = pendingFires.poll();

        droneStatus.put(idleDrone, DroneState.EN_ROUTE);

        // build dispatch with leftover needed
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

        Logger.log("[Scheduler]", "Dispatched Drone " + idleDrone + " => " + fireEvt);
        dronesQueue.put(dispatchMsg);
    }
}