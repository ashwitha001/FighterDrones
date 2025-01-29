import java.io.*;
import java.util.concurrent.BlockingQueue;

public class FireIncidentSubsystem implements Runnable {
    private final BlockingQueue<String> schedulerQueue;

    public FireIncidentSubsystem(BlockingQueue<String> schedulerQueue) {
        this.schedulerQueue = schedulerQueue;
    }

    @Override
    public void run() {
        try (BufferedReader eventReader = new BufferedReader(new FileReader("events.csv"));
             BufferedReader zoneReader = new BufferedReader(new FileReader("zones.csv"))) {

            // Read and display zone data
            String zoneLine;
            while ((zoneLine = zoneReader.readLine()) != null) {
                System.out.println("[FireIncidentSubsystem] Zone Info: " + zoneLine);
            }

            // Read and send events to the scheduler
            String eventLine;
            while ((eventLine = eventReader.readLine()) != null) {
                System.out.println("[FireIncidentSubsystem] Sending event to Scheduler: " + eventLine);
                schedulerQueue.put(eventLine); // Send event to Scheduler
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}