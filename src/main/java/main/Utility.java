package main;

import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;

/**
 * Utility
 * 1. Count lines in `events.csv` to determine totalFires (minus header).
 * 2. Compute travel time using piecewise formula (0->70->0).
 * 3. Display multi-line progress bars in 10% increments.
 * 4. Compute nozzle drop time for partial coverage scenario.
 */
public class Utility {

    // Speed & acceleration
    public static final double MAX_SPEED_MS = 19.44;   // 70 km/h => 19.44 m/s
    public static final double ACC_DEC_TIME = 3.0;     // 3s to accelerate or decelerate
    public static final double ACCEL = MAX_SPEED_MS / ACC_DEC_TIME;
    public static final double FULL_ACCEL_DECEL_DIST = 58.32; // dist for 0->70->0

    // Foam flow
    public static final double FLOW_RATE_LPS = 9.0;    // 540 L/min
    public static final double NOZZLE_OPEN_TIME = 0.01;

    /**
     * Count how many lines are in `events.csv` (minus header),
     * thus giving the total number of distinct fire events.
     */
    public static int countEventLines(String eventsFile) {
        int count = 0;
        try (BufferedReader br = new BufferedReader(new FileReader(eventsFile))) {
            // skip header
            br.readLine();
            while (br.readLine() != null) {
                count++;
            }
        } catch (IOException e) {
            System.err.println("[Utility] Could not read " + eventsFile + ": " + e.getMessage());
        }
        return count;
    }

    /**
     * computeTravelTime: piecewise formula
     * if dist >= 58.32 => time= 6 + (dist-58.32)/19.44
     * else => time= 2 * sqrt(dist/6.48)
     */
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

    /**
     * showProgress: prints a multi-line progress bar in increments of 10%,
     * sleeping partial amounts so other threads can interleave logs.
     */
    public static void showProgress(double totalTime, String label) throws InterruptedException {
        final int STEPS = 10; // => 0..100% in 10% increments

        if (totalTime <= 0.0) {
            System.out.println("[####################] 100%  " + label);
            return;
        }
        double stepDur = totalTime / STEPS;
        for (int i = 0; i <= STEPS; i++) {
            double fraction = i / (double)STEPS;
            int pct = (int)(fraction * 100);
            String bar = buildBar(fraction);
            System.out.printf("%s %d%%  %s%n", bar, pct, label);

            if (i < STEPS) {
                Thread.sleep((long)(stepDur * 1000));
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

    /**
     * nozzleDropTime => usage / flowRate + overhead
     * e.g. usage=30 => time= 30/9 + 0.01= ~3.34s
     */
    public static double nozzleDropTime(double usage) {
        double flowTime = usage / FLOW_RATE_LPS;
        return NOZZLE_OPEN_TIME + flowTime;
    }
}