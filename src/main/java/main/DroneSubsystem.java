package main;

import java.io.IOException;
import java.net.DatagramSocket;
import java.net.InetSocketAddress;
import java.net.SocketException;
import java.util.HashMap;
import java.util.HashSet;
import java.util.Map;
import java.util.Set;
import java.util.Timer;
import java.util.TimerTask;

public class DroneSubsystem implements Runnable {

    private SimulationUI simulationUI = null;

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
    private volatile Message lastKnownMessage = null;  // Store the last message
    private final Set<String> firesWithFaults = new HashSet<>();  // Track fires that have had faults triggered
    private final Set<String> attemptedFires = new HashSet<>();  // Track fires this drone has attempted to handle

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
        PerformanceLogger.initializeDroneIdleStart(droneID);


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

                // Convert the message type to a DroneEvent. If invalid, log and exit.
                DroneEvent event;
                try {
                    event = DroneEvent.valueOf(m.getType());
                } catch (IllegalArgumentException e) {
                    Logger.log("[DroneSubsystem-" + droneID + "]", "Invalid event type: " + m.getType());
                    return;
                }

                // Handle SHUTDOWN and RESET_CONNECTION events immediately.
                if (event == DroneEvent.SHUTDOWN) {
                    Logger.log("[DroneSubsystem-" + droneID + "]", "Shutdown command received. Shutting down.");
                    shutDown();
                    return;
                } else if (event == DroneEvent.RESET_CONNECTION) {
                    Logger.log("[DroneSubsystem-" + droneID + "]", "Reset connection command received. Resetting connection.");
                    resetConnection();
                    return;
                }

                // If a dispatch (or divert) message has fault data, start the fault timer.
                if ((event == DroneEvent.DISPATCH_RECEIVED || event == DroneEvent.DIVERT)
                        && m.getFaultType() != null && !m.getFaultType().isEmpty()
                        && m.getFaultTime() > 0) {
                    String fireKey = m.getEventID();  // Unique identifier for the fire
                    if (!firesWithFaults.contains(fireKey)) {
                        firesWithFaults.add(fireKey);
                        if (!timeoutTriggered) {
                            startFaultTimer(m);
                        }
                    } else {
                        // For subsequent dispatches for the same fire, clear fault info.
                        m = new Message(
                                m.getType(),
                                m.getDroneID(),
                                m.getZoneID(),
                                m.getSeverity(),
                                m.getEventTime(),
                                m.getEventTimeString(),
                                m.getCenterX(),
                                m.getCenterY(),
                                m.getRemainingFoamNeeded(),
                                m.getEventID(),
                                "",  // Clear fault type
                                0.0  // Clear fault time
                        );
                    }
                }

                // Check if this drone has already attempted to handle this fire.
                if ((event == DroneEvent.DISPATCH_RECEIVED || event == DroneEvent.DIVERT)
                        && attemptedFires.contains(m.getEventID())) {
                    Logger.log("[DroneSubsystem-" + droneID + "]", "Ignoring dispatch to fire " + m.getEventID()
                            + " - already attempted to handle this fire.");
                    return;
                }

                // If handling a new dispatch or divert, record that we attempted the fire.
                if (event == DroneEvent.DISPATCH_RECEIVED || event == DroneEvent.DIVERT) {
                    attemptedFires.add(m.getEventID());
                }

                // Pass the message to the current state's handler.
                currentState.handleEvent(this, event, m);
                // Save the last known message for performance tracking if needed.
                lastKnownMessage = m;
            } catch (InterruptedException ex) {
                Logger.log("[DroneSubsystem-" + droneID + "]", "Interrupted in message handling.");
            }
        }), "DroneReceiver-" + droneID);
        receiverThread.start();

        // Send registration message to Scheduler.
        // Use centerX to carry the ephemeral port.
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

    // Fault timer management: Do not cancel the timer if the fault should persist
    public synchronized void startFaultTimer(Message m) {
        cancelFaultTimer();
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
                            currentLocation.getX1(),  // Use current location
                            currentLocation.getY1(),
                            getFoamRemaining(),
                            m.getEventID(),
                            m.getFaultType(),
                            m.getFaultTime()
                    );
                    timeoutTriggered = true;

                    // Handle the fault based on type
                    switch (m.getFaultType()) {
                        case "NOZZLE_JAM":
                            // For NOZZLE_JAM, we'll wait for RETURN_TO_BASE command from Scheduler
                            Logger.log("[DroneSubsystem-" + droneID + "]", "Nozzle jam detected. Waiting for return command.");
                            break;
                        case "STUCK_EN_ROUTE":
                            // For STUCK_EN_ROUTE, we stay where we are and stop all movement
                            Logger.log("[DroneSubsystem-" + droneID + "]", "Stuck en route. Remaining in current position.");
                            setState("FAULT");
                            // Send a final location update to show where we're stuck
                            sendToScheduler(new Message(
                                    "DRONE_COORD_UPDATE",
                                    droneID,
                                    0,
                                    "STUCK",
                                    m.getEventTime(),
                                    m.getEventTimeString(),
                                    currentLocation.getX1(),
                                    currentLocation.getY1(),
                                    0.0,
                                    m.getEventID(),
                                    "",
                                    0.0
                            ));
                            break;
                    }
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
        // Cleanup operations (e.g., close socket) can be performed here.
        if (socket != null && !socket.isClosed()) {
            socket.close();
        }
        Logger.log("[DroneSubsystem-" + droneID + "]", "System shutdown.");
    }

    // Simulate a reset connection by pausing for 10 seconds, then resuming.
    public void resetConnection() throws InterruptedException {
        Logger.log("[DroneSubsystem-" + droneID + "]", "Resetting connection. Pausing for 10 seconds...");
        Thread.sleep(10000);
        Logger.log("[DroneSubsystem-" + droneID + "]", "Resuming operations after connection reset.");
        setTimeoutTriggered(false);
    }

    // Shut down the drone: stop processing messages, cancel timers, and mark as shut down.
    public void shutDown() {
        Logger.log("[DroneSubsystem-" + droneID + "]", "Executing shutdown procedure.");
        isShutDown = true;
        cancelFaultTimer();
        // Additional clean-up (e.g., closing the socket) is handled in run() upon loop exit.
    }

    public void setState(String stateName) {
        if (stateMap.containsKey(stateName)) {
            this.currentState = stateMap.get(stateName);
        } else {
            Logger.log("[DroneSubsystem-" + droneID + "]", "No such state: " + stateName + ". Keeping current state.");
        }
    }

    public Map<String, DroneState> getStateMap() {
        return stateMap;
    }

    public void setLastKnownMessage(Message msg) {
        this.lastKnownMessage = msg;
    }

    public Message getLastKnownMessage() {
        return this.lastKnownMessage;
    }

    public boolean isShutDown() { return isShutDown; }

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

    public void setSimulationUI(SimulationUI ui) {
        this.simulationUI = ui;
    }

    public SimulationUI getSimulationUI() {
        return simulationUI;
    }
}
