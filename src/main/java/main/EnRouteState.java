package main;

/**
 * EnRouteState:
 * - The drone is traveling to the zone or eventually returning to base.
 * - After traveling, we raise ARRIVE_ZONE => transition to DroppingAgentState, etc.
 * - If traveling to base, we raise ARRIVE_BASE => IDLE.
 */
public class EnRouteState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        switch (event) {
            case DISPATCH_RECEIVED:
                // Travel to zone, then ARRIVE_ZONE
                travelToZone(subsystem, msg);
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.ARRIVE_ZONE, msg);
                break;

            case ARRIVE_ZONE:
                Logger.log("[EnRouteState]", "ARRIVE_ZONE => transition to DROPPING.");
                subsystem.setState("DROPPING");
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.START_DROPPING, msg);
                break;

            case RETURN_TO_BASE:
                travelBackToBase(subsystem);
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.ARRIVE_BASE, msg);
                break;

            case ARRIVE_BASE:
                Logger.log("[EnRouteState]", "ARRIVE_BASE => transition to IDLE.");
                subsystem.setState("IDLE");
                break;

            default:
                Logger.log("[EnRouteState]", "Ignoring event " + event + " while EN_ROUTE.");
        }
    }

    private void travelToZone(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        Coordinates curr = subsystem.getCurrentLocation();
        Coordinates tgt  = new Coordinates(msg.getCenterX(), msg.getCenterY());

        double tOut = Utility.computeTravelTime(curr.getX1(), curr.getY1(), tgt.getX1(), tgt.getY1());

        // battery check: time for out + back
        double tBack = Utility.computeTravelTime(tgt.getX1(), tgt.getY1(), 0,0);
        double newTotal = subsystem.getTotalFlightTime() + tOut + tBack;
        if (newTotal > DroneSubsystem.getMaxBatterySeconds()) {
            Logger.log("[EnRouteState]", "Not enough battery => skip traveling to zone.");
            // We do not transition or handle partial coverage here
            return;
        }

        String label = String.format("DRONE-%d traveling from (%d,%d) to (%d,%d)",
                subsystem.getDroneID(),
                curr.getX1(), curr.getY1(),
                tgt.getX1(),  tgt.getY1()
        );
        Utility.showProgress(tOut, label);

        subsystem.setCurrentLocation(tgt);
        subsystem.setTotalFlightTime(subsystem.getTotalFlightTime() + tOut);
    }

    private void travelBackToBase(DroneSubsystem subsystem) throws InterruptedException {
        Coordinates curr = subsystem.getCurrentLocation();
        double tBack = Utility.computeTravelTime(curr.getX1(), curr.getY1(), 0,0);
        double newTotal = subsystem.getTotalFlightTime() + tBack;
        if (newTotal > DroneSubsystem.getMaxBatterySeconds()) {
            Logger.log("[EnRouteState]", "No battery => can't return to base?");
            return;
        }

        String label = String.format("DRONE-%d returning from (%d,%d) to (0,0)",
                subsystem.getDroneID(),
                curr.getX1(), curr.getY1()
        );
        Utility.showProgress(tBack, label);

        subsystem.setCurrentLocation(new Coordinates(0,0));
        subsystem.setTotalFlightTime(newTotal);

        // Refuel
        Logger.log("[EnRouteState]", "Refueling foam & battery at base.");
        subsystem.setTotalFlightTime(0.0);
        subsystem.setFoamRemaining(DroneSubsystem.getFoamCapacity());
    }
}