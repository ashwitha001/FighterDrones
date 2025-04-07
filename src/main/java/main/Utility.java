package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Utility:
 * 1) Count lines in events.csv => totalFires
 * 2) computeTravelTime => piecewise formula
 * 3) showProgress => multi-line progress bar
 * 4) nozzleDropTime => partial coverage flow
 */
public class Utility {
    public static final double MAX_SPEED_MS = 130; // e.g. 70 km/h
    public static final double ACC_DEC_TIME = 3.0;
    public static final double ACCEL = MAX_SPEED_MS / ACC_DEC_TIME;
    public static final double FULL_ACCEL_DECEL_DIST = 58.32;

    public static final double FLOW_RATE_LPS = 9.0;
    public static final double NOZZLE_OPEN_TIME = 0.01;

    // For this design, the Scheduler does not read the number of events.
    public static int countEventLines(String eventsFile) {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
            br.readLine(); // skip header
            while (br.readLine() != null) count++;
        } catch (IOException e) {
            System.err.println("[Utility] Could not read " + eventsFile + ": " + e.getMessage());
        }
        return count;
    }

    public static double computeTravelTime(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        double dist = Math.sqrt(dx*dx + dy*dy);
        if (dist >= FULL_ACCEL_DECEL_DIST) {
            double cruiseDist = dist - FULL_ACCEL_DECEL_DIST;
            return 6.0 + (cruiseDist / MAX_SPEED_MS);
        } else {
            return 2.0 * Math.sqrt(dist / ACCEL);
        }
    }

    public static Coordinates computeCoorGivenTime(int x1, int y1, int x2, int y2, double t) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        double d; // distance

        double maxDistance = Math.sqrt(dx * dx + dy * dy);
        if (t <= ACC_DEC_TIME) {
            d = Math.min(maxDistance, 0.5*ACCEL*t*t); // distance while accelerating, capped at distance to endpoint
        } else {
            d = Math.min(maxDistance, 0.5*ACCEL*ACC_DEC_TIME*ACC_DEC_TIME + MAX_SPEED_MS*(t-ACC_DEC_TIME)); //distance while accelerating + distance at max speed, capped at distance to end point
        }

        double m = (double) dy/dx; // slope
        double k = d/(Math.sqrt(1+m*m));
        int x = (int) Math.round(x1 + (dx >= 0 ? k : -k));
        int y = (int) Math.round(y1 + (dy >= 0 ? k * m : -k * m));
        return new Coordinates(x, y);
    }

    public static void showProgress(double totalTime, String label, DroneSubsystem subsystem, int x1, int y1, int x2, int y2) throws InterruptedException {
        final int STEPS = (int) Math.floor(totalTime);
        double stepDur = 1000;

        for (int i = 0; i <= STEPS; i++) {
            double frac = i / (double) STEPS;
            int pct = (int)(frac * 100);
            String bar = buildBar(frac);

            if (subsystem.getTimeoutTriggered()) {
                System.out.println("Drone stopped since there is fault.");
                return;
            }

            Coordinates c = computeCoorGivenTime(x1, y1, x2, y2, i);
            System.out.printf("%s %d%%  %s,   Current Coordinate: (%d, %d)%n", bar, pct, label, c.getX1(), c.getY1());
            subsystem.setCurrentLocation(c);

            Message updateMsg = new Message(
                    "DRONE_COORD_UPDATE",
                    subsystem.getDroneID(),
                    0,
                    "", // no severity
                    java.time.LocalTime.now(),
                    java.time.LocalTime.now().toString(),
                    c.getX1(),
                    c.getY1(),
                    subsystem.getFoamRemaining(),
                    "DRONE_COORD_" + subsystem.getDroneID(), // unique event ID
                    "", 0.0
            );
            subsystem.sendToScheduler(updateMsg);

            // update UI
            if (subsystem.getSimulationUI() != null) {
                subsystem.getSimulationUI().updateDroneLocation(
                        subsystem.getDroneID(),
                        c.getX1(),
                        c.getY1(),
                        label.toUpperCase().contains("RETURN") ? "RETURNING" : "OUTBOUND"
                );
            }

            if (i < STEPS) {
                Thread.sleep((long)(stepDur));
            }
        }
    }


    private static String buildBar(double fraction) {
        final int WIDTH = 20;
        int fill = (int)Math.round(WIDTH * fraction);
        int empty = WIDTH - fill;
        StringBuilder sb = new StringBuilder("[");
        for (int i = 0; i < fill; i++) sb.append("#");
        for (int i = 0; i < empty; i++) sb.append("-");
        sb.append("]");
        return sb.toString();
    }

    public static double nozzleDropTime(double usage) {
        double flowTime = usage / FLOW_RATE_LPS;
        return NOZZLE_OPEN_TIME + flowTime;
    }
}
