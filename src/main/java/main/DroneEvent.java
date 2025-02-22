package main;

/**
 * DroneEvent:
 * Enumerates the events that can trigger transitions in the drone's state machine.
 */
public enum DroneEvent {
    DISPATCH_RECEIVED,  // When the drone receives a dispatch from the Scheduler
    ARRIVE_ZONE,        // Drone arrives at the fire zone
    START_DROPPING,     // Drone begins dropping foam
    PARTIAL_COVERAGE,   // Drone did partial coverage
    FIRE_EXTINGUISHED,  // Fire fully extinguished
    RETURN_TO_BASE,     // Drone sets course back to (0,0)
    ARRIVE_BASE         // Drone arrives at (0,0)
}