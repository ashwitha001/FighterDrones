package main;

import java.util.concurrent.BlockingQueue;

/**
 * main.Scheduler:
 * - Acts as a central router between main.FireIncidentSubsystem and main.DroneSubsystem.
 * - Passes fire events to available drones.
 * - Notifies main.FireIncidentSubsystem when fires are extinguished.
 */
public class Scheduler implements Runnable {
    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;

    public Scheduler(BlockingQueue<Message> incidentQueue, BlockingQueue<Message> dronesQueue,
                     BlockingQueue<Message> droneCompletionQueue, BlockingQueue<Message> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Receive fire event from main.FireIncidentSubsystem
                Message event = incidentQueue.take();
                //System.out.println("[main.Scheduler] Received Fire Event: " + event);
                Logger.log("[main.Scheduler]", "Received Fire Event: " + event);

                // Forward the event to main.DroneSubsystem
                //System.out.println("[main.Scheduler] Sent event to main.DroneSubsystem: " + event);
                Logger.log("[main.Scheduler]", "Sent event to main.DroneSubsystem: " + event);
                dronesQueue.put(event);

                // Wait for the drone to complete the task
                Message completedEvent = droneCompletionQueue.take();
                //System.out.println("[main.Scheduler] Completion confirmed: " + completedEvent);
                Logger.log("[main.Scheduler]", "Completion confirmed: " + completedEvent);

                // Forward completion event to main.FireIncidentSubsystem
                //System.out.println("[main.Scheduler] Completion sent: " + completedEvent);
                Logger.log("[main.Scheduler]", "Completion sent: " + completedEvent);
                incidentCompletionQueue.put(completedEvent);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
