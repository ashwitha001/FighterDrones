package test;

import main.Message;
import main.Scheduler;
import main.SimulationUI;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertTrue;

import java.net.InetSocketAddress;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;


/**

 Test class to test Scheduler.java thread**/
public class SchedulerTest {
    Thread schedulerThread;


    /**Creating Scheduler Thread**/
    @BeforeEach
    public void setUp(){
        BlockingQueue<Message> incidentQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> dronesQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> droneCompletionQueue = new LinkedBlockingQueue<>();
        BlockingQueue<Message> incidentCompletionQueue = new LinkedBlockingQueue<>();


        InetSocketAddress schedulerAddress = new InetSocketAddress("localhost", 2);
        SimulationUI ui = new SimulationUI();
        schedulerThread = new Thread(new Scheduler(schedulerAddress, ui));
    }

    /**

     Testing Scheduler Thread**/@Test
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