package main;

/**
 * DroneState:
 * - The interface for each concrete drone state (IdleState, EnRouteState, DroppingAgentState).
 * - handleEvent(...) processes a given DroneEvent, referencing the DroneSubsystem context and a Message if needed.
 */
public interface DroneState {

    /**
     * handleEvent:
     * @param subsystem The DroneSubsystem "context" object
     * @param event The DroneEvent that occurred
     * @param msg The Message data about the event/zone/severity, etc.
     * @throws InterruptedException if sleeps are interrupted during travel or dropping foam
     */
    void handleEvent(DroneSubsystem subsystem, DroneEvent event, Message msg) throws InterruptedException;
}