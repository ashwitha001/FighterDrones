package main;

import java.util.Timer;
import java.util.TimerTask;

public class DroppingAgentState implements DroneState {

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
        double needed = msg.getRemainingFoamNeeded();
        double droneFoam = subsystem.getFoamRemaining();

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
                needed,
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
                }
            }, (long)(faultTime * 1000));
        }

        if (droneFoam >= needed) {
            double dropTime = Utility.nozzleDropTime(needed);
            Thread.sleep((long) (dropTime * 1000));
            subsystem.setFoamRemaining(droneFoam - needed);
            Logger.log("[DroppingAgentState-" + droneID + "]", "FIRE_EXTINGUISHED => used " + needed + ", leftover foam=" + subsystem.getFoamRemaining());
            subsystem.sendToScheduler(new Message(
                    "FIRE_EXTINGUISHED",
                    droneID,
                    msg.getZoneID(),
                    msg.getSeverity(),
                    msg.getEventTime(),
                    msg.getEventTimeString(),
                    msg.getCenterX(),
                    msg.getCenterY(),
                    0.0,
                    msg.getEventID(),
                    faultType,
                    faultTime
            ));
        } else {
            double partialDrop = droneFoam;
            double leftover = needed - partialDrop;
            double dropTime = Utility.nozzleDropTime(partialDrop);
            Thread.sleep((long) (dropTime * 1000));
            subsystem.setFoamRemaining(0.0);
            Logger.log("[DroppingAgentState-" + droneID + "]", "PARTIAL_COVERAGE => leftover= " + leftover);
            subsystem.sendToScheduler(new Message(
                    "PARTIAL_COVERAGE",
                    droneID,
                    msg.getZoneID(),
                    msg.getSeverity(),
                    msg.getEventTime(),
                    msg.getEventTimeString(),
                    msg.getCenterX(),
                    msg.getCenterY(),
                    leftover,
                    msg.getEventID(),
                    faultType,
                    faultTime
            ));
        }
        if (faultTimer != null) {
            faultTimer.cancel();
        }
        Logger.log("[DroppingAgentState-" + droneID + "]", "Dropping done => RETURN_TO_BASE event.");
        subsystem.setState("EN_ROUTE");
        subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.RETURN_TO_BASE, msg);
    }
}