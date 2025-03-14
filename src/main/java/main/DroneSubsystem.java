package main;

import java.util.concurrent.BlockingQueue;
import java.util.Map;
import java.util.HashMap;

public class DroneSubsystem implements Runnable {

    private static final double MAX_BATTERY_SECONDS = 3600.0;
    private static final double FOAM_CAPACITY       = 15.0;

    private final int droneID;
    private final BlockingQueue<Message> dronesQueue;           // from Scheduler
    private final BlockingQueue<Message> droneCompletionQueue;  // to Scheduler

    // State map for the drone
    private final Map<String, DroneState> stateMap;
    // Current state
    private DroneState currentState;

    // Shared data: current location, total flight time, and remaining foam.
    private Coordinates currentLocation;
    private double totalFlightTime;
    private double foamRemaining;

    public DroneSubsystem(int droneID,
                          BlockingQueue<Message> dronesQueue,
                          BlockingQueue<Message> droneCompletionQueue) {
        this.droneID = droneID;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;

        stateMap = new HashMap<>();
        stateMap.put("IDLE", new IdleState());
        stateMap.put("EN_ROUTE", new EnRouteState());
        stateMap.put("DROPPING", new DroppingAgentState());

        // Initial values
        this.currentLocation = new Coordinates(0, 0);
        this.totalFlightTime = 0.0;
        this.foamRemaining   = FOAM_CAPACITY;

        // Start as IDLE
        this.currentState = stateMap.get("IDLE");
    }

    @Override
    public void run() {
        while (true) {
            try {
                Message msg = dronesQueue.take();
                Logger.log("[DroneSubsystem-" + droneID + "]", "Received message => " + msg);
                currentState.handleEvent(this, DroneEvent.DISPATCH_RECEIVED, msg);
            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Interrupted => shutting down...");
                break;
            }
        }
    }

    public void setState(String stateName) {
        if (stateMap.containsKey(stateName)) {
            this.currentState = stateMap.get(stateName);
        } else {
            Logger.log("[DroneSubsystem-" + droneID + "]", "No such state => " + stateName + ". Keeping current state.");
        }
    }

    public DroneState getCurrentState() {
        return this.currentState;
    }

    public int getDroneID() { return droneID; }
    public BlockingQueue<Message> getDronesQueue() { return dronesQueue; }
    public BlockingQueue<Message> getDroneCompletionQueue() { return droneCompletionQueue; }

    public Coordinates getCurrentLocation() { return currentLocation; }
    public void setCurrentLocation(Coordinates c) { this.currentLocation = c; }

    public double getTotalFlightTime() { return totalFlightTime; }
    public void setTotalFlightTime(double t) { this.totalFlightTime = t; }

    public double getFoamRemaining() { return foamRemaining; }
    public void setFoamRemaining(double v) { this.foamRemaining = v; }

    public static double getMaxBatterySeconds() { return MAX_BATTERY_SECONDS; }
    public static double getFoamCapacity() { return FOAM_CAPACITY; }
}
