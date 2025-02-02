package main;

import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Fire Incident Subsystem:
 * - Reads fire events from "events.csv".
 * - Sends structured messages to the main.Scheduler.
 * - Listens for fire extinguishment confirmations
 */
public class FireIncidentSubsystem implements Runnable {
    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;

    // Store zone data for future use
    private final Map<Integer, Coordinates> zoneMap = new HashMap<>();

    public FireIncidentSubsystem(BlockingQueue<Message> incidentQueue,
                                 BlockingQueue<Message> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
    }

    @Override
    public void run() {
        // Load zone information
        loadZoneData("zones.csv");

        // Load events from CSV
        List<Message> allEvents = loadAndParseEvents("events.csv");

        // Dispatch events in chronological order
        for (Message event : allEvents) {
            try {
                // Send the event to the main.Scheduler
                //System.out.println("[main.FireIncidentSubsystem] Sent event: " + event);
                Logger.log("[main.FireIncidentSubsystem]", "Sent event: " + event);
                incidentQueue.put(event);

                // Check for any completed fires in the queue before sending next event
                checkForCompletions();

                // Short delay between sending events
                Thread.sleep(500);

            } catch (InterruptedException e) {
                System.err.println("[main.FireIncidentSubsystem] Interrupted while sending events.");
                Thread.currentThread().interrupt();
                break;
            }
        }

        // After dispatching all known events, keep listening for completions
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkForCompletions();
                Thread.sleep(200); // idle a bit
            } catch (InterruptedException e) {
                System.err.println("[main.FireIncidentSubsystem] Interrupted while waiting for completions.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[main.FireIncidentSubsystem] Exiting...");
    }

    /**
     * Reads and stores zone coordinates from zones.csv
     * Expected format (for subsequent lines):
     *   ZoneID, (x1;y1), (x2;y2)
     */
    private void loadZoneData(String zonesFile) {
        try (BufferedReader reader = new BufferedReader(new FileReader(zonesFile))) {
            // Skip the first line (header)
            reader.readLine();

            String line;
            while ((line = reader.readLine()) != null) {

                String[] parts = line.split(",");

                int zoneID = Integer.parseInt(parts[0].trim());
                int[] startCoords = parseCoords(parts[1]);
                int[] endCoords   = parseCoords(parts[2]);

                Coordinates coords = new Coordinates(
                        startCoords[0], startCoords[1],
                        endCoords[0],   endCoords[1]
                );

                zoneMap.put(zoneID, coords);
                System.out.println("[main.FireIncidentSubsystem] Loaded zone " + zoneID + ": " + coords);
            }
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("[main.FireIncidentSubsystem] Could not read zones file: " + zonesFile);
        }
    }

    /**
     * Reads all events from events.csv into a List<main.Message>.
     */
    private List<Message> loadAndParseEvents(String eventsFile) {
        List<Message> eventList = new ArrayList<>();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");

        try (BufferedReader eventReader = new BufferedReader(new FileReader(eventsFile))) {
            // Skip the first line (header)
            eventReader.readLine();

            String eventLine;
            while ((eventLine = eventReader.readLine()) != null) {
                Message message = getMessage(eventLine, timeFormatter);

                eventList.add(message);
                System.out.println("[main.FireIncidentSubsystem] Received event: " + message);
            }
        } catch (IOException e) {
            System.err.println("[main.FireIncidentSubsystem] Error reading " + eventsFile + ": " + e.getMessage());
        }
        return eventList;
    }

    private static Message getMessage(String eventLine, DateTimeFormatter timeFormatter) {
        String[] parts = eventLine.split(",");

        String timeString = parts[0].trim();     // e.g. "14:03:15"
        int zoneID = Integer.parseInt(parts[1]); // e.g. "3"
        String eventType = parts[2].trim();      // e.g. "FIRE_DETECTED"
        String severity  = parts[3].trim();      // e.g. "High"

        LocalTime parsedTime = LocalTime.parse(timeString, timeFormatter);

        Message message = new Message(
                "ACTIVE_FIRE",
                zoneID,
                severity.toUpperCase(),
                parsedTime,
                timeString
        );
        return message;
    }

    /**
     * Helper to parse a "(x;y)" string into an int array [x, y].
     */
    private int[] parseCoords(String coords) {
        coords = coords.trim();
        coords = coords.replace("(", "").replace(")", "");
        String[] xy = coords.split(";");
        int x = Integer.parseInt(xy[0].trim());
        int y = Integer.parseInt(xy[1].trim());
        return new int[]{x,y};
    }

    /**
     * Checks for any completion messages in incidentCompletionQueue and prints them to the screen.
     */
    private void checkForCompletions() throws InterruptedException {
        while (!incidentCompletionQueue.isEmpty()) {
            Message completedEvent = incidentCompletionQueue.take();
            //System.out.println("[main.FireIncidentSubsystem] Completion confirmed: " + completedEvent);
            Logger.log("[main.FireIncidentSubsystem]", "Completion confirmed of: " + completedEvent);
        }
    }
}
