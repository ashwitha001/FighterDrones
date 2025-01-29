import java.util.concurrent.BlockingQueue;

public class Scheduler implements Runnable {
    private final BlockingQueue<String> incidentQueue;

    public Scheduler(BlockingQueue<String> incidentQueue) {
        this.incidentQueue = incidentQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                String event = incidentQueue.take(); // Receive from FireIncidentSubsystem
                System.out.println("[Scheduler] Received event: " + event);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
            System.out.println("[Scheduler] Sends event to Drones");
            System.out.println("----Drone Logic-----");
        }
    }
}