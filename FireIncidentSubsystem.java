import java.io.*;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.TimeUnit;

public class FireIncidentSubsystem implements Runnable {
    private final BlockingQueue<String> incidentQueue;
    private final BlockingQueue<String> incidentCompletionQueue;


    public FireIncidentSubsystem(BlockingQueue<String> incidentQueue, BlockingQueue<String> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;

    }

    @Override
    public void run() {
        try (BufferedReader eventReader = new BufferedReader(new FileReader("events.csv"));
             BufferedReader zoneReader = new BufferedReader(new FileReader("zones.csv"))) {

            // Read and display zone data
            System.out.println("===== Zone Information =====");
            String zoneLine;
            while ((zoneLine = zoneReader.readLine()) != null) {
                System.out.println("Zone Info: " + zoneLine);
            }
            System.out.println("=============================\n");

            // Read and send events to the scheduler
            String eventLine;
            while ((eventLine = eventReader.readLine()) != null) {
                System.out.println("[FireIncidentSubsystem] Event Received: " + eventLine);
                System.out.println("[FireIncidentSubsystem] Sending event to Scheduler: " + eventLine);
                incidentQueue.put(eventLine);  // Send event to the Scheduler
            }

            //handle completion message
            while (true) {
                String completedEvent = incidentCompletionQueue.take();
                System.out.println("[FireIncidentSubsystem] Completed: " + completedEvent);
            }
        } catch (IOException | InterruptedException e) {
            e.printStackTrace();
        }
    }
}