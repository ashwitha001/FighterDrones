package main;

import java.util.concurrent.BlockingQueue;

/**
 * DroneSubsystem
 * 1. Receives dispatch from Scheduler (including leftover foam needed).
 * 2. Checks battery feasibility for traveling out & returning.
 * 3. If drone foam < needed => partial coverage:
 *    - Drone uses all foamRemaining,
 *    - leftover needed re-queued as PARTIAL_COVERAGE.
 * 4. If drone foam >= needed => fully extinguish => FIRE_EXTINGUISHED.
 * 5. Returns to base, refuels foam + battery => sends DRONE_IDLE.
 * 6. Shows multi-line progress bar (10% increments) traveling out & back.
 */
enum DroneState {
    IDLE,
    EN_ROUTE,
    DROPPING_AGENT
}

public class DroneSubsystem implements Runnable {

    private static final double MAX_BATTERY_SECONDS = 3600.0;  // 60 min
    private static final double FOAM_CAPACITY = 15.0;          // 15 kg foam

    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;

    private DroneState state;
    private Coordinates currentLocation;
    private double totalFlightTime; // track used battery
    private double foamRemaining;   // track foam left

    public DroneSubsystem(int droneID,
                          BlockingQueue<Message> dronesQueue,
                          BlockingQueue<Message> droneCompletionQueue) {
        this.droneID = droneID;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;

        this.state = DroneState.IDLE;
        this.currentLocation = new Coordinates(0, 0);
        this.totalFlightTime = 0.0;
        this.foamRemaining   = FOAM_CAPACITY;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Wait for dispatch
                Message dispatch = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Dispatch received: " + dispatch);

                double needed = dispatch.getRemainingFoamNeeded();
                Coordinates target = new Coordinates(
                        dispatch.getCenterX(),
                        dispatch.getCenterY()
                );

                // Calculate out & back times
                double tOut = Utility.computeTravelTime(
                        currentLocation.getX1(), currentLocation.getY1(),
                        target.getX1(), target.getY1()
                );
                double tBack = Utility.computeTravelTime(
                        target.getX1(), target.getY1(),
                        0,0
                );

                // Check battery
                if (totalFlightTime + tOut + tBack > MAX_BATTERY_SECONDS) {
                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Insufficient battery => skip event!");
                    continue;
                }

                // Mark DRONE_EN_ROUTE
                droneCompletionQueue.put(new Message(
                        "DRONE_EN_ROUTE",
                        droneID,
                        dispatch.getZoneID(),
                        dispatch.getSeverity(),
                        dispatch.getEventTime(),
                        dispatch.getEventTimeString(),
                        dispatch.getCenterX(),
                        dispatch.getCenterY(),
                        needed
                ));
                state = DroneState.EN_ROUTE;

                // Travel out => show progress
                String labelOut = String.format(
                        "DRONE-%d => from (%d,%d) to (%d,%d)",
                        droneID,
                        currentLocation.getX1(), currentLocation.getY1(),
                        target.getX1(), target.getY1()
                );
                Utility.showProgress(tOut, labelOut);

                currentLocation = target;
                totalFlightTime += tOut;
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Arrived => needed=" + needed + ", foamRemaining=" + foamRemaining);

                // Mark DRONE_DROPPING
                droneCompletionQueue.put(new Message(
                        "DRONE_DROPPING",
                        droneID,
                        dispatch.getZoneID(),
                        dispatch.getSeverity(),
                        dispatch.getEventTime(),
                        dispatch.getEventTimeString(),
                        dispatch.getCenterX(),
                        dispatch.getCenterY(),
                        needed
                ));
                state = DroneState.DROPPING_AGENT;

                if (foamRemaining >= needed) {
                    // FULL coverage => extinguish
                    double dropTime = Utility.nozzleDropTime(needed);
                    Thread.sleep((long)(dropTime * 1000));
                    foamRemaining -= needed;
                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Fully extinguished => used " + needed
                                    + ", leftover foam=" + foamRemaining);

                    // FIRE_EXTINGUISHED
                    droneCompletionQueue.put(
                            new Message(
                                    "FIRE_EXTINGUISHED",
                                    droneID,
                                    dispatch.getZoneID(),
                                    dispatch.getSeverity(),
                                    dispatch.getEventTime(),
                                    dispatch.getEventTimeString(),
                                    dispatch.getCenterX(),
                                    dispatch.getCenterY(),
                                    0.0
                            )
                    );

                } else {
                    // PARTIAL coverage
                    double partialDrop = foamRemaining;
                    double leftover = needed - partialDrop;
                    double dropTime = Utility.nozzleDropTime(partialDrop);
                    Thread.sleep((long)(dropTime * 1000));
                    foamRemaining = 0.0;

                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Partial coverage => used " + partialDrop
                                    + ", leftover needed=" + leftover);

                    droneCompletionQueue.put(
                            new Message(
                                    "PARTIAL_COVERAGE",
                                    droneID,
                                    dispatch.getZoneID(),
                                    dispatch.getSeverity(),
                                    dispatch.getEventTime(),
                                    dispatch.getEventTimeString(),
                                    dispatch.getCenterX(),
                                    dispatch.getCenterY(),
                                    leftover
                            )
                    );
                }

                // Return to base => show progress
                state = DroneState.EN_ROUTE;
                String labelBack = String.format(
                        "DRONE-%d => returning from (%d,%d) to (0,0)",
                        droneID, currentLocation.getX1(), currentLocation.getY1()
                );
                Utility.showProgress(tBack, labelBack);

                currentLocation = new Coordinates(0,0);
                totalFlightTime += tBack;
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Refueling => battery & foam reset.");

                // Reset battery & foam
                totalFlightTime = 0.0;
                foamRemaining   = FOAM_CAPACITY;

                // Mark DRONE_IDLE
                state = DroneState.IDLE;
                droneCompletionQueue.put(
                        new Message(
                                "DRONE_IDLE",
                                droneID,
                                -1,
                                "",
                                null,
                                "",
                                0, 0,
                                0.0
                        )
                );

            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Interrupted => shutting down...");
                break;
            }
        }
    }
}