package main;

import java.io.Serializable;
import java.time.LocalTime;

/**
 * Message
 * 1. Holds data passed among subsystems (Drone, Scheduler, FireIncident).
 * 2. Stores the zone ID, severity, time, center coords.
 * 3. Contains `remainingFoamNeeded` for partial coverage logic.
 * 4. Has two constructors: one for FireIncidentSubsystem (no droneID),
 *    another for DroneSubsystem/Scheduler (with droneID).
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

    private double remainingFoamNeeded; // partial coverage leftover
    private final String eventID;

    // New fault fields:
    private final String faultType;
    private final double faultTime;

    // FireIncidentSubsystem constructor
    public Message(String type,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString,
                   int centerX,
                   int centerY,
                   double foamNeeded,
                   String eventID,
                   String faultType,
                   double faultTime) {
        this.type = type;
        this.droneID = -1;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
        this.centerX = centerX;
        this.centerY = centerY;
        this.remainingFoamNeeded = foamNeeded;
        this.eventID = eventID;
        this.faultType = faultType;
        this.faultTime = faultTime;
    }

    // Drone/Scheduler constructor
    public Message(String type,
                   int droneID,
                   int zoneID,
                   String severity,
                   LocalTime eventTime,
                   String eventTimeString,
                   int centerX,
                   int centerY,
                   double foamNeeded,
                   String eventID,
                   String faultType,
                   double faultTime) {
        this.type = type;
        this.droneID = droneID;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
        this.centerX = centerX;
        this.centerY = centerY;
        this.remainingFoamNeeded = foamNeeded;
        this.eventID = eventID;
        this.faultType = faultType;
        this.faultTime = faultTime;
    }

    public String getType() { return type; }
    public String getEventID() { return eventID; }
    public int getDroneID() { return droneID; }
    public int getZoneID() { return zoneID; }
    public String getSeverity() { return severity; }
    public LocalTime getEventTime() { return eventTime; }
    public String getEventTimeString() { return eventTimeString; }
    public int getCenterX() { return centerX; }
    public int getCenterY() { return centerY; }

    public double getRemainingFoamNeeded() { return remainingFoamNeeded; }
    public void setRemainingFoamNeeded(double v) { this.remainingFoamNeeded = v; }

    public String getFaultType() { return faultType; }
    public double getFaultTime() { return faultTime; }

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
        if (faultType != null && !faultType.isEmpty()) {
            sb.append(", faultType='").append(faultType).append("', faultTime=").append(faultTime);
        }
        sb.append("}");
        return sb.toString();
    }
}