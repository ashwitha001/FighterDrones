package main;

/**
 * EnRouteState:
 * - The drone is traveling either to the zone or back to base.
 * - If event=DISPATCH_RECEIVED => we interpret that as traveling to the zone.
 * - After travel, we do ARRIVE_ZONE => new event => DroppingAgentState, etc.
 * - If we want to handle returning to base, we do RETURN_TO_BASE, ARRIVE_BASE, etc.
 */
public class EnRouteState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        switch (event) {
            case DISPATCH_RECEIVED:
                // Means we have a target zone => do the traveling
                travelToZone(subsystem, msg);
                // Then we raise ARRIVE_ZONE event
                subsystem.getState().handleEvent(subsystem, DroneEvent.ARRIVE_ZONE, msg);
                break;

            case ARRIVE_ZONE:
                Logger.log("[EnRouteState]", "Arrived => switching to DroppingAgentState");
                subsystem.setState(new DroppingAgentState());
                subsystem.getState().handleEvent(subsystem, DroneEvent.START_DROPPING, msg);
                break;

            case RETURN_TO_BASE:
                travelBackToBase(subsystem, msg);
                // Then ARRIVE_BASE => we can do another event
                subsystem.getState().handleEvent(subsystem, DroneEvent.ARRIVE_BASE, msg);
                break;

            case ARRIVE_BASE:
                Logger.log("[EnRouteState]", "Arrived at base => IdleState");
                subsystem.setState(new IdleState());
                break;

            default:
                Logger.log("[EnRouteState]", "Ignoring " + event + " while EN_ROUTE.");
        }
    }

    private void travelToZone(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        Coordinates curr = subsystem.getCurrentLocation();
        Coordinates tgt  = new Coordinates(msg.getCenterX(), msg.getCenterY());

        double tOut = Utility.computeTravelTime(
                curr.getX1(), curr.getY1(),
                tgt.getX1(),  tgt.getY1()
        );
        // battery check
        double tBack  = Utility.computeTravelTime(tgt.getX1(), tgt.getY1(), 0,0);
        double newTotal = subsystem.getTotalFlightTime() + tOut + tBack;
        if (newTotal > DroneSubsystem.getMaxBatterySeconds()) {
            Logger.log("[EnRouteState]", "Insufficient battery => skip traveling");
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

    private void travelBackToBase(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        Coordinates curr = subsystem.getCurrentLocation();
        double tBack = Utility.computeTravelTime(curr.getX1(), curr.getY1(), 0,0);
        double newTotal = subsystem.getTotalFlightTime() + tBack;
        if (newTotal > DroneSubsystem.getMaxBatterySeconds()) {
            Logger.log("[EnRouteState]", "No battery => cannot return to base");
            return;
        }

        String label = String.format("DRONE-%d returning from (%d,%d) to (0,0)",
                subsystem.getDroneID(),
                curr.getX1(), curr.getY1()
        );
        Utility.showProgress(tBack, label);

        subsystem.setCurrentLocation(new Coordinates(0,0));
        subsystem.setTotalFlightTime(newTotal);

        // Refill foam & battery
        Logger.log("[EnRouteState]", "Refuel at base => foam & battery reset");
        subsystem.setTotalFlightTime(0.0);
        subsystem.setFoamRemaining(DroneSubsystem.getFoamCapacity());
    }
}