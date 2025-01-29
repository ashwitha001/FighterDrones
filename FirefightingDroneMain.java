import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FirefightingDroneMain {
    public static void main(String[] args) {
        BlockingQueue<String> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<String> dronesQueue = new LinkedBlockingQueue<>();

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(incidentQueue), "FireIncidentSubsystem");
        Thread schedulerThread = new Thread(new Scheduler(incidentQueue, dronesQueue), "Scheduler");
        Thread droneThread = new Thread(new DroneSubsystem(dronesQueue), "DroneSubsystem");

        fireIncidentThread.start();
        schedulerThread.start();
        droneThread.start();

        try {
            Thread.sleep(5000);
            // Allow the threads to run for a short period to simulate system activity
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("....Fires are all put out!");
    }
}