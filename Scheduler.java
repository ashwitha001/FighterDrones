import java.util.concurrent.BlockingQueue;

public class Scheduler implements Runnable {
    private final BlockingQueue<String> incidentQueue;
    private final BlockingQueue<String> dronesQueue;

    public Scheduler(BlockingQueue<String> incidentQueue, BlockingQueue<String> dronesQueue) {
        this.incidentQueue = incidentQueue;
        this.dronesQueue = dronesQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Receive from FireIncidentSubsystem
                String event = incidentQueue.take();
                System.out.println("[Scheduler] Received event from Incident Report: " + event);

                // Send event to DroneSubsystem
                dronesQueue.put(event);
                System.out.println("[Scheduler] Sent event to Drones: " + event);

//                String Completed = dronesQueue.take();
//                System.out.println("[Scheduler] Fire dealt with: " + Completed);
                } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }

}