import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import main.java.FireIncidentSubsystem;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import main.java.Message;
import static org.junit.jupiter.api.Assertions.assertTrue;

//Unused but may be used in the future
//import static org.mockito.Mockito.mock;
//import org.junit.runner.RunWith;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnitRunner;
//import org.powermock.modules.junit4.PowerMockRunner;

public class FireIncidentSubsystemTest {
    Thread fireIncidentThread;

    @BeforeEach
    public void setUp(){
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

        fireIncidentThread = new Thread(new FireIncidentSubsystem(incidentQueue, incidentCompletionQueue), "FireIncidentSubsystem");

    }
    @Test
    public void test(){
        fireIncidentThread.start();

        try {
            Thread.sleep(10000); // 10 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertTrue(fireIncidentThread.isAlive());
    }

}
