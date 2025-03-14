//package main;
//
//import java.net.InetSocketAddress;
//
//
///**
// * FirefightingDroneMain (Application Entry Point)
// * 1. Reads the total number of fire events dynamically by counting lines in `events.csv`.
// * 2. Creates all the necessary BlockingQueues for communication among subsystems.
// * 3. Instantiates and starts:
// *    - FireIncidentSubsystem (reads zone/event files and sends events)
// *    - Scheduler (dispatches events to drones, tracks completions, ends program)
// *    - One or more DroneSubsystem thread(s) (simulating drone behavior).
// * 4. The program ends once the Scheduler detects all fires extinguished and all drones idle, then calls System.exit(0).
// */
//public class FirefightingDroneMain {
//    private static final int NUM_DRONES = 3;
//
//    public static void main(String[] args) {
//        int totalFires = Utility.countEventLines("events.csv");
//        Logger.log("[Main]", "Detected " + totalFires + " fire events.");
//
//        // Define addresses:
//        InetSocketAddress schedulerAddress = new InetSocketAddress("localhost", 5000);
//        InetSocketAddress fireIncidentAddress = new InetSocketAddress("localhost", 5001);
//
//        // Drone addresses: each drone gets port 6000 + id.
//        InetSocketAddress[] droneAddresses = new InetSocketAddress[NUM_DRONES];
//        for (int i = 0; i < NUM_DRONES; i++) {
//            droneAddresses[i] = new InetSocketAddress("localhost", 6000 + i);
//        }
//
//        // Start Scheduler.
//        Thread schedulerThread = new Thread(new Scheduler(schedulerAddress, droneAddresses, totalFires), "Scheduler");
//        schedulerThread.start();
//
//        // Start FireIncidentSubsystem.
//        Thread fireIncidentThread = new Thread(new FireIncidentSubsystem(schedulerAddress, fireIncidentAddress.getPort()), "FireIncidentSubsystem");
//        fireIncidentThread.start();
//
//        // Start each DroneSubsystem.
//        for (int i = 0; i < NUM_DRONES; i++) {
//            Thread droneThread = new Thread(new DroneSubsystem(i, schedulerAddress, 6000 + i), "DroneSubsystem-" + i);
//            droneThread.start();
//        }
//    }
//}