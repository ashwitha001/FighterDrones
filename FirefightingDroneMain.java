import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

public class FirefightingDroneMain {
    public static void main(String[] args) {
        BlockingQueue<String> schedulerQueue = new LinkedBlockingQueue<>();

        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(schedulerQueue), "FireIncidentSubsystem");
        Thread schedulerThread = new Thread(new Scheduler(schedulerQueue), "Scheduler");
        Thread droneThread = new Thread(new DroneSubsystem(), "DroneSubsystem");

        fireIncidentThread.start();
        schedulerThread.start();
        droneThread.start();

        try {
            // Allow the threads to run for a short period to simulate system activity
            Thread.sleep(5000);
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("....Fires are all put out!");
    }
}