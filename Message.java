import java.io.Serializable;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

/**
 * Represents a structured message used for communication between subsystems.
 * This class is serializable to allow for message passing between threads.
 * Ensures a future-proof design by encapsulating message fields for when transitioning to UDP.
 */
public class Message implements Serializable {
    private final String type;     // "FIRE_EVENT", "DRONE_STATUS", etc.
    private final int zoneID;      // Fire zone ID
    private final String severity; // "HIGH", "MODERATE", "LOW"
    private final LocalTime eventTime;         // Parsed time
    private final String eventTimeString;      // Original string from CSV

    public Message(String type, int zoneID, String severity,
                   LocalTime eventTime, String eventTimeString) {
        this.type = type;
        this.zoneID = zoneID;
        this.severity = severity;
        this.eventTime = eventTime;
        this.eventTimeString = eventTimeString;
    }

    public String getType() {
        return type;
    }
    public int getZoneID() {
        return zoneID;
    }
    public String getSeverity() {
        return severity;
    }
    public LocalTime getEventTime() {
        return eventTime;
    }
    public String getEventTimeString() {
        return eventTimeString;
    }

    @Override
    public String toString() {
        // Include the eventTimeString in the output
        return "Message{" +
                "time='" + eventTimeString + "', " +
                "type='" + type + "', " +
                "zoneID=" + zoneID + ", " +
                "severity='" + severity + "'}";
    }
}

