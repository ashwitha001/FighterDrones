import java.io.*;
import java.util.concurrent.BlockingQueue;

/**
 * Fire Incident Subsystem:
 * - Reads fire events from "events.csv".
 * - Sends structured messages to the Scheduler.
 * - Listens for fire extinguishment confirmations within the same loop.
 */
public class FireIncidentSubsystem implements Runnable {
    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;

    public FireIncidentSubsystem(BlockingQueue<Message> incidentQueue, BlockingQueue<Message> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
    }

    @Override
    public void run() {
        try (BufferedReader eventReader = new BufferedReader(new FileReader("events.csv"))) {
            String eventLine;
            while (true) {  // Keep running to both send events and receive confirmations
                // 1️⃣ **Check if there’s a new fire event to send**
                if ((eventLine = eventReader.readLine()) != null) {
                    eventLine = eventLine.trim();
                    String[] parts = eventLine.split(",");  // Ensure CSV format uses commas

                    if (parts.length < 4) {
                        System.err.println("[FireIncidentSubsystem] Invalid event format: " + eventLine);
                        continue;
                    }

                    try {
                        int zoneID = Integer.parseInt(parts[1]); // Extract Zone ID
                        String severity = parts[3].toUpperCase(); // Normalize severity

                        if (!severity.equals("HIGH") && !severity.equals("MODERATE") && !severity.equals("LOW")) {
                            System.err.println("[FireIncidentSubsystem] Invalid severity: " + severity);
                            continue;
                        }

                        // Send the event message to the Scheduler
                        Message message = new Message("FIRE_EVENT", zoneID, severity);
                        incidentQueue.put(message);
                        System.out.println("[FireIncidentSubsystem] Sent event: " + message);
                    } catch (NumberFormatException e) {
                        System.err.println("[FireIncidentSubsystem] Invalid Zone ID: " + parts[1]);
                    }
                }

                // 2️⃣ **Check for fire extinguishment confirmations**
                while (!incidentCompletionQueue.isEmpty()) {
                    Message completedEvent = incidentCompletionQueue.take();  // Get the next confirmation message
                    System.out.println("[FireIncidentSubsystem] Fire extinguished confirmation received: " + completedEvent);
                }

                // 3️⃣ **Pause briefly to prevent CPU overuse**
                Thread.sleep(500);  // Allows time for new messages to arrive
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}
