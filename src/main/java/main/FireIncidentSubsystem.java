package main;

import java.io.*;
import java.net.*;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.*;

/**
 * FireIncidentSubsystem
 * 1. Reads zone data from `zones.csv` (defining rectangular corners or base).
 * 2. Reads fire events from `events.csv` (time, zone, severity) and calculates center coordinates + foam needed.
 * 3. Creates Message objects for each event and sends them to the Scheduler via `incidentQueue`.
 * 4. Logs and checks for FIRE_EXTINGUISHED completions via `incidentCompletionQueue`.
 * 5. Continues listening for completions until interrupted or program ends.
 */
public class FireIncidentSubsystem implements Runnable {
    private final InetSocketAddress schedulerAddress;
    private final DatagramSocket socket; // to receive confirmations
    private final Map<Integer, Coordinates> zoneMap = new HashMap<>();
    private final Map<String, Message> activeFires = new HashMap<>(); // Track active fires by eventID

    public FireIncidentSubsystem(InetSocketAddress schedulerAddress, int localPort) {
        this.schedulerAddress = schedulerAddress;
        try {
            this.socket = new DatagramSocket(localPort);
            Logger.log("[FireIncidentSubsystem]", "Bound to port " + localPort);
        } catch (SocketException e) {
            throw new RuntimeException("Could not bind FireIncidentSubsystem on port " + localPort, e);
        }
    }

    @Override
    public void run() {
        loadZoneData("zones.csv");
        List<Message> allEvents = loadAndParseEvents("events.csv");

        Thread receiverThread = new Thread(new UDPReceiver(socket, m -> {
            if ("FIRE_EXTINGUISHED".equals(m.getType())) {
                Logger.log("[FireIncidentSubsystem]", "Completion confirmed: " + m);
                // Remove the extinguished fire from active fires
                activeFires.remove(m.getEventID());
                
                Message confirm = new Message(
                        "INCIDENT_CONFIRMED",
                        m.getDroneID(),
                        m.getZoneID(),
                        m.getSeverity(),
                        m.getEventTime(),
                        m.getEventTimeString(),
                        m.getCenterX(),
                        m.getCenterY(),
                        0.0,
                        m.getEventID(),
                        "", 0.0
                );
                try {
                    UDPUtil.sendMessage(confirm, schedulerAddress);
                    Logger.log("[FireIncidentSubsystem]", "Sent incident confirmation: " + confirm);
                } catch (IOException e) {
                    Logger.log("[FireIncidentSubsystem]", "Error sending confirmation: " + e.getMessage());
                }
            }
        }), "FireIncidentReceiver");
        receiverThread.start();

        // Sort events by time
        allEvents.sort(Comparator.comparing(Message::getEventTime));

        // Get the start time of the first event
        LocalTime startTime = allEvents.get(0).getEventTime();
        LocalTime currentTime = startTime;

        for (Message e : allEvents) {
            try {
                // Calculate the delay until the next event
                long delayMillis = (long)(Utility.convertToSimulationTime(
                    java.time.Duration.between(currentTime, e.getEventTime()).getSeconds()
                ) * 1000);

                if (delayMillis > 0) {
                    Thread.sleep(delayMillis);
                }

                // Only send the event if the fire is still active (not extinguished)
                if (!activeFires.containsKey(e.getEventID())) {
                    Logger.log("[FireIncidentSubsystem]", "Sent event: " + e);
                    UDPUtil.sendMessage(e, schedulerAddress);
                    // Add to active fires map
                    activeFires.put(e.getEventID(), e);
                } else {
                    Logger.log("[FireIncidentSubsystem]", "Skipping event - fire already extinguished: " + e);
                }
                
                currentTime = e.getEventTime();
            } catch (InterruptedException | IOException ex) {
                Logger.log("[FireIncidentSubsystem]", "Interrupted or error => shutting down.");
                Thread.currentThread().interrupt();
                break;
            }
        }

        while (!Thread.currentThread().isInterrupted()) {
            try {
                Thread.sleep(200);
            } catch (InterruptedException e) {
                Logger.log("[FireIncidentSubsystem]", "Interrupted => exit.");
                Thread.currentThread().interrupt();
                break;
            }
        }
        Logger.log("[FireIncidentSubsystem]", "Exiting...");
    }

    private void loadZoneData(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int zoneID = Integer.parseInt(parts[0].trim());
                int[] startC = parseCoords(parts[1]);
                int[] endC = parseCoords(parts[2]);
                Coordinates coords = new Coordinates(startC[0], startC[1], endC[0], endC[1]);
                zoneMap.put(zoneID, coords);
                System.out.println("[FireIncidentSubsystem] Loaded zone " + zoneID + ": " + coords);
            }
            System.out.println("====================================================");
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Could not read " + filename);
        }
        zoneMap.put(0, new Coordinates(0,0,0,0));
        System.out.println("[FireIncidentSubsystem] Loaded zone 0: " + zoneMap.get(0) + " (base)");
    }

    private List<Message> loadAndParseEvents(String filename) {
        List<Message> events = new ArrayList<>();
        DateTimeFormatter fmt = DateTimeFormatter.ofPattern("HH:mm:ss");
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.readLine(); // skip header
            String line;
            while ((line = br.readLine()) != null) {
                Message m = buildMessage(line, fmt);
                events.add(m);
                System.out.println("[FireIncidentSubsystem] Received event: " + m);
            }
        } catch (IOException e) {
            System.err.println("[FireIncidentSubsystem] Error reading " + filename + ": " + e.getMessage());
        }
        return events;
    }

    private Message buildMessage(String line, DateTimeFormatter fmt) {
        String[] parts = line.split(",");
        String timeStr = parts[0].trim();
        int zoneID = Integer.parseInt(parts[1].trim());
        String severity = parts[3].trim().toUpperCase();
        LocalTime parsedTime = LocalTime.parse(timeStr, fmt);
        Coordinates zC = zoneMap.getOrDefault(zoneID, new Coordinates(0,0,0,0));
        int cx = (zC.getX1() + zC.getX2()) / 2;
        int cy = (zC.getY1() + zC.getY2()) / 2;
        double needed;
        switch (severity) {
            case "LOW":       needed = 10.0; break;
            case "MODERATE":  needed = 20.0; break;
            case "HIGH":      needed = 30.0; break;
            default:          needed = 10.0;
        }
        String eventID = timeStr + "_Z" + zoneID;
        // New fault columns: FAULT and FAULT_TIME (if provided)
        String faultType = "";
        double faultTime = 0.0;
        if (parts.length >= 6) {
            faultType = parts[4].trim();
            try {
                faultTime = Double.parseDouble(parts[5].trim());
            } catch (NumberFormatException ex) {
                faultTime = 0.0;
            }
        }
        return new Message(
                "ACTIVE_FIRE",
                zoneID,
                severity,
                parsedTime,
                timeStr,
                cx,
                cy,
                needed,
                eventID,
                faultType,
                faultTime
        );
    }

    private int[] parseCoords(String c) {
        c = c.trim().replace("(", "").replace(")", "");
        String[] xy = c.split(";");
        int x = Integer.parseInt(xy[0].trim());
        int y = Integer.parseInt(xy[1].trim());
        return new int[]{x, y};
    }
}