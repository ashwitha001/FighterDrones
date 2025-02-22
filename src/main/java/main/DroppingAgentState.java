package main;

/**
 * DroppingAgentState:
 * - The drone is actively dropping foam.
 * - If foam < needed => PARTIAL_COVERAGE => leftover re-queued in scheduler.
 * - If foam >= needed => FIRE_EXTINGUISHED.
 * - Then => RETURN_TO_BASE => transitions to EN_ROUTE => eventually IDLE.
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
                        "Ignoring event " + event + " while DROPPING.");
        }
    }

    private void dropFoam(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        double needed    = msg.getRemainingFoamNeeded();
        double droneFoam = subsystem.getFoamRemaining();

        // Mark DRONE_DROPPING
        subsystem.getDroneCompletionQueue().put(new Message(
                "DRONE_DROPPING",
                subsystem.getDroneID(),
                msg.getZoneID(),
                msg.getSeverity(),
                msg.getEventTime(),
                msg.getEventTimeString(),
                msg.getCenterX(),
                msg.getCenterY(),
                needed
        ));

        if (droneFoam >= needed) {
            // fully extinguish
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
            double partialDrop = droneFoam;
            double leftover    = needed - partialDrop;
            double dropTime    = Utility.nozzleDropTime(partialDrop);
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

        // Return to base => transitions to EN_ROUTE => ARRIVE_BASE => IDLE
        Logger.log("[DroppingAgentState]", "Dropping done => RETURN_TO_BASE event.");
        subsystem.setState("EN_ROUTE");
        subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.RETURN_TO_BASE, msg);
    }
}