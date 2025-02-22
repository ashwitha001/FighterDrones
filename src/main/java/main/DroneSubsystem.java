package main;

import java.util.concurrent.BlockingQueue;

/**
 * DroneSubsystem (Context in the State Pattern):
 *
 * - Replaces the old "DroneSubsystem" approach with an internal state machine.
 * - Holds references to the current DroneState, plus shared data (foam, battery, location).
 * - The run() method reads messages from the scheduler and triggers handleEvent(DISPATCH_RECEIVED).
 */
public class DroneSubsystem implements Runnable {

    private static final double MAX_BATTERY_SECONDS = 3600.0;  // 60 mins
    private static final double FOAM_CAPACITY       = 15.0;    // 15 kg foam

    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;           // from Scheduler
    private final BlockingQueue<Message> droneCompletionQueue;  // to Scheduler

    // The "current state" in the State Pattern
    private DroneState currentState;

    // Shared data for partial coverage, battery usage, location, etc.
    private Coordinates currentLocation;
    private double totalFlightTime;
    private double foamRemaining;

    public DroneSubsystem(int droneID,
                          BlockingQueue<Message> dronesQueue,
                          BlockingQueue<Message> droneCompletionQueue) {
        this.droneID = droneID;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;

        // Start in IdleState
        this.currentState = new IdleState();

        // Initialize drone data
        this.currentLocation = new Coordinates(0, 0);
        this.totalFlightTime = 0.0;
        this.foamRemaining   = FOAM_CAPACITY;
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Wait for a dispatch or partial coverage re-queue, etc.
                Message msg = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Received message: " + msg);

                // We interpret this as DISPATCH_RECEIVED event => handle it
                currentState.handleEvent(this, DroneEvent.DISPATCH_RECEIVED, msg);

            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Interrupted => shutting down...");
                break;
            }
        }
    }

    // Set or get the current state
    public void setState(DroneState newState) {
        this.currentState = newState;
    }

    public DroneState getState() {
        return this.currentState;
    }

    // Accessors for the rest of the data

    public int getDroneID() { return droneID; }

    public BlockingQueue<Message> getDronesQueue() { return dronesQueue; }

    public BlockingQueue<Message> getDroneCompletionQueue() { return droneCompletionQueue; }

    public Coordinates getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Coordinates loc) { this.currentLocation = loc; }

    public double getTotalFlightTime() { return totalFlightTime; }
    public void setTotalFlightTime(double t) { this.totalFlightTime = t; }

    public double getFoamRemaining() { return foamRemaining; }
    public void setFoamRemaining(double foam) { this.foamRemaining = foam; }

    public static double getMaxBatterySeconds() { return MAX_BATTERY_SECONDS; }
    public static double getFoamCapacity() { return FOAM_CAPACITY; }
}