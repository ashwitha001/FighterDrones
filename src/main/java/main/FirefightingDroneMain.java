package main;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

/**
 * FirefightingDroneMain:
 * - Initializes and starts all subsystems as separate threads.
 * - Manages the communication between FireIncidentSubsystem, Scheduler, and DroneSubsystem.
 */
public class FirefightingDroneMain {
    private static final int NUM_DRONES = 1; // or more
    // We'll assume totalFires is the known number of lines in your events file.
    // For demonstration, let's set a fixed number (e.g. 4).
    private static final int TOTAL_FIRES = 4;

    public static void main(String[] args) {
        // Create inter-thread queues
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> dronesQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> droneCompletionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

        // FireIncidentSubsystem and Scheduler
        Thread fireIncidentThread = new Thread(
                new FireIncidentSubsystem(incidentQueue, incidentCompletionQueue),
                "FireIncidentSubsystem"
        );
        Thread schedulerThread = new Thread(
                new Scheduler(
                        incidentQueue,
                        dronesQueue,
                        droneCompletionQueue,
                        incidentCompletionQueue,
                        NUM_DRONES,
                        TOTAL_FIRES // e.g. number of events
                ),
                "Scheduler"
        );

        fireIncidentThread.start();
        schedulerThread.start();

        // Create and start DroneSubsystem(s)
        for (int i = 0; i < NUM_DRONES; i++) {
            Thread droneThread = new Thread(
                    new DroneSubsystem(i, dronesQueue, droneCompletionQueue),
                    "DroneSubsystem-" + i
            );
            droneThread.start();
        }

        // Let system run until Scheduler calls System.exit(0) or user kills program
        // Typically no direct Thread.sleep() needed here, as Scheduler will end the program
    }
}