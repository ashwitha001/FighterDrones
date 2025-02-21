package main;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Represents a structured message used for communication between subsystems.
 * This class is serializable to allow for message passing between threads.
 */
public class Message implements Serializable {
    private final String type;
    private final int droneID;
    private final int zoneID;
    private final String severity;
    private final LocalTime eventTime;
    private final String eventTimeString;

    private final int centerX;
    private final int centerY;

    private double remainingFoamNeeded;

    // FireIncidentSubsystem
    public Message(String type,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString,
                   int centerX,
                   int centerY,
                   double foamNeeded) {
        this.type = type;
        this.droneID = -1;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
        this.centerX = centerX;
        this.centerY = centerY;
        this.remainingFoamNeeded = foamNeeded;
    }

    // Scheduler/DroneSubsystem
    public Message(String type,
                   int droneID,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString,
                   int centerX,
                   int centerY,
                   double foamNeeded) {
        this.type = type;
        this.droneID = droneID;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
        this.centerX = centerX;
        this.centerY = centerY;
        this.remainingFoamNeeded = foamNeeded;
    }

    public String getType() { return type; }
    public int getDroneID() { return droneID; }
    public int getZoneID() { return zoneID; }
    public String getSeverity() { return severity; }
    public LocalTime getEventTime() { return eventTime; }
    public String getEventTimeString() { return eventTimeString; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }
    public double getRemainingFoamNeeded() { return remainingFoamNeeded; }
    public void setRemainingFoamNeeded(double val) { this.remainingFoamNeeded = val; }

    @Override
    public String toString() {
        StringBuilder sb = new StringBuilder("Message{");
        sb.append("time='").append(eventTimeString).append("', ");
        sb.append("type='").append(type).append("', ");
        if (droneID != -1) {
            sb.append("droneID=").append(droneID).append(", ");
        }
        sb.append("zoneID=").append(zoneID).append(", ");
        sb.append("severity='").append(severity).append("', ");
        sb.append("center=(").append(centerX).append(",").append(centerY).append("), ");
        sb.append("remainingFoamNeeded=").append(remainingFoamNeeded);
        sb.append("}");
        return sb.toString();
    }
}