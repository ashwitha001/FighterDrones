package src;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * FirefightingDroneMain:
 * - Initializes and starts all subsystems as separate threads.
 * - Manages the communication between FireIncidentSubsystem, Scheduler, and DroneSubsystem.
 */
public class FirefightingDroneMain {
    public static void main(String[] args) {
        // Initialize BlockingQueues for inter-thread communication
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> dronesQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> droneCompletionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

        // Create and start threads
        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(incidentQueue, incidentCompletionQueue), "FireIncidentSubsystem");
        Thread schedulerThread = new Thread(new Scheduler(incidentQueue, dronesQueue, droneCompletionQueue, incidentCompletionQueue), "Scheduler");
        Thread droneThread = new Thread(new DroneSubsystem(0, dronesQueue, droneCompletionQueue), "DroneSubsystem");

        fireIncidentThread.start();
        schedulerThread.start();
        droneThread.start();

        // Let the system run for some time
        try {
            Thread.sleep(20000); // 20 seconds

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("....Simulation complete.");
    }
}
