package main;

import java.util.concurrent.BlockingQueue;

/**
 * DroneSubsystem:
 * - Sends intermediate messages (DRONE_EN_ROUTE, DRONE_ARRIVED, DRONE_DROPPING, FIRE_EXTINGUISHED, DRONE_IDLE)
 * - Uses Utility.showProgress(...) for multi-line progress bars
 * - Enforces 45-minute limit
 */
enum DroneState {
    IDLE,
    EN_ROUTE,
    DROPPING_AGENT
}

public class DroneSubsystem implements Runnable {
    private static final double MAX_TRAVEL_TIME_SECONDS = 2700.0; // 45 minutes

    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;           // from Scheduler
    private final BlockingQueue<Message> droneCompletionQueue;  // to Scheduler

    private DroneState state;
    private Coordinates currentLocation;
    private double totalTravelTime;

    public DroneSubsystem(int droneID,
                          BlockingQueue<Message> dronesQueue,
                          BlockingQueue<Message> droneCompletionQueue) {
        this.droneID = droneID;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;

        this.state = DroneState.IDLE;
        this.currentLocation = new Coordinates(0, 0); // base
        this.totalTravelTime = 0.0;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // 1) Receive dispatch
                Message eventMsg = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Dispatch received: " + eventMsg);

                // The zone center or 0,0 from the event message
                // Drone has to be at center of zone to extinguish fire
                Coordinates zoneCenter = new Coordinates(
                        eventMsg.getCenterX(),
                        eventMsg.getCenterY()
                );

                // Calculate time to zone and back
                double timeToZone = Utility.computeTravelTime(
                        currentLocation.getX1(), currentLocation.getY1(),
                        zoneCenter.getX1(),  zoneCenter.getY1()
                );
                double timeBack = Utility.computeTravelTime(
                        zoneCenter.getX1(), zoneCenter.getY1(),
                        0, 0
                );

                // Check 45-min total limit
                if (totalTravelTime + timeToZone + timeBack > MAX_TRAVEL_TIME_SECONDS) {
                    Logger.log("[DroneSubsystem-" + droneID + "]",
                            "Not enough time to reach zone " + eventMsg.getZoneID()
                                    + " and return before 45 mins!");
                    // Possibly notify Scheduler or skip for iteration 2
                    continue;
                }

                // 2) Send DRONE_EN_ROUTE to scheduler
                Message enRouteMsg = new Message(
                        "DRONE_EN_ROUTE",
                        droneID,
                        eventMsg.getZoneID(),
                        eventMsg.getSeverity(),
                        eventMsg.getEventTime(),
                        eventMsg.getEventTimeString(),
                        eventMsg.getCenterX(),
                        eventMsg.getCenterY()
                );
                droneCompletionQueue.put(enRouteMsg);

                // 3) Multi-line progress to zone
                state = DroneState.EN_ROUTE;
                String labelTo = String.format(
                        "DRONE-%d => from (%d,%d) to (%d,%d)",
                        droneID,
                        currentLocation.getX1(), currentLocation.getY1(),
                        zoneCenter.getX1(),      zoneCenter.getY1()
                );
                Utility.showProgress(timeToZone, 10, labelTo);

                // Arrive
                currentLocation = zoneCenter;
                totalTravelTime += timeToZone;
                // 4) DRONE_ARRIVED
                Message arrivedMsg = new Message(
                        "DRONE_ARRIVED",
                        droneID,
                        eventMsg.getZoneID(),
                        eventMsg.getSeverity(),
                        eventMsg.getEventTime(),
                        eventMsg.getEventTimeString(),
                        eventMsg.getCenterX(),
                        eventMsg.getCenterY()
                );
                droneCompletionQueue.put(arrivedMsg);
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Arrived at zone => " + currentLocation);

                // 5) Drop agent: DRONE_DROPPING
                state = DroneState.DROPPING_AGENT;
                Message droppingMsg = new Message(
                        "DRONE_DROPPING",
                        droneID,
                        eventMsg.getZoneID(),
                        eventMsg.getSeverity(),
                        eventMsg.getEventTime(),
                        eventMsg.getEventTimeString(),
                        eventMsg.getCenterX(),
                        eventMsg.getCenterY()
                );
                droneCompletionQueue.put(droppingMsg);

                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Dropping agent at zone " + eventMsg.getZoneID());
                Thread.sleep(1000);

                // 6) Return to base
                double returnTime = Utility.computeTravelTime(
                        currentLocation.getX1(), currentLocation.getY1(),
                        0, 0
                );
                state = DroneState.EN_ROUTE;
                String labelBack = String.format(
                        "DRONE-%d => returning from (%d,%d) to base (0,0)",
                        droneID,
                        currentLocation.getX1(), currentLocation.getY1()
                );
                Utility.showProgress(returnTime, 10, labelBack);

                currentLocation = new Coordinates(0,0);
                totalTravelTime += returnTime;
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Refueled & restocked at base => totalTravelTime="
                                + totalTravelTime);

                // 7) FIRE_EXTINGUISHED
                Message doneMsg = new Message(
                        "FIRE_EXTINGUISHED",
                        droneID,
                        eventMsg.getZoneID(),
                        eventMsg.getSeverity(),
                        eventMsg.getEventTime(),
                        eventMsg.getEventTimeString(),
                        eventMsg.getCenterX(),
                        eventMsg.getCenterY()
                );
                droneCompletionQueue.put(doneMsg);
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "FIRE_EXTINGUISHED => done.");

                // 8) DRONE_IDLE
                state = DroneState.IDLE;
                Message idleMsg = new Message(
                        "DRONE_IDLE",
                        droneID,
                        -1,
                        "",
                        null,
                        "",
                        0,
                        0
                );
                droneCompletionQueue.put(idleMsg);

            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Interrupted => shutting down...");
                break;
            }
        }
    }
}