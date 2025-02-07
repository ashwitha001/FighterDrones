package src;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Represents a structured message used for communication between subsystems.
 * This class is serializable to allow for message passing between threads.
 * Ensures a future-proof design by encapsulating message fields for when transitioning to UDP.
 */
public class Message implements Serializable {
    private final String type;     // "FIRE_EVENT", "DRONE_STATUS", etc.
    private final int droneID;     // Drone ID (-1 if not applicable)
    private final int zoneID;      // Fire zone ID
    private final String severity; // "HIGH", "MODERATE", "LOW"
    private final java.time.LocalTime eventTime; // Parsed time
    private final String eventTimeString;      // Original string from CSV

    // Constructor for FireIncidentSubsystem messages (no droneID needed)
    public Message(String type, int zoneID, String severity, java.time.LocalTime eventTime, String eventTimeString) {
        this.type = type;
        this.droneID = -1; // No drone associated
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
    }

    // Constructor for Scheduler and DroneSubsystem messages (includes droneID)
    public Message(String type, int droneID, int zoneID, String severity, java.time.LocalTime eventTime, String eventTimeString) {
        this.type = type;
        this.droneID = droneID;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
    }

    public String getType() {
        return type;
    }
    public int getDroneID() {
        return droneID;
    }
    public int getZoneID() {
        return zoneID;
    }
    public String getSeverity() {
        return severity;
    }
    public java.time.LocalTime getEventTime() {
        return eventTime;
    }
    public String getEventTimeString() {
        return eventTimeString;
    }

    @Override
    public String toString() {
        return "Message{" +
                "time='" + eventTimeString + "', " +
                "type='" + type + "', " +
                (droneID != -1 ? "droneID=" + droneID + ", " : "") +
                "zoneID=" + zoneID + ", " +
                "severity='" + severity + "'}";
    }
}
