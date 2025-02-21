package main;

/**
 * Utility class providing:
 * 1) Piecewise travel time calculation (0->70->0).
 * 2) A multi-line progress display, labeling each line with the given description.
 */
public class Utility {

    // Speed & acceleration constants
    private static final double MAX_SPEED_MS = 19.44;         // 70 km/h in m/s
    private static final double ACC_DEC_TIME = 3.0;           // 3s accelerate or decelerate
    private static final double ACCEL = MAX_SPEED_MS / ACC_DEC_TIME; // ~6.48 m/s^2
    private static final double FULL_ACCEL_DECEL_DIST = 58.32; // Distance needed for full 0->70->0

    /**
     * Computes travel time from (x1,y1) to (x2,y2) in meters
     * using the piecewise formula for 70 km/h w/ 3s accel & decel.
     */
    public static double computeTravelTime(int x1, int y1, int x2, int y2) {
        int dx = x2 - x1;
        int dy = y2 - y1;
        double dist = Math.sqrt(dx * dx + dy * dy);

        if (dist >= FULL_ACCEL_DECEL_DIST) {
            // 6s total for full accel+decel, plus cruise
            double cruiseDist = dist - FULL_ACCEL_DECEL_DIST;
            double cruiseTime = cruiseDist / MAX_SPEED_MS;
            return 6.0 + cruiseTime;
        } else {
            // never hits top speed => symmetrical accel/decel
            return 2.0 * Math.sqrt(dist / ACCEL);
        }
    }

    /**
     * Displays a multi-line progress bar for totalTime (seconds).
     * Each line includes the label, so user knows what this bar refers to.
     *
     * Example output:
     *   [#-------------------] 5%  DRONE-0 => from (0,0) to (350,300)
     *   [##------------------] 10% DRONE-0 => from (0,0) to (350,300)
     *   ...
     *   [####################] 100% DRONE-0 => from (0,0) to (350,300)
     *
     * @param totalTime total time in seconds
     * @param numSteps how many lines/updates to print
     * @param label e.g. "DRONE-0 => from (0,0) to (350,300)"
     */
    public static void showProgress(double totalTime, int numSteps, String label) throws InterruptedException {
        if (totalTime <= 0.0 || numSteps <= 0) {
            // If no travel needed or invalid steps, just print immediate done
            System.out.println("[####################] 100% " + label);
            return;
        }

        double stepDuration = totalTime / numSteps;

        for (int i = 0; i <= numSteps; i++) {
            double fraction = i / (double) numSteps; // 0..1
            String bar = buildProgressBar(fraction);

            // e.g.: "[###------] 15%  DRONE-0 => from (0,0) to (350,300)"
            int pct = (int)(fraction * 100);
            System.out.printf("%s %d%%  %s%n", bar, pct, label);

            if (i < numSteps) {
                Thread.sleep((long) (stepDuration * 500)); // REAL TIME WOULD BE 1000, but 500 for testing speed
            }
        }
    }

    /**
     * Builds a bar of width 20, e.g. "[#####-----]"
     *
     * @param fraction in [0..1]
     * @return bar string
     */
    private static String buildProgressBar(double fraction) {
        final int BAR_WIDTH = 20;

        int filled = (int) Math.round(BAR_WIDTH * fraction);
        int empty  = BAR_WIDTH - filled;

        StringBuilder sb = new StringBuilder();
        sb.append("[");
        for (int i = 0; i < filled; i++) sb.append("#");
        for (int i = 0; i < empty; i++) sb.append("-");
        sb.append("]");
        return sb.toString();
    }
}