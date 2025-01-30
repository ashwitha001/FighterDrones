import java.io.Serializable;
/**
 * Represents a structured message used for communication between subsystems.
 * This class is serializable to allow for message passing between threads.
 * Ensures a future-proof design by encapsulating message fields for when transitioning to UDP.
 */
public class Message implements Serializable {
    private final String type; // "FIRE_EVENT", "DRONE_STATUS", etc.
    private final int zoneID; // Fire zone ID
    private final String severity; // "HIGH", "MODERATE", "LOW"

    public Message (String type, int zoneID, String severity) {
        this.type = type;
        this.zoneID = zoneID;
        this.severity = severity;
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

    @Override
    public String toString() {
        return "Message{type='" + type + "', zoneID=" + zoneID + ", severity='" + severity + "'}";
    }

}
