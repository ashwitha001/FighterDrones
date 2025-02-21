package main;

import java.io.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.concurrent.BlockingQueue;

/**
 * Fire Incident Subsystem:
 * - Reads fire events from "events.csv".
 * - Sends structured messages to the Scheduler.
 * - Listens for fire extinguishment confirmations
 */
public class FireIncidentSubsystem implements Runnable {
    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;

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

        // 2) Load events from CSV
        List<Message> allEvents = loadAndParseEvents("events.csv");

        // 3) Dispatch in chronological order
        for (Message event : allEvents) {
            try {
                Logger.log("[FireIncidentSubsystem]", "Sent event: " + event);
                incidentQueue.put(event);
                checkForCompletions();
                Thread.sleep(500);
            } catch (InterruptedException e) {
                System.err.println("[FireIncidentSubsystem] Interrupted => exiting.");
                Thread.currentThread().interrupt();
                break;
            }
        }

        // Keep listening for completions
        while (!Thread.currentThread().isInterrupted()) {
            try {
                checkForCompletions();
                Thread.sleep(200);
            } catch (InterruptedException e) {
                System.err.println("[FireIncidentSubsystem] Interrupted => shutting down...");
                Thread.currentThread().interrupt();
                break;
            }
        }
        System.out.println("[FireIncidentSubsystem] Exiting...");
    }

    private void loadZoneData(String fileName) {
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                String[] parts = line.split(",");
                int zoneID = Integer.parseInt(parts[0].trim());
                int[] startC = parseCoords(parts[1]);
                int[] endC   = parseCoords(parts[2]);

                Coordinates coords = new Coordinates(
                        startC[0], startC[1],
                        endC[0],   endC[1]
                );
                zoneMap.put(zoneID, coords);
                System.out.println("[FireIncidentSubsystem] Loaded zone " + zoneID + ": " + coords);
            }
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Could not read " + fileName);
        }

        // zone 0 => base
        zoneMap.put(0, new Coordinates(0, 0, 0, 0));
        System.out.println("[FireIncidentSubsystem] Loaded zone 0: " + zoneMap.get(0) + " (base)");
    }

    private List<Message> loadAndParseEvents(String fileName) {
        List<Message> eventList = new ArrayList<>();
        DateTimeFormatter timeFmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        try (BufferedReader reader = new BufferedReader(new FileReader(fileName))) {
            reader.readLine(); // skip header
            String line;
            while ((line = reader.readLine()) != null) {
                Message m = buildMessage(line, timeFmt);
                eventList.add(m);
                System.out.println("[FireIncidentSubsystem] Received event: " + m);
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error reading " + fileName + ": " + e.getMessage());
        }
        return eventList;
    }

    private Message buildMessage(String line, DateTimeFormatter fmt) {
        String[] parts = line.split(",");
        String timeStr = parts[0].trim();
        int zoneID = Integer.parseInt(parts[1]);
        String eventType = parts[2].trim();      // e.g. "FIRE_DETECTED"
        String severity  = parts[3].trim();      // e.g. "LOW"

        LocalTime parsedTime = LocalTime.parse(timeStr, fmt);

        // find zone center
        Coordinates zC = zoneMap.getOrDefault(zoneID, new Coordinates(0,0,0,0));
        int centerX = (zC.getX1() + zC.getX2())/2;
        int centerY = (zC.getY1() + zC.getY2())/2;

        // figure out foam needed
        double needed = 0.0;
        switch (severity.toUpperCase()) {
            case "LOW":       needed = 10.0; break;
            case "MODERATE":  needed = 20.0; break;
            case "HIGH":      needed = 30.0; break;
            default:          needed = 10.0;
        }

        // Build message => "ACTIVE_FIRE"
        Message msg = new Message(
                "ACTIVE_FIRE",
                zoneID,
                severity.toUpperCase(),
                parsedTime,
                timeStr,
                centerX,
                centerY,
                needed
        );
        return msg;
    }

    private int[] parseCoords(String c) {
        c = c.trim().replace("(", "").replace(")", "");
        String[] xy = c.split(";");
        int x = Integer.parseInt(xy[0].trim());
        int y = Integer.parseInt(xy[1].trim());
        return new int[]{x,y};
    }

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