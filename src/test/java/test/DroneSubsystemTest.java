package test;

import main.Coordinates;
import main.DroneSubsystem;
import main.EnRouteState;
import main.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;

import java.net.InetSocketAddress;
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
    InetSocketAddress schedulerAddress = new InetSocketAddress("localhost", 1);

    @BeforeEach
    public void setUp() {
        //dronesQueue = new LinkedBlockingQueue<>();
        //droneCompletionQueue = new LinkedBlockingQueue<>();
        droneSubsystem = new DroneSubsystem(1, schedulerAddress);

        //droneSubsystemThread = new Thread(droneSubsystem);
        //droneSubsystemThread.start();
    }

//    //check if a completion message is received
//    @Test
//    public void messageTest() throws InterruptedException {
//
//        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
//       // Message message = new Message("ACTIVE_FIRE",1,"HIGH", LocalTime.parse("12:30:00", timeFormatter), "12:30:00");
//
//      //  dronesQueue.put(message);
//        Message done = droneCompletionQueue.poll(5, TimeUnit.SECONDS);
//
//        assertNotNull(done);
//        assertEquals(1, done.getZoneID());
//        assertEquals("FIRE_EXTINGUISHED", done.getType());
//    }


//    public void murderTest() throws InterruptedException {
//        droneSubsystemThread.interrupt();
//        droneSubsystemThread.join();
//
//        assertFalse(droneSubsystemThread.isAlive());
//    }

    /**
     * Test Class to test DroneSubsystem.java thread
     */

    @Test
    public void test() {
        droneSubsystemThread = new Thread(droneSubsystem);
        droneSubsystemThread.start();

        try {
            Thread.sleep(10000); // 10 seconds
        } catch (InterruptedException e) {
            e.printStackTrace();
        }

        assertTrue(droneSubsystemThread.isAlive());
    }

    /**
     * Test Class to test getter for states
     */

    @Test
    public void testState() {
        droneSubsystem.setState("EN_ROUTE");
        assertEquals(droneSubsystem.getCurrentState(), droneSubsystem.getStateMap().get("EN_ROUTE"));
    }

    /**
     * Test Class to test getter for drone ID
     */

    @Test
    public void testDroneID() {
        assertEquals(droneSubsystem.getDroneID(), 1);
    }

    /**
     * Test Class to test getter for location
     *
     */
    @Test
    public void testLocation() {
        assertEquals(droneSubsystem.getCurrentLocation().toString(), "(0, 0)");

    }

    /**
     * Test Class to test setter for location
     */
    @Test
    public void testSetLocation() {
        droneSubsystem.setCurrentLocation(new Coordinates(1,1));
        assertEquals(droneSubsystem.getCurrentLocation().toString(), "(1, 1)");

    }

    /**
     * Test Class to test getter and setter for total flight time
     */
    @Test
    public void testFlightTime() {
        assertEquals(droneSubsystem.getTotalFlightTime(), 0.0);
        droneSubsystem.setTotalFlightTime(3.0);
        assertEquals(droneSubsystem.getTotalFlightTime(), 3.0);

    }
    /**
     * Test Class to test getter and setter for foam remaining
     */
    @Test
    public void testFoam() {
        assertEquals(droneSubsystem.getFoamRemaining(), 15.0);
        droneSubsystem.setFoamRemaining(5.0);
        assertEquals(droneSubsystem.getFoamRemaining(), 5.0);
    }

    /**
     * Test Class to test getter for battery seconds
     */
    @Test
    public void testBattery() {
        assertEquals(droneSubsystem.getMaxBatterySeconds(), 3600.0);
    }

}