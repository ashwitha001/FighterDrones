import java.util.concurrent.BlockingQueue;

public class Scheduler implements Runnable {
    private final BlockingQueue<String> incidentQueue;
    private final BlockingQueue<String> dronesQueue;
    private final BlockingQueue<String> droneCompletionQueue;
    private final BlockingQueue<String> incidentCompletionQueue;



    public Scheduler(BlockingQueue<String> incidentQueue, BlockingQueue<String> dronesQueue, BlockingQueue<String> droneCompletionQueue, BlockingQueue<String> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Receive from FireIncidentSubsystem
                String event = incidentQueue.take();
                System.out.println("[Scheduler] Received Fire Event: " + event);

                // Send event to DroneSubsystem
                dronesQueue.put(event);
                System.out.println("[Scheduler] Sent event to DroneSubsystem: " + event);

                //wait for completion
                String completedEvent = droneCompletionQueue.take();
                //System.out.println("[Scheduler] Received response from DroneSubsystem: " + completedEvent);

                // Notify Fire Incident Subsystem
                incidentCompletionQueue.put(completedEvent);
                //System.out.println("[Scheduler] Sending completion response to FireIncidentSubsystem: " + completedEvent);
                } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}