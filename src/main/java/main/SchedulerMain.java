package main;

import java.net.InetSocketAddress;

public class SchedulerMain {
    public static void main(String[] args) {
        // Scheduler listens on localhost:5000.
        InetSocketAddress schedulerAddr = new InetSocketAddress("localhost", 5000);
        Logger.log("[SchedulerMain]", "Starting Scheduler at " + schedulerAddr);
        Thread schedulerThread = new Thread(new Scheduler(schedulerAddr), "Scheduler");
        schedulerThread.start();
    }
}
