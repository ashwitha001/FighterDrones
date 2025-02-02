import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import main.java.Message;
import main.java.Scheduler;

import static org.junit.jupiter.api.Assertions.assertTrue;

public class SchedulerTest {
    Thread schedulerThread;

    @BeforeEach
    public void setUp(){
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> dronesQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> droneCompletionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

        schedulerThread = new Thread(new Scheduler(incidentQueue, dronesQueue, droneCompletionQueue, incidentCompletionQueue), "Scheduler");

    }
    @Test
    public void test(){
        schedulerThread.start();

        try {
            Thread.sleep(10000); // 10 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertTrue(schedulerThread.isAlive());
    }

}
