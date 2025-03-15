package main;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;

public class DroneSubsystem implements Runnable {

    private static final double MAX_BATTERY_SECONDS = 3600.0;
    private static final double FOAM_CAPACITY = 15.0;

    private final int droneID;
    private final InetSocketAddress schedulerAddress;
    private DatagramSocket socket; // ephemeral socket
    private final Map<String, DroneState> stateMap;
    private DroneState currentState;

    // Shared data
    private Coordinates currentLocation;
    private double totalFlightTime;
    private double foamRemaining;

    public DroneSubsystem(int droneID, InetSocketAddress schedulerAddress) {
        this.droneID = droneID;
        this.schedulerAddress = schedulerAddress;

        stateMap = new HashMap<>();
        stateMap.put("IDLE", new IdleState());
        stateMap.put("EN_ROUTE", new EnRouteState());
        stateMap.put("DROPPING", new DroppingAgentState());

        currentLocation = new Coordinates(0, 0);
        totalFlightTime = 0.0;
        foamRemaining = FOAM_CAPACITY;
        currentState = stateMap.get("IDLE");

        try {
            // Bind to an ephemeral port.
            this.socket = new DatagramSocket();
            Logger.log("[DroneSubsystem-" + droneID + "]", "Bound to port " + socket.getLocalPort());
        } catch (SocketException e) {
            Logger.log("[DroneSubsystem-" + droneID + "]", "SocketException: " + e.getMessage());
        }

        // Start the UDPReceiver for this drone.
        Thread receiverThread = new Thread(new UDPReceiver(socket, m -> {
            try {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Received message => " + m);
                currentState.handleEvent(this, DroneEvent.DISPATCH_RECEIVED, m);
            } catch (InterruptedException ex) {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Interrupted in message handling.");
            }
        }), "DroneReceiver-" + droneID);
        receiverThread.start();

        // Send registration message to Scheduler.
        // We'll use the centerX field to carry our ephemeral port.
        Message registration = new Message(
                "DRONE_REGISTRATION",
                droneID,
                0,
                "REGISTER",
                java.time.LocalTime.now(),
                java.time.LocalTime.now().toString(),
                socket.getLocalPort(),
                0,
                0.0,
                "REG_" + droneID
        );
        sendToScheduler(registration);
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Interrupted => shutting down...");
                Thread.currentThread().interrupt();
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

    public Map<String, DroneState> getStateMap(){
        return stateMap;
    }

    public DroneState getCurrentState() {
        return currentState;
    }

    public int getDroneID() {
        return droneID;
    }

    public Coordinates getCurrentLocation() {
        return currentLocation;
    }

    public void setCurrentLocation(Coordinates c) {
        this.currentLocation = c;
    }

    public double getTotalFlightTime() {
        return totalFlightTime;
    }

    public void setTotalFlightTime(double t) {
        this.totalFlightTime = t;
    }

    public double getFoamRemaining() {
        return foamRemaining;
    }

    public void setFoamRemaining(double v) {
        this.foamRemaining = v;
    }

    public static double getMaxBatterySeconds() {
        return MAX_BATTERY_SECONDS;
    }

    public static double getFoamCapacity() {
        return FOAM_CAPACITY;
    }

    public void sendToScheduler(Message m) {
        try {
            UDPUtil.sendMessage(m, schedulerAddress);
        } catch (IOException e) {
            Logger.log("[DroneSubsystem-" + droneID + "]", "Error sending to Scheduler: " + e.getMessage());
        }
    }
}