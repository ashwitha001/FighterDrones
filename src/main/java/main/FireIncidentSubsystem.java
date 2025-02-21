package main;

import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * FireIncidentSubsystem
 * 1. Reads zone data from `zones.csv` (defining rectangular corners or base).
 * 2. Reads fire events from `events.csv` (time, zone, severity) and calculates center coordinates + foam needed.
 * 3. Creates Message objects for each event and sends them to the Scheduler via `incidentQueue`.
 * 4. Logs and checks for FIRE_EXTINGUISHED completions via `incidentCompletionQueue`.
 * 5. Continues listening for completions until interrupted or program ends.
 */
public class FireIncidentSubsystem implements Runnable {

    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;

    // Maps zoneID -> Coordinates (rectangular corners or base)
    private final Map<Integer, Coordinates> zoneMap = new HashMap<>();

    public FireIncidentSubsystem(BlockingQueue<Message> incidentQueue,
                                 BlockingQueue<Message> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
    }

    @Override
    public void run() {
        // 1) Load zone data
        loadZoneData("zones.csv");

        // 2) Load events
        List<Message> allEvents = loadAndParseEvents("events.csv");

        // 3) Dispatch events in chronological order
        for (Message event : allEvents) {
            try {
                Logger.log("[FireIncidentSubsystem]", "Sent event: " + event);
                incidentQueue.put(event);

                // Check if any completions arrived
                checkForCompletions();
                Thread.sleep(500);

            } catch (InterruptedException e) {
                System.err.println("[FireIncidentSubsystem] Interrupted => shutting down...");
                Thread.currentThread().interrupt();
                break;
            }
        }

        // 4) After dispatch, keep listening for completions
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkForCompletions();
                Thread.sleep(200);
            } catch (InterruptedException e) {
                System.err.println("[FireIncidentSubsystem] Interrupted => exit.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[FireIncidentSubsystem] Exiting...");
    }

    /**
     * Loads zone data from `zones.csv` (skipping header).
     * zoneMap is updated with each zone's corners, plus zone 0 => base (0,0).
     */
    private void loadZoneData(String fileName) {
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            // skip header
            br.readLine();
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int zoneID = Integer.parseInt(parts[0].trim());
                int[] startC = parseCoords(parts[1]);
                int[] endC   = parseCoords(parts[2]);

                Coordinates coords = new Coordinates(startC[0], startC[1], endC[0], endC[1]);
                zoneMap.put(zoneID, coords);

                System.out.println("[FireIncidentSubsystem] Loaded zone " + zoneID + ": " + coords);
            }
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Could not read " + fileName);
        }

        // Reserve zone 0 => base
        zoneMap.put(0, new Coordinates(0,0,0,0));
        System.out.println("[FireIncidentSubsystem] Loaded zone 0: " + zoneMap.get(0) + " (base)");
    }

    /**
     * Loads and parses events from `events.csv` (skipping header).
     * Each line => (time, zoneID, eventType, severity).
     * Builds a Message with 'remainingFoamNeeded' mapped from severity (10,20,30).
     */
    private List<Message> loadAndParseEvents(String fileName) {
        List<Message> events = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        try (BufferedReader br = new BufferedReader(new FileReader(fileName))) {
            // skip header
            br.readLine();

            String line;
            while ((line = br.readLine()) != null) {
                Message m = buildMessage(line, fmt);
                events.add(m);
                System.out.println("[FireIncidentSubsystem] Received event: " + m);
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error reading " + fileName + ": " + e.getMessage());
        }
        return events;
    }

    /**
     * Builds a single Message for an event line, calculating center coords from zoneMap
     * and deciding foam usage based on severity.
     */
    private Message buildMessage(String line, DateTimeFormatter fmt) {
        String[] parts = line.split(",");
        String timeStr = parts[0].trim();
        int zoneID     = Integer.parseInt(parts[1].trim());
        String evType  = parts[2].trim();
        String severity= parts[3].trim();

        LocalTime parsedTime = LocalTime.parse(timeStr, fmt);
        Coordinates zC = zoneMap.getOrDefault(zoneID, new Coordinates(0,0,0,0));
        int cx = (zC.getX1() + zC.getX2())/2;
        int cy = (zC.getY1() + zC.getY2())/2;

        double needed;
        switch (severity.toUpperCase()) {
            case "LOW":       needed = 10.0; break;
            case "MODERATE":  needed = 20.0; break;
            case "HIGH":      needed = 30.0; break;
            default:          needed = 10.0;
        }

        return new Message(
                "ACTIVE_FIRE",
                zoneID,
                severity.toUpperCase(),
                parsedTime,
                timeStr,
                cx,
                cy,
                needed
        );
    }

    private int[] parseCoords(String c) {
        c = c.trim().replace("(", "").replace(")", "");
        String[] xy = c.split(";");
        int x = Integer.parseInt(xy[0].trim());
        int y = Integer.parseInt(xy[1].trim());
        return new int[]{x,y};
    }

    /**
     * Checks the incidentCompletionQueue for any FIRE_EXTINGUISHED messages,
     * logs them as confirmation of completion.
     */
    private void checkForCompletions() throws InterruptedException {
        while (!incidentCompletionQueue.isEmpty()) {
            Message done = incidentCompletionQueue.take();
            if ("FIRE_EXTINGUISHED".equals(done.getType())) {
                Logger.log("[FireIncidentSubsystem]",
                        "Completion confirmed: " + done);
            }
        }
    }
}