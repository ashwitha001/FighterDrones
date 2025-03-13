package main;

/**
 * DroppingAgentState:
 * - Drone is actively dropping agent.
 * - partial coverage => leftover re-queued
 * - full coverage => FIRE_EXTINGUISHED
 * - Then we do => RETURN_TO_BASE event
 */
public class DroppingAgentState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        switch (event) {
            case START_DROPPING:
                dropFoam(subsystem, msg);
                break;

            default:
                Logger.log("[DroppingAgentState]",
                        "Ignoring event " + event + " while DROPPING_AGENT.");
        }
    }

    private void dropFoam(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        double needed    = msg.getRemainingFoamNeeded();
        double droneFoam = subsystem.getFoamRemaining();
        double actualFoamDropped = Math.min(needed, droneFoam);

        // DRONE_DROPPING => notify scheduler
        subsystem.getDroneCompletionQueue().put(new Message(
                "DRONE_DROPPING",
                subsystem.getDroneID(),
                msg.getZoneID(),
                msg.getSeverity(),
                msg.getEventTime(),
                msg.getEventTimeString(),
                msg.getCenterX(),
                msg.getCenterY(),
                actualFoamDropped
        ));

        if (droneFoam >= needed) {
            // full coverage
            double dropTime = Utility.nozzleDropTime(needed);
            Thread.sleep((long)(dropTime * 1000));
            subsystem.setFoamRemaining(droneFoam - needed);

            Logger.log("[DroppingAgentState]",
                    "FIRE_EXTINGUISHED => used " + needed
                            + ", leftover foam=" + subsystem.getFoamRemaining());

            subsystem.getDroneCompletionQueue().put(new Message(
                    "FIRE_EXTINGUISHED",
                    subsystem.getDroneID(),
                    msg.getZoneID(),
                    msg.getSeverity(),
                    msg.getEventTime(),
                    msg.getEventTimeString(),
                    msg.getCenterX(),
                    msg.getCenterY(),
                    0.0
            ));
        } else {
            // partial coverage
            double leftover = needed - actualFoamDropped;
            double dropTime = Utility.nozzleDropTime(actualFoamDropped);
            Thread.sleep((long)(dropTime * 1000));
            subsystem.setFoamRemaining(0.0);

            Logger.log("[DroppingAgentState]",
                    "PARTIAL_COVERAGE => leftover= " + leftover);

            subsystem.getDroneCompletionQueue().put(new Message(
                    "PARTIAL_COVERAGE",
                    subsystem.getDroneID(),
                    msg.getZoneID(),
                    msg.getSeverity(),
                    msg.getEventTime(),
                    msg.getEventTimeString(),
                    msg.getCenterX(),
                    msg.getCenterY(),
                    leftover
            ));
        }

        // After dropping => RETURN_TO_BASE
        Logger.log("[DroppingAgentState]", "Dropping done => RETURN_TO_BASE event.");
        subsystem.setState(new EnRouteState());
        subsystem.getState().handleEvent(subsystem, DroneEvent.RETURN_TO_BASE, msg);
    }
}