import java.util.concurrent.BlockingQueue;

/**
 * Scheduler:
 * - Acts as a central router between FireIncidentSubsystem and DroneSubsystem.
 * - Passes fire events to available drones.
 * - Notifies FireIncidentSubsystem when fires are extinguished.
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
                // Receive fire event from FireIncidentSubsystem
                Message event = incidentQueue.take();
                System.out.println("[Scheduler] Received Fire Event: " + event);

                // Forward the event to DroneSubsystem
                dronesQueue.put(event);
                System.out.println("[Scheduler] Sent event to DroneSubsystem: " + event);

                // Wait for the drone to complete the task
                Message completedEvent = droneCompletionQueue.take();

                // Forward completion event to FireIncidentSubsystem
                incidentCompletionQueue.put(completedEvent);
                System.out.println("[Scheduler] Fire extinguished at: " + completedEvent);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}
