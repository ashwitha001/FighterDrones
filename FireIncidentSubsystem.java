import java.io.*;
import java.util.concurrent.BlockingQueue;

public class FireIncidentSubsystem implements Runnable {
    private final BlockingQueue<String> incidentQueue;

    public FireIncidentSubsystem(BlockingQueue<String> incidentQueue) {
        this.incidentQueue = incidentQueue;
    }

    @Override
    public void run() {
        try (BufferedReader eventReader = new BufferedReader(new FileReader("events.csv"));
             BufferedReader zoneReader = new BufferedReader(new FileReader("zones.csv"))) {

            // Read and display zone data
            String zoneLine;
            while ((zoneLine = zoneReader.readLine()) != null) {
                System.out.println("Zone Info: " + zoneLine);
            }
            System.out.println("\nFire Incidents:");
            // Read and send events to the scheduler
            String eventLine;
            while ((eventLine = eventReader.readLine()) != null) {
                System.out.println("[FireIncidentSubsystem] Sending event to Scheduler: " + eventLine);
                incidentQueue.put(eventLine); // Send event to Scheduler
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}