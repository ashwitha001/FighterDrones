package src;

import java.util.concurrent.BlockingQueue;

/**
 * Drone Subsystem:
 * - Receives dispatch orders from the Scheduler.
 * - Simulates fire extinguishing at a given zone.
 * - Sends a completion message back to the Scheduler.
 */

enum DroneState {
    IDLE, EN_ROUTE, DROPPING_AGENT, RETURNING // Remove RETURNING, add coordinates to the en_route state depicting where drone is headed (0,0 would mean returning)
}
public class DroneSubsystem implements Runnable {
    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;
    private DroneState state;

    public DroneSubsystem(int droneID, BlockingQueue<Message> dronesQueue, BlockingQueue<Message> droneCompletionQueue) {

        this.droneID = droneID;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
        this.state = DroneState.IDLE;
    }
  
    @Override
    public void run() {
        while (true) {
            try {
                // Receive event from Scheduler
                Message eventFire = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]", "Dispatch received: " + eventFire);

                // Simulate travel + firefighting
                state = DroneState.EN_ROUTE;
                Logger.log("[DroneSubsystem-" + droneID + "]", "En route to Zone " + eventFire.getZoneID()
                        + " (Time = " + eventFire.getEventTimeString() + ")");
                // Notify scheduler that drone is en route
                droneCompletionQueue.put(new Message("DRONE_EN_ROUTE", droneID, eventFire.getZoneID(), eventFire.getSeverity(), eventFire.getEventTime(), eventFire.getEventTimeString()));
                Thread.sleep(2000); // Simulate travel

                state = DroneState.DROPPING_AGENT;
                Logger.log("[DroneSubsystem-" + droneID + "]", "Extinguishing Fire at Zone " + eventFire.getZoneID());
                Thread.sleep(1000); // Simulate firefighting

                state = DroneState.RETURNING;
                Logger.log("[DroneSubsystem-" + droneID + "]", "Returning from Zone " + eventFire.getZoneID());
                // Notify completion
                Message doneMsg = new Message(
                        "FIRE_EXTINGUISHED",
                        droneID,
                        eventFire.getZoneID(),
                        eventFire.getSeverity(),
                        eventFire.getEventTime(),
                        eventFire.getEventTimeString()
                );
                Logger.log("[DroneSubsystem-" + droneID + "]", "Completion sent: " + doneMsg);
                droneCompletionQueue.put(doneMsg);
                state = DroneState.IDLE;
                droneCompletionQueue.put(new Message("DRONE_IDLE", droneID, -1, "", null, "")); // Notify scheduler
                Logger.log("[DroneSubsystem-" + droneID + "]", "Completion sent: " + doneMsg);


            } catch (InterruptedException e) {
                System.out.println("[DroneSubsystem-" + droneID + "]" + "Interrupted, shutting down...");
                break;
            }
        }

    }
}
