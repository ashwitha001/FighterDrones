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
        switch (event) {
            case DISPATCH_RECEIVED:
                Logger.log("[IdleState]", "DISPATCH_RECEIVED => transition to EN_ROUTE state.");
                // We'll set the drone's current state to the "EN_ROUTE" object from the HashMap
                subsystem.setState("EN_ROUTE");
                // Then let that new state handle the same event to do the traveling logic
                subsystem.getCurrentState().handleEvent(subsystem, event, msg);
                break;

            default:
                Logger.log("[IdleState]", "Ignoring event " + event + " while IDLE.");
        }
    }
}