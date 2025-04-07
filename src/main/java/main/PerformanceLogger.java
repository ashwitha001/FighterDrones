package main;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceLogger {
    private static final String LOG_FILE = "performance_metrics.txt";
    // Map eventID -> start time (ms)
    private static ConcurrentHashMap<String, Long> eventStartTimes = new ConcurrentHashMap<>();
    // Map eventID -> dispatch time (ms) if needed
    private static ConcurrentHashMap<String, Long> dispatchTimes = new ConcurrentHashMap<>();

    // Formatter for timestamps: hours:minutes:seconds.milliseconds
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    /**
     * Records the event start time using the current system time.
     * Logs the event's start time in a human-readable format.
     */
    public static void recordEventStart(String eventID) {
        long now = System.currentTimeMillis();
        eventStartTimes.put(eventID, now);
        logMetric("Event " + eventID + " started at " + sdf.format(new Date(now)));
    }

    /**
     * Records the dispatch time for an event, calculating the difference
     * from when the event started.
     */
    public static void recordDispatchTime(String eventID) {
        Long start = eventStartTimes.get(eventID);
        if (start != null) {
            long now = System.currentTimeMillis();
            long responseTimeMs = now - start;
            dispatchTimes.put(eventID, now);
            logMetric("Event " + eventID + " dispatch time: " + formatSeconds(responseTimeMs)
                    + " sec (dispatched at " + sdf.format(new Date(now)) + ")");
        }
    }

    /**
     * Records the event completion time (i.e. when the fire is extinguished).
     * It calculates the elapsed time from when the event started.
     */
    public static void recordEventCompletion(String eventID) {
        Long start = eventStartTimes.get(eventID);
        if (start != null) {
            long now = System.currentTimeMillis();
            long elapsedMs = now - start;
            logMetric("Event " + eventID + " extinguish time: " + formatSeconds(elapsedMs)
                    + " sec (completed at " + sdf.format(new Date(now)) + ")");
            eventStartTimes.remove(eventID);
            dispatchTimes.remove(eventID);
        }
    }

    /**
     * Converts a time difference in milliseconds to seconds formatted with two decimals.
     */
    private static String formatSeconds(long ms) {
        double seconds = ms / 1000.0;
        return String.format("%.2f", seconds);
    }

    /**
     * Synchronized method to append a metric entry to the log file.
     */
    private static synchronized void logMetric(String entry) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(entry);
        } catch (IOException e) {
            System.err.println("Error writing performance metrics: " + e.getMessage());
        }
    }
}