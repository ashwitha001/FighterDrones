package main;

public class EnRouteState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        switch (event) {
            case DISPATCH_RECEIVED:
                Logger.log("[EnRouteState-" + droneID + "]", "DISPATCH_RECEIVED => traveling to fire zone.");
                travelToZone(subsystem, msg);
                // Arrival successful; cancel any fault timer.
                subsystem.cancelFaultTimer();
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.ARRIVE_ZONE, msg);
                break;
            case DRONE_FAULT:
                Logger.log("[EnRouteState-" + droneID + "]", "DRONE_FAULT event received, transitioning to FaultState.");
                subsystem.setState("FAULT");
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.DRONE_FAULT, msg);
                break;
            case RETURN_TO_BASE:
                if (subsystem.getFoamRemaining() > 0) {
                    Logger.log("[EnRouteState-" + droneID + "]", "Foam remains (" + subsystem.getFoamRemaining() + " kg). Notifying Scheduler and waiting for potential diversion.");
                    Coordinates loc = subsystem.getCurrentLocation();
                    subsystem.sendToScheduler(new Message(
                            "DRONE_RETURNING",
                            droneID,
                            0,
                            "RETURNING",
                            msg.getEventTime(),
                            msg.getEventTimeString(),
                            loc.getX1(),
                            loc.getY1(),
                            subsystem.getFoamRemaining(),
                            msg.getEventID(),
                            "", 0.0
                    ));
                    Thread.sleep(500);
                    Logger.log("[EnRouteState-" + droneID + "]", "No diversion received => proceeding to base.");
                }
                travelBackToBase(subsystem, msg);
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.ARRIVE_BASE, msg);
                break;
            case DIVERT:
                Logger.log("[EnRouteState-" + droneID + "]", "DIVERT event received. Diverting to new fire zone.");
                travelToZone(subsystem, msg);
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.ARRIVE_ZONE, msg);
                break;
            case ARRIVE_ZONE:
                Logger.log("[EnRouteState-" + droneID + "]", "ARRIVE_ZONE => transition to DROPPING state.");
                subsystem.setState("DROPPING");
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.START_DROPPING, msg);
                break;
            case ARRIVE_BASE:
                Logger.log("[EnRouteState-" + droneID + "]", "ARRIVE_BASE => transition to IDLE state.");
                subsystem.setState("IDLE");
                subsystem.sendToScheduler(new Message(
                        "DRONE_IDLE",
                        droneID,
                        0,
                        "IDLE",
                        msg.getEventTime(),
                        msg.getEventTimeString(),
                        0,
                        0,
                        0.0,
                        msg.getEventID(),
                        "", 0.0
                ));
                break;
            default:
                Logger.log("[EnRouteState-" + droneID + "]", "Ignoring event " + event + " while EN_ROUTE.");
        }
    }

    private void travelToZone(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        Coordinates curr = subsystem.getCurrentLocation();
        Coordinates tgt = new Coordinates(msg.getCenterX(), msg.getCenterY());
        double tOut = Utility.computeTravelTime(curr.getX1(), curr.getY1(), tgt.getX1(), tgt.getY1());
        String label = String.format("DRONE-%d traveling from (%d,%d) to (%d,%d)",
                droneID, curr.getX1(), curr.getY1(), tgt.getX1(), tgt.getY1());
        Utility.showProgress(tOut, label);
        subsystem.setCurrentLocation(tgt);
        subsystem.setTotalFlightTime(subsystem.getTotalFlightTime() + tOut);
    }

    private void travelBackToBase(DroneSubsystem subsystem, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        Coordinates curr = subsystem.getCurrentLocation();
        double tBack = Utility.computeTravelTime(curr.getX1(), curr.getY1(), 0, 0);
        double newTotal = subsystem.getTotalFlightTime() + tBack;
        if (newTotal > DroneSubsystem.getMaxBatterySeconds()) {
            Logger.log("[EnRouteState-" + droneID + "]", "Insufficient battery => cannot return to base.");
            return;
        }
        String label = String.format("DRONE-%d returning from (%d,%d) to (0,0)",
                droneID, curr.getX1(), curr.getY1());
        Utility.showProgress(tBack, label);
        subsystem.setCurrentLocation(new Coordinates(0, 0));
        subsystem.setTotalFlightTime(newTotal);
        Logger.log("[EnRouteState-" + droneID + "]", "Refueling foam & battery at base.");
        subsystem.setTotalFlightTime(0.0);
        subsystem.setFoamRemaining(DroneSubsystem.getFoamCapacity());
    }
}