package main;

public class FaultState implements DroneState {
    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        if (subsystem.isShutDown()) return;

        switch (event) {
            case SHUTDOWN:
                Logger.log("[FaultState-" + droneID + "]", "Shutdown command received. Shutting down drone.");
                subsystem.cancelFaultTimer();
                subsystem.setTimeoutTriggered(false);
                subsystem.shutDown();
                return;

            case RESET_CONNECTION:
                subsystem.resetConnection();
                Message resumeMsg = subsystem.getLastKnownMessage();
                if (resumeMsg != null) {
                    Logger.log("[FaultState-" + droneID + "]", "Reprocessing previous event: " + resumeMsg.getType());
                    DroneEvent resumeEvent = DroneEvent.valueOf(resumeMsg.getType());
                    if (resumeEvent == DroneEvent.DISPATCH_RECEIVED || resumeEvent == DroneEvent.DIVERT) {
                        subsystem.setState("EN_ROUTE");
                    }
                    subsystem.getCurrentState().handleEvent(subsystem, resumeEvent, resumeMsg);
                }
                break;

            case RETURN_TO_BASE:
                Logger.log("[FaultState-" + droneID + "]", "RETURN_TO_BASE command received. Returning to base.");
                subsystem.setTimeoutTriggered(false); // ✅ clear fault flag before returning
                subsystem.setState("EN_ROUTE");
                subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.RETURN_TO_BASE, msg);
                break;

            case DRONE_FAULT:
                Logger.log("[FaultState-" + droneID + "]", "Fault detected: " + msg.getFaultType());
                break;

            case DISPATCH_RECEIVED:
                Logger.log("[FaultState-" + droneID + "]", "ERROR: Received dispatch while in FaultState. Ignoring.");
                break;

            default:
                Logger.log("[FaultState-" + droneID + "]", "In FaultState, ignoring event: " + event);
        }
    }
    @Override
    public String toString() {
        return "FAULT";
    }
}