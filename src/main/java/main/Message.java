package main;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Represents a structured message used for communication between subsystems.
 * This class is serializable to allow for message passing between threads.
 */
public class Message implements Serializable {
    private final String type;     // e.g. "ACTIVE_FIRE", "DRONE_EN_ROUTE", "FIRE_EXTINGUISHED"
    private final int droneID;     // Drone ID (-1 if not applicable)
    private final int zoneID;      // Fire zone ID
    private final String severity; // e.g. "HIGH", "MODERATE"
    private final LocalTime eventTime;
    private final String eventTimeString;

    // Store the center coords that the drone should travel to
    private final int centerX;
    private final int centerY;

    // Constructor for FireIncidentSubsystem => no droneID
    public Message(String type,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString,
                   int centerX,
                   int centerY) {
        this.type = type;
        this.droneID = -1;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
        this.centerX = centerX;
        this.centerY = centerY;
    }

    // Constructor for Scheduler/DroneSubsystem => includes droneID
    public Message(String type,
                   int droneID,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString,
                   int centerX,
                   int centerY) {
        this.type = type;
        this.droneID = droneID;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
        this.centerX = centerX;
        this.centerY = centerY;
    }

    // Another convenience constructor if you want no center for e.g. DRONE_IDLE
    public Message(String type,
                   int droneID,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString) {
        this(type, droneID, zoneID, severity, eventTime, eventTimeString, 0, 0);
    }

    public String getType() { return type; }
    public int getDroneID() { return droneID; }
    public int getZoneID() { return zoneID; }
    public String getSeverity() { return severity; }
    public LocalTime getEventTime() { return eventTime; }
    public String getEventTimeString() { return eventTimeString; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }

    @Override
    public String toString() {
        return "Message{" +
                "time='" + eventTimeString + "', type='" + type + "', " +
                (droneID != -1 ? "droneID=" + droneID + ", " : "") +
                "zoneID=" + zoneID + ", severity='" + severity + "', " +
                "center=(" + centerX + ", " + centerY + ")" +
                "}";
    }
}