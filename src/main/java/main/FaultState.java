package main;

public class FaultState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        if (event == DroneEvent.SHUTDOWN) {
            Logger.log("[FaultState-" + droneID + "]", "Shutdown command received. Shutting down drone.");
            // Perform any cleanup as needed and then shut down.
            System.exit(0);
        } else if (event == DroneEvent.DRONE_FAULT) {
            Logger.log("[FaultState-" + droneID + "]", "Fault detected: " + msg.getFaultType() +
                    ". Waiting for shutdown command from Scheduler.");
            // Optionally, continue waiting or perform local recovery here.
        } else if (event == DroneEvent.DISPATCH_RECEIVED) {
            Logger.log("[FaultState-" + droneID + "]", "Fault detected: " + msg.getFaultType() +
                    ". Waiting for shutdown command from Scheduler.");
            subsystem.getCurrentState().handleEvent(subsystem, DroneEvent.valueOf(msg.getType()), msg);
        } else {
            Logger.log("[FaultState-" + droneID + "]", "In FaultState, ignoring event: " + event);
        }
    }
}
