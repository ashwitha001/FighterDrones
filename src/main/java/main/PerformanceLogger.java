package main;

import java.io.FileWriter;
import java.io.IOException;
import java.io.PrintWriter;
import java.text.SimpleDateFormat;
import java.util.Date;
import java.util.concurrent.ConcurrentHashMap;

public class PerformanceLogger {
    private static final String LOG_FILE = "performance_metrics.txt";
    private static final long programStartTime = System.currentTimeMillis();

    // Event metrics: map eventID -> start time (ms) and dispatch time
    private static ConcurrentHashMap<String, Long> eventStartTimes = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<String, Long> dispatchTimes = new ConcurrentHashMap<>();

    // Drone state metrics: track the last time a drone changed state.
    // When a drone goes from idle to moving, record the idle period.
    private static ConcurrentHashMap<Integer, Long> droneIdleStart = new ConcurrentHashMap<>();
    // When a drone starts moving (EN_ROUTE or DROPPING) record its start time.
    private static ConcurrentHashMap<Integer, Long> droneMoveStart = new ConcurrentHashMap<>();
    // Sums (in ms) of idle and moving times.
    private static ConcurrentHashMap<Integer, Long> totalIdleTime = new ConcurrentHashMap<>();
    private static ConcurrentHashMap<Integer, Long> totalMoveTime = new ConcurrentHashMap<>();

    // Formatter for human-readable timestamps (with milliseconds)
    private static final SimpleDateFormat sdf = new SimpleDateFormat("HH:mm:ss.SSS");

    // ----- Event-level Metrics -----
    public static void recordEventStart(String eventID) {
        long now = System.currentTimeMillis();
        eventStartTimes.put(eventID, now);
        logMetric("Event " + eventID + " started at " + sdf.format(new Date(now)));
    }

    public static void logProgramDuration() {
        long durationMs = System.currentTimeMillis() - programStartTime;
        logMetric("Total program duration: " + formatSeconds(durationMs) + " sec");
    }
    public static void recordDispatchTime(String eventID, int droneID) {
        Long start = eventStartTimes.get(eventID);
        if (start != null) {
            long now = System.currentTimeMillis();
            long responseTimeMs = now - start;
            dispatchTimes.put(eventID, now);
            logMetric("Event " + eventID + " dispatched by Drone " + droneID + " at " + sdf.format(new Date(now)) +
                    " with dispatch time: " + formatSeconds(responseTimeMs) + " sec");
        }
    }

    public static void recordEventCompletion(String eventID) {
        Long start = eventStartTimes.get(eventID);
        if (start != null) {
            long now = System.currentTimeMillis();
            long elapsedMs = now - start;
            logMetric("Event " + eventID + " extinguish time: " + formatSeconds(elapsedMs) +
                    " sec (completed at " + sdf.format(new Date(now)) + ")");
            eventStartTimes.remove(eventID);
            dispatchTimes.remove(eventID);
        }
    }

    // ----- Drone-level State Metrics -----
    /**
     * Record a drone state transition. For example, if a drone transitions
     * from IDLE to EN_ROUTE, record the idle time that ended and the moving start.
     */
    public static void recordDroneStateTransition(int droneId, String oldState, String newState) {
        long now = System.currentTimeMillis();
        // Transition: IDLE -> (EN_ROUTE or DROPPING) means the drone is starting to move.
        if ("IDLE".equals(oldState) && ("EN_ROUTE".equals(newState) || "DROPPING".equals(newState))) {
            Long idleStart = droneIdleStart.remove(droneId);
            if (idleStart != null) {
                long idleDuration = now - idleStart;
                totalIdleTime.merge(droneId, idleDuration, Long::sum);
                logMetric("Drone " + droneId + " idle period ended: " + formatSeconds(idleDuration) + " sec");
            }
            droneMoveStart.put(droneId, now);
            logMetric("Drone " + droneId + " started moving at " + sdf.format(new Date(now)));
        }
        // Transition: (EN_ROUTE or DROPPING) -> IDLE means the drone finished moving.
        else if (("EN_ROUTE".equals(oldState) || "DROPPING".equals(oldState)) && "IDLE".equals(newState)) {
            Long moveStart = droneMoveStart.remove(droneId);
            if (moveStart != null) {
                long moveDuration = now - moveStart;
                totalMoveTime.merge(droneId, moveDuration, Long::sum);
                logMetric("Drone " + droneId + " moving period ended: " + formatSeconds(moveDuration) + " sec");
            }
            droneIdleStart.put(droneId, now);
            logMetric("Drone " + droneId + " started idling at " + sdf.format(new Date(now)));
        }
        // Additional transitions (e.g., moving to fault) can be added here as needed.
    }
    public static void initializeDroneIdleStart(int droneId) {
        long now = System.currentTimeMillis();
        droneIdleStart.put(droneId, now);
        logMetric("Drone " + droneId + " initialized in IDLE state at " + sdf.format(new Date(now)));
    }
    /**
     * At the end of the simulation (or periodically), report overall metrics per drone.
     */
    public static void reportFinalDroneMetrics(int droneId) {

        long now = System.currentTimeMillis();
        long totalDurationMs = now - programStartTime;

        // Get already accumulated times.
        long idle = totalIdleTime.getOrDefault(droneId, 0L);
        if (droneIdleStart.containsKey(droneId)) {
            idle += now - droneIdleStart.get(droneId);
        }
        long move = totalDurationMs - idle;
        double utilization = (double) move / totalDurationMs;

        // If the drone is currently idle, add the idle time since the last idle transition.
        if (droneIdleStart.containsKey(droneId)) {
            idle += now - droneIdleStart.get(droneId);
        }
        logMetric("Drone " + droneId + " total idle time: " + formatSeconds(idle) + " sec");
        logMetric("Drone " + droneId + " total moving time: " + formatSeconds(move) + " sec");
        logMetric("Drone " + droneId + " utilization: " + String.format("%.2f%%", utilization * 100));
    }

    private static String formatSeconds(long ms) {
        double seconds = ms / 1000.0;
        return String.format("%.2f", seconds);
    }

    private static synchronized void logMetric(String entry) {
        try (FileWriter fw = new FileWriter(LOG_FILE, true);
             PrintWriter pw = new PrintWriter(fw)) {
            pw.println(entry);
        } catch (IOException e) {
            System.err.println("Error writing performance metrics: " + e.getMessage());
        }
    }
}
