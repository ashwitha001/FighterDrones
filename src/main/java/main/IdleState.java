package main;

/**
 * IdleState:
 * - The drone is at base, presumably with fresh battery & foam.
 * - Main event: DISPATCH_RECEIVED => we transition to "EN_ROUTE" in the HashMap,
 *   so we can handle traveling to the zone.
 */
public class IdleState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        int droneID = subsystem.getDroneID();
        switch (event) {
            case DISPATCH_RECEIVED:
                Logger.log("[IdleState-" + droneID + "]", "DISPATCH_RECEIVED => transition to EN_ROUTE state.");
                subsystem.setState("EN_ROUTE");
                subsystem.getCurrentState().handleEvent(subsystem, event, msg);
                break;
            default:
                Logger.log("[IdleState-" + droneID + "]", "Ignoring event " + event + " while IDLE.");
        }
    }
}