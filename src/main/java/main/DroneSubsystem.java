package main;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.Map;
import java.util.Timer;
import java.util.TimerTask;


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

    // Fault timer (used to inject faults based on faultTime)
    private Timer faultTimer = null;

        // Flag for when fault happens
    private volatile boolean timeoutTriggered = false;

    private volatile boolean isShutDown = false;
    private volatile Message lastKnownMessage = null;  // 🆕 store the last message
    
    public DroneSubsystem(int droneID, InetSocketAddress schedulerAddress) {
        this.droneID = droneID;
        this.schedulerAddress = schedulerAddress;

        stateMap = new HashMap<>();
        stateMap.put("IDLE", new IdleState());
        stateMap.put("EN_ROUTE", new EnRouteState());
        stateMap.put("DROPPING", new DroppingAgentState());
        stateMap.put("FAULT", new FaultState());
        
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

                DroneEvent event;
                try {
                    event = DroneEvent.valueOf(m.getType());
                } catch (IllegalArgumentException e) {
                    Logger.log("[DroneSubsystem-" + droneID + "]", "Invalid event type: " + m.getType());
                    return;
                }

                // If the message is a dispatch (or divert) with fault data, start the fault timer.
                if ((event == DroneEvent.DISPATCH_RECEIVED || event == DroneEvent.DIVERT)
                        && m.getFaultType() != null && !m.getFaultType().isEmpty()
                        && m.getFaultTime() > 0) {
                    if (!timeoutTriggered) {
                        startFaultTimer(m);
                    }

                }
//                currentState.handleEvent(this, DroneEvent.DISPATCH_RECEIVED, m);
                currentState.handleEvent(this, event, m);
            } catch (InterruptedException ex) {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Interrupted in message handling.");
            }
        }), "DroneReceiver-" + droneID);
        receiverThread.start();

        // Send registration message to Scheduler.
        // Use centerX to carry our ephemeral port; no fault data for registration.
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
                "REG_" + droneID,
                "", 0.0
        );
        sendToScheduler(registration);
    }

    // Fault timer management
    public synchronized void startFaultTimer(Message m) {
        // Cancel any existing timer.
        cancelFaultTimer();
        // Start a new timer if fault info is present.
        if (m.getFaultType() != null && !m.getFaultType().isEmpty() && m.getFaultTime() > 0) {
            faultTimer = new Timer();
            faultTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Logger.log("[DroneSubsystem-" + droneID + "]", "Fault triggered: " + m.getFaultType());
                    Message faultMsg = new Message(
                            "DRONE_FAULT",
                            droneID,
                            m.getZoneID(),
                            m.getSeverity(),
                            m.getEventTime(),
                            m.getEventTimeString(),
                            m.getCenterX(),
                            m.getCenterY(),
                            getFoamRemaining(),
                            m.getEventID(),
                            m.getFaultType(),
                            m.getFaultTime()
                    );
                    timeoutTriggered = true;
                    sendToScheduler(faultMsg);
                }
            }, (long)(m.getFaultTime() * 1000));
        }
    }

    public synchronized void cancelFaultTimer() {
        if (faultTimer != null) {
            faultTimer.cancel();
            faultTimer = null;
        }
    }

    @Override
    public void run() {
        while (!Thread.currentThread().isInterrupted() && !isShutDown) {
            try {
                Thread.sleep(1000);
            } catch (InterruptedException e) {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Interrupted => shutting down...");
                Thread.currentThread().interrupt();
                break;
            }
        }
        Logger.log("[DroneSubsystem-" + droneID + "]", "System shutdown.");
    }

    public void resetConnection() throws InterruptedException {
        Logger.log("[DroneSubsystem-" + droneID + "]", "resetting connection...");
        Thread.sleep(10000);
        Logger.log("[DroneSubsystem-" + droneID + "]", "resuming operations.");
        setTimeoutTriggered(false); // ✅ clear fault flag after reset
    }

    public void setState(String stateName) {
        if (stateMap.containsKey(stateName)) {
            this.currentState = stateMap.get(stateName);
        } else {
            Logger.log("[DroneSubsystem-" + droneID + "]", "No such state => " + stateName + ". Keeping current state.");
        }
    }

    public Map <String, DroneState> getStateMap() {
        return stateMap;
    }

    public void setLastKnownMessage(Message msg) {
        this.lastKnownMessage = msg;
    }

    public Message getLastKnownMessage() {
        return this.lastKnownMessage;
    }
    public boolean isShutDown() { return isShutDown; }
    public void shutDown() { isShutDown = true; }

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

    public boolean getTimeoutTriggered() {
        return timeoutTriggered;
    }
    
    public void sendToScheduler(Message m) {
        try {
            UDPUtil.sendMessage(m, schedulerAddress);
        } catch (IOException e) {
            Logger.log("[DroneSubsystem-" + droneID + "]", "Error sending to Scheduler: " + e.getMessage());
        }
    }

    public void setTimeoutTriggered(boolean value) {
        timeoutTriggered = value;
    }
}
