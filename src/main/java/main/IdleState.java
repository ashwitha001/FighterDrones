package main;

/**
 * IdleState:
 * - Drone is at base, presumably with fresh battery & foam.
 * - Main event: DISPATCH_RECEIVED => transition to EnRouteState.
 */
public class IdleState implements DroneState {

    @Override
    public void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException {
        switch (event) {
            case DISPATCH_RECEIVED:
                // Transition from Idle -> EnRouteState
                subsystem.setState(new EnRouteState());
                // Let EnRouteState handle the same event (some designs pass it on),
                // so it can do the travel logic:
                subsystem.getState().handleEvent(subsystem, event, msg);
                break;

            default:
                Logger.log("[IdleState]", "Ignoring event " + event + " while IDLE.");
        }
    }
}