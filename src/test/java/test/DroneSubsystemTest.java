package test;

import main.DroneSubsystem;
import main.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.BlockingQueue;
import java.util.concurrent.LinkedBlockingQueue;
import java.util.concurrent.TimeUnit;

/**
 * Test Class to test DroneSubsystem.java thread
 */
public class DroneSubsystemTest {
    Thread droneSubsystemThread;
    BlockingQueue<Message> dronesQueue;
    BlockingQueue<Message> droneCompletionQueue;
    DroneSubsystem droneSubsystem;

    @BeforeEach
    public void setUp() {
        dronesQueue = new LinkedBlockingQueue<>();
        droneCompletionQueue = new LinkedBlockingQueue<>();
        droneSubsystem = new DroneSubsystem(1, dronesQueue, droneCompletionQueue);

        droneSubsystemThread = new Thread(droneSubsystem);
        droneSubsystemThread.start();
    }

    //check if a completion message is received
    @Test
    public void messageTest() throws InterruptedException {

        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        Message message = new Message("ACTIVE_FIRE", 1,1,"HIGH", LocalTime.parse("12:30:00", timeFormatter), "12:30:00",3,4,10);

        dronesQueue.put(message);
        Message done = droneCompletionQueue.poll(5, TimeUnit.SECONDS);

        assertNotNull(done);
        assertEquals(1, done.getZoneID());
        assertEquals("DRONE_EN_ROUTE", done.getType());
    }

    public void murderTest() throws InterruptedException {
        droneSubsystemThread.interrupt();
        droneSubsystemThread.join();

        assertFalse(droneSubsystemThread.isAlive());
    }
}