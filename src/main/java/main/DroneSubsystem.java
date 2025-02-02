package main;

import java.util.concurrent.BlockingQueue;

/**
 * Drone Subsystem:
 * - Receives dispatch orders from the main.Scheduler.
 * - Simulates fire extinguishing at a given zone.
 * - Sends a completion message back to the main.Scheduler.
 */
public class DroneSubsystem implements Runnable {
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;

    public DroneSubsystem(BlockingQueue<Message> dronesQueue, BlockingQueue<Message> droneCompletionQueue) {
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Receive event from main.Scheduler
                Message eventFire = dronesQueue.take();
                //System.out.println("[main.DroneSubsystem] Dispatch received: " + eventFire);
                Logger.log("[main.DroneSubsystem]", "Dispatch received: " + eventFire);

                // Simulate travel + firefighting
                //System.out.println("[main.DroneSubsystem] Drone dispatched to Zone " + eventFire.getZoneID()
                Logger.log("[main.DroneSubsystem]", "Drone dispatched to Zone " + eventFire.getZoneID()
                        + " (Time = " + eventFire.getEventTimeString() + ")");
                Thread.sleep(2000); // Simulate time needed to travel

                //System.out.println("[main.DroneSubsystem] FIRE EXTINGUISHED at Zone " + eventFire.getZoneID());
                Logger.log("[main.DroneSubsystem]", "Completed: " + eventFire);

                // Notify completion
                Message doneMsg = new Message(
                        "FIRE_EXTINGUISHED",
                        eventFire.getZoneID(),
                        eventFire.getSeverity(),
                        eventFire.getEventTime(),
                        eventFire.getEventTimeString()
                );

                //System.out.println("[main.DroneSubsystem] Completion sent: " + doneMsg);
                Logger.log("[main.DroneSubsystem]", "Completion sent: " + doneMsg);
                droneCompletionQueue.put(doneMsg);

            } catch (InterruptedException e) {
                System.out.println("[main.DroneSubsystem] Interrupted, shutting down...");
                break;
            }
        }
    }
}
