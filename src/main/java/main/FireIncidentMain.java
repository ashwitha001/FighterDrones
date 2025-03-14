package main;

import java.net.InetSocketAddress;

public class FireIncidentMain {
    public static void main(String[] args) {
        // Scheduler is assumed to run on localhost:5000.
        InetSocketAddress schedulerAddr = new InetSocketAddress("localhost", 5000);
        // FireIncidentSubsystem listens on port 5001 for completions.
        int localPort = 5001;
        Thread t = new Thread(new FireIncidentSubsystem(schedulerAddr, localPort), "FireIncidentSubsystem");
        t.start();
    }
}
