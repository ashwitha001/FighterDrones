package main;

import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.HashMap;
/**
 * DroneSubsystem (Context in the State Pattern):
 *
 * - Replaces the old "DroneSubsystem" approach with an internal state machine.
 * - Holds references to the current DroneState, plus shared data (foam, battery, location).
 * - The run() method reads messages from the scheduler and triggers handleEvent(DISPATCH_RECEIVED).
 */
public class DroneSubsystem implements Runnable {

    private static final double MAX_BATTERY_SECONDS = 3600.0;
    private static final double FOAM_CAPACITY       = 15.0;

    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;           // from Scheduler
    private final BlockingQueue<Message> droneCompletionQueue;  // to Scheduler

    // The HashMap of possible states
    private final Map<String, DroneState> stateMap;
    // The current DroneState object
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

        // Create the states and store them in a HashMap
        stateMap = new HashMap<>();
        stateMap.put("IDLE",       new IdleState());
        stateMap.put("EN_ROUTE",   new EnRouteState());
        stateMap.put("DROPPING",   new DroppingAgentState());

        // Initial data
        this.currentLocation = new Coordinates(0,0);
        this.totalFlightTime = 0.0;
        this.foamRemaining   = FOAM_CAPACITY;

        // Start in "IDLE" state
        this.currentState = stateMap.get("IDLE");
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Wait for dispatch or partial coverage leftover
                Message msg = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Received message => " + msg);

                // We interpret it as DISPATCH_RECEIVED
                currentState.handleEvent(this, DroneEvent.DISPATCH_RECEIVED, msg);

            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]",
                        "Interrupted => shutting down...");
                break;
            }
        }
    }

    // The method to switch states by string key:
    public void setState(String stateName) {
        if (stateMap.containsKey(stateName)) {
            this.currentState = stateMap.get(stateName);
        } else {
            Logger.log("[DroneSubsystem-" + droneID + "]",
                    "No such state => " + stateName + ". Keeping current state.");
        }
    }

    public DroneState getCurrentState() {
        return this.currentState;
    }

    // Access to shared data:

    public int getDroneID() { return droneID; }
    public BlockingQueue<Message> getDronesQueue() { return dronesQueue; }
    public BlockingQueue<Message> getDroneCompletionQueue() { return droneCompletionQueue; }

    public Coordinates getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Coordinates c) { currentLocation = c; }

    public double getTotalFlightTime() { return totalFlightTime; }
    public void setTotalFlightTime(double t) { totalFlightTime = t; }

    public double getFoamRemaining() { return foamRemaining; }
    public void setFoamRemaining(double v) { foamRemaining = v; }

    public static double getMaxBatterySeconds() { return MAX_BATTERY_SECONDS; }
    public static double getFoamCapacity()      { return FOAM_CAPACITY; }
}