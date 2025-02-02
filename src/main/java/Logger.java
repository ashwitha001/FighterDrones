package main.java;

public class Logger {
    private static final int LABEL_WIDTH = 30;

    // For example: log("[FireIncidentSubsystem]", "Hello World");
    public static void log(String subsystem, String message) {
        // %-30s will left-justify the subsystem label in a 30-char field.
        System.out.printf("%-" + LABEL_WIDTH + "s %s%n", subsystem, message);
    }
}
