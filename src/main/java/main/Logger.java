package main;

/**
 * Logger
 * 1. Provides a simple method to log messages from each subsystem with a label.
 * 2. Uses fixed-width formatting so logs are somewhat aligned.
 */
public class Logger {
    private static final int LABEL_WIDTH = 30;

    public static void log(String subsystem, String message) {
        System.out.printf("%-" + LABEL_WIDTH + "s %s%n", subsystem, message);
    }
}