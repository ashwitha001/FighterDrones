package main;

import java.util.concurrent.BlockingQueue;

/**
 * DroneSubsystem:
 * - Sends intermediate messages (DRONE_EN_ROUTE, DRONE_ARRIVED, DRONE_DROPPING, FIRE_EXTINGUISHED, DRONE_IDLE)
 */
enum DroneState {
    IDLE,
    EN_ROUTE,
    DROPPING_AGENT
}

public class DroneSubsystem implements Runnable {
    private static final double MAX_BATTERY_SECONDS = 3600.0;
    private static final double FOAM_CAPACITY = 15.0;

    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;

    private DroneState state;
    private Coordinates currentLocation;
    private double totalFlightTime;
    private double foamRemaining;

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
                Message dispatch = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Dispatch received: " + dispatch);

                Coordinates target = new Coordinates(
                        dispatch.getCenterX(),
                        dispatch.getCenterY()
                );
                double needed = dispatch.getRemainingFoamNeeded();

                // Check battery feasibility for out + back
                double tOut = Utility.computeTravelTime(
                        currentLocation.getX1(), currentLocation.getY1(),
                        target.getX1(), target.getY1()
                );
                double tBack = Utility.computeTravelTime(
                        target.getX1(), target.getY1(),
                        0,0
                );
                if (totalFlightTime + tOut + tBack > MAX_BATTERY_SECONDS) {
                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Not enough battery => skip");
                    continue;
                }

                // DRONE_EN_ROUTE
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
                String labelOut = String.format("DRONE-%d => from (%d,%d) to (%d,%d)",
                        droneID, currentLocation.getX1(), currentLocation.getY1(),
                        target.getX1(), target.getY1());
                Utility.showProgress(tOut, labelOut);

                currentLocation = target;
                totalFlightTime += tOut;
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Arrived => needed foam=" + needed + ", drone foam=" + foamRemaining);

                // Attempt coverage
                state = DroneState.DROPPING_AGENT;
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

                if (foamRemaining >= needed) {
                    // Full extinguish
                    double dropTime = Utility.nozzleDropTime(needed);
                    Thread.sleep((long)(dropTime * 1000));
                    foamRemaining -= needed;
                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Fire fully extinguished => used " + needed
                                    + ", foam left=" + foamRemaining);

                    // FIRE_EXTINGUISHED
                    Message doneMsg = new Message(
                            "FIRE_EXTINGUISHED",
                            droneID,
                            dispatch.getZoneID(),
                            dispatch.getSeverity(),
                            dispatch.getEventTime(),
                            dispatch.getEventTimeString(),
                            dispatch.getCenterX(),
                            dispatch.getCenterY(),
                            0.0
                    );
                    droneCompletionQueue.put(doneMsg);
                } else {
                    // partial coverage
                    double partialDrop = foamRemaining;
                    double leftover = needed - foamRemaining;

                    double dropTime = Utility.nozzleDropTime(partialDrop);
                    Thread.sleep((long)(dropTime * 1000));
                    foamRemaining = 0.0;

                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Partial coverage => dropped " + partialDrop
                                    + ", leftover needed=" + leftover);

                    // PARTIAL_COVERAGE => re-queue with leftover
                    Message partialMsg = new Message(
                            "PARTIAL_COVERAGE",
                            droneID,
                            dispatch.getZoneID(),
                            dispatch.getSeverity(),
                            dispatch.getEventTime(),
                            dispatch.getEventTimeString(),
                            dispatch.getCenterX(),
                            dispatch.getCenterY(),
                            leftover
                    );
                    droneCompletionQueue.put(partialMsg);
                }

                // Return to base
                state = DroneState.EN_ROUTE;
                String labelBack = String.format(
                        "DRONE-%d => returning from (%d,%d) to (0,0)",
                        droneID, currentLocation.getX1(), currentLocation.getY1()
                );
                Utility.showProgress(tBack, labelBack);

                currentLocation = new Coordinates(0,0);
                totalFlightTime += tBack;
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "At base => reset battery & foam.");

                totalFlightTime = 0.0;
                foamRemaining   = FOAM_CAPACITY;

                // DRONE_IDLE
                state = DroneState.IDLE;
                droneCompletionQueue.put(new Message(
                        "DRONE_IDLE",
                        droneID,
                        -1,
                        "",
                        null,
                        "",
                        0,0,
                        0.0
                ));

            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Interrupted => shutting down...");
                break;
            }
        }
    }
}