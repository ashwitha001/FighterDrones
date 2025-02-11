package main;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * FirefightingDroneMain:
 * - Initializes and starts all subsystems as separate threads.
 * - Manages the communication between FireIncidentSubsystem, Scheduler, and DroneSubsystem.
 */
public class FirefightingDroneMain {
    private static final int NUM_DRONES = 1; // Set to 1 drone for iteration 2

    public static void main(String[] args) {
        // Initialize BlockingQueues for inter-thread communication
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> dronesQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> droneCompletionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

        // Create and start threads
        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(incidentQueue, incidentCompletionQueue), "FireIncidentSubsystem");
        Thread schedulerThread = new Thread(new Scheduler(incidentQueue, dronesQueue, droneCompletionQueue, incidentCompletionQueue, NUM_DRONES), "Scheduler");

        fireIncidentThread.start();
        schedulerThread.start();

        // Create and start multiple DroneSubsystems
        for (int i = 0; i < NUM_DRONES; i++) {
            Thread droneThread = new Thread(new DroneSubsystem(i, dronesQueue, droneCompletionQueue), "DroneSubsystem-" + i);
            droneThread.start();
        }

        // Let the system run for some time
        try {
            Thread.sleep(20000); // 20 seconds

        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        System.out.println("....Simulation complete.");
    }
}
