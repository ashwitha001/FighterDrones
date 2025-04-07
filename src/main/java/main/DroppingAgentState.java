package main;

import java.util.Timer;
import java.util.TimerTask;
import java.util.Map;
import java.util.HashMap;

public class DroppingAgentState implements DroneState {

    private final Map<Integer, Double> fireTotalFoamNeeded = new HashMap<>();
    private final Map<Integer, Double> fireFoamApplied = new HashMap<>();

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        switch (event) {
            case START_DROPPING:
                Logger.log("[DroppingAgentState-" + droneID + "]", "START_DROPPING received. Beginning foam drop.");
                dropFoam(subsystem, msg);
                break;
            case DRONE_FAULT:
                Logger.log("[DroppingAgentState-" + droneID + "]", "DRONE_FAULT event received, transitioning to FaultState.");
                subsystem.setState("FAULT");
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.DRONE_FAULT, msg);
                break;
            default:
                Logger.log("[DroppingAgentState-" + droneID + "]", "Ignoring event " + event + " while DROPPING.");
        }
    }

    private void dropFoam(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        double totalFoamNeeded = msg.getRemainingFoamNeeded();  // Total foam still needed for the fire
        double droneFoam = subsystem.getFoamRemaining();        // How much foam this drone has

        // Send initial update.
        subsystem.sendToScheduler(new Message(
                "DRONE_DROPPING",
                droneID,
                msg.getZoneID(),
                msg.getSeverity(),
                msg.getEventTime(),
                msg.getEventTimeString(),
                msg.getCenterX(),
                msg.getCenterY(),
                totalFoamNeeded,
                msg.getEventID(),
                msg.getFaultType(),
                msg.getFaultTime()
        ));

        // Start a fault timer for dropping.
        String faultType = msg.getFaultType();
        double faultTime = msg.getFaultTime();
        Timer faultTimer = null;
        if (faultType != null && !faultType.isEmpty() && faultTime > 0) {
            faultTimer = new Timer();
            faultTimer.schedule(new TimerTask() {
                @Override
                public void run() {
                    Logger.log("[DroppingAgentState-" + droneID + "]", "Fault triggered during dropping: " + faultType);
                    Message faultMsg = new Message(
                            "DRONE_FAULT",
                            droneID,
                            msg.getZoneID(),
                            msg.getSeverity(),
                            msg.getEventTime(),
                            msg.getEventTimeString(),
                            msg.getCenterX(),
                            msg.getCenterY(),
                            subsystem.getFoamRemaining(),
                            msg.getEventID(),
                            faultType,
                            faultTime
                    );
                    subsystem.sendToScheduler(faultMsg);
                    
                    // Handle the fault based on type
                    switch (faultType) {
                        case "NOZZLE_JAM":
                            // For NOZZLE_JAM, we'll wait for RETURN_TO_BASE command from Scheduler
                            Logger.log("[DroppingAgentState-" + droneID + "]", "Nozzle jam detected during dropping. Waiting for return command.");
                            break;
                        case "STUCK_EN_ROUTE":
                            // For STUCK_EN_ROUTE, we stay where we are
                            Logger.log("[DroppingAgentState-" + droneID + "]", "Stuck en route during dropping. Remaining in current position.");
                            subsystem.setState("FAULT");
                            break;
                    }
                }
            }, (long)(faultTime * 1000));
        }

        // Calculate how much foam this drone can apply
        double foamToApply = Math.min(droneFoam, totalFoamNeeded);  // Apply only what's needed
        double dropTime = Utility.nozzleDropTime(foamToApply);
        
        // Check if fault occurred before dropping
        if (subsystem.getTimeoutTriggered()) {
            Logger.log("[DroppingAgentState-" + droneID + "]", "Fault occurred before dropping foam. Aborting drop.");
            if (faultTimer != null) {
                faultTimer.cancel();
            }
            return;
        }
        
        Thread.sleep((long) (dropTime * 1000));
        
        // Check if fault occurred during dropping
        if (subsystem.getTimeoutTriggered()) {
            Logger.log("[DroppingAgentState-" + droneID + "]", "Fault occurred during foam dropping. Aborting drop.");
            if (faultTimer != null) {
                faultTimer.cancel();
            }
            return;
        }
        
        // Update drone's foam remaining
        subsystem.setFoamRemaining(droneFoam - foamToApply);
        
        // Calculate remaining foam needed for the fire after this drone's contribution
        double remainingFoamNeeded = totalFoamNeeded - foamToApply;
        
        // First send FOAM_FINISHED to indicate this drone has completed its foam application
        Message foamFinishedMsg = new Message(
                "FOAM_FINISHED",
                droneID,
                msg.getZoneID(),
                msg.getSeverity(),
                msg.getEventTime(),
                msg.getEventTimeString(),
                msg.getCenterX(),
                msg.getCenterY(),
                remainingFoamNeeded,
                msg.getEventID(),
                faultType,
                faultTime
        );
        subsystem.sendToScheduler(foamFinishedMsg);

        // If this drone has extinguished the fire, send FIRE_EXTINGUISHED message
        if (remainingFoamNeeded <= 0) {
            Message fireExtinguishedMsg = new Message(
                    "FIRE_EXTINGUISHED",
                    droneID,
                    msg.getZoneID(),
                    "EXTINGUISHED",
                    msg.getEventTime(),
                    msg.getEventTimeString(),
                    msg.getCenterX(),
                    msg.getCenterY(),
                    0.0,
                    msg.getEventID(),
                    faultType,
                    faultTime
            );
            subsystem.sendToScheduler(fireExtinguishedMsg);
        } else {
            // If fire is not fully extinguished, send PARTIAL_COVERAGE
            Message partialCoverageMsg = new Message(
                    "PARTIAL_COVERAGE",
                    droneID,
                    msg.getZoneID(),
                    msg.getSeverity(),  // send current severity; Scheduler will recalculate
                    msg.getEventTime(),
                    msg.getEventTimeString(),
                    msg.getCenterX(),
                    msg.getCenterY(),
                    remainingFoamNeeded,  // from this drone's local view
                    msg.getEventID(),
                    faultType,
                    faultTime
            );
            subsystem.sendToScheduler(partialCoverageMsg);
        }

        if (faultTimer != null) {
            faultTimer.cancel();
        }
        
        // Only proceed with return if no fault occurred
        if (!subsystem.getTimeoutTriggered()) {
            Logger.log("[DroppingAgentState-" + droneID + "]", "Dropping done => RETURN_TO_BASE event.");
            subsystem.setState("EN_ROUTE");
            subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.FOAM_FINISHED, msg);
            subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.RETURN_TO_BASE, msg);
        }
    }
}