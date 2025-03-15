package test;

import main.FireIncidentSubsystem;
import main.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;

//Unused but may be used in the future
//import static org.mockito.Mockito.mock;
//import org.junit.runner.RunWith;
//import org.mockito.Mock;
//import org.mockito.junit.MockitoJUnitRunner;
//import org.powermock.modules.junit4.PowerMockRunner;

/**
 * Test Class to test FireIncidentSubsystem.java thread
 *
 */
public class FireIncidentSubsystemTest {
    Thread fireIncidentThread;

    /**
     * Creating FireIncidentSubsystem Thread
     *
     */
    @BeforeEach
    public void setUp(){
//        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
//        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();

      //  fireIncidentThread = new Thread(new FireIncidentSubsystem(incidentQueue, incidentCompletionQueue), "main.FireIncidentSubsystem");

        InetSocketAddress schedulerAddress = new InetSocketAddress("localhost", 1);
        fireIncidentThread = new Thread(new FireIncidentSubsystem(schedulerAddress,1));
    }

    /**
     * Testing FireIncidentSubsystem Thread
     *
     */
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
