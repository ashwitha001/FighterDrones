package main;

import java.net.InetSocketAddress;

public class DroneMain {
    public static void main(String[] args) {
        if (args.length < 1) {
            System.err.println("Usage: java main.DroneMain <droneID>");
            System.exit(1);
        }
        int droneID;
        try {
            droneID = Integer.parseInt(args[0]);
        } catch (NumberFormatException ex) {
            System.err.println("Invalid droneID; must be an integer.");
            System.exit(1);
            return;
        }
        // Scheduler is assumed to run on localhost:5000.
        InetSocketAddress schedulerAddress = new InetSocketAddress("localhost", 5000);
        Logger.log("[DroneMain-" + droneID + "]", "Starting DroneSubsystem with ID " + droneID);
        DroneSubsystem drone = new DroneSubsystem(droneID, schedulerAddress);
        Thread droneThread = new Thread(drone, "DroneSubsystem-" + droneID);
        droneThread.start();
        try {
            droneThread.join();
        } catch (InterruptedException e) {
            Logger.log("[DroneMain-" + droneID + "]", "Interrupted, shutting down.");
        }
    }
}
