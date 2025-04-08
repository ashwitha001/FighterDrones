package main;

public class DroneMetrics {
    private double totalMovingTime; // seconds
    private double totalIdleTime;   // seconds

    public synchronized void addMovingTime(double seconds) {
        totalMovingTime += seconds;
    }

    public synchronized void addIdleTime(double seconds) {
        totalIdleTime += seconds;
    }

    @Override
    public String toString() {
        return "Moving time = " + totalMovingTime + " sec, Idle time = " + totalIdleTime + " sec";
    }
}
