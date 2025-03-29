package main;

public class FaultState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        if (event == DroneEvent.SHUTDOWN) {
            Logger.log("[FaultState-" + droneID + "]", "Shutdown command received. Shutting down drone.");

            subsystem.cancelFaultTimer();
            subsystem.setTimeoutTriggered(false);  // reset the flag
            subsystem.setState("EN_ROUTE");
            subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.RETURN_TO_BASE, msg);

        } else if (event == DroneEvent.DRONE_FAULT) {
            Logger.log("[FaultState-" + droneID + "]", "Fault detected: " + msg.getFaultType() +
                    ". Waiting for shutdown command from Scheduler.");
            // Optionally, continue waiting or perform local recovery here.
        } else if (event == DroneEvent.DISPATCH_RECEIVED) {
//            Logger.log("[FaultState-" + droneID + "]", "Fault detected: " + msg.getFaultType() +
//                    ". Waiting for shutdown command from Scheduler.");
//            subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.valueOf(msg.getType()), msg);
            Logger.log("[FaultState-" + droneID + "]", "ERROR: Received dispatch while in FaultState. Ignoring.");
        } else {
            Logger.log("[FaultState-" + droneID + "]", "In FaultState, ignoring event: " + event);
        }
    }
}
