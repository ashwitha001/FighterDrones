package main;

import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**
 * FirefightingDroneMain (Application Entry Point)
 * 1. Reads the total number of fire events dynamically by counting lines in `events.csv`.
 * 2. Creates all the necessary BlockingQueues for communication among subsystems.
 * 3. Instantiates and starts:
 *    - FireIncidentSubsystem (reads zone/event files and sends events)
 *    - Scheduler (dispatches events to drones, tracks completions, ends program)
 *    - One or more DroneSubsystem thread(s) (simulating drone behavior).
 * 4. The program ends once the Scheduler detects all fires extinguished and all drones idle, then calls System.exit(0).
 */
public class FirefightingDroneMain {
    private static final int NUM_DRONES = 1; // can be more

    public static void main(String[] args) {
        // Count lines => totalFires
        int totalFires = Utility.countEventLines("events.csv");
        Logger.log("[Main]", "Detected " + totalFires + " fire events.");

        // Create queues
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> dronesQueue   = new LinkedBlockingQueue<>();
        BlockingQueue<Message> droneCompletionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

        // FireIncidentSubsystem & Scheduler
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
                        totalFires
                ),
                "Scheduler"
        );

        fireIncidentThread.start();
        schedulerThread.start();

        // DroneSubsystem(s):
        for (int i = 0; i < NUM_DRONES; i++) {
            Thread droneThread = new Thread(
                    new DroneSubsystem(i, dronesQueue, droneCompletionQueue),
                    "DroneSubsystem-" + i
            );
            droneThread.start();
        }

        // The Scheduler calls System.exit(0) once done
    }
}