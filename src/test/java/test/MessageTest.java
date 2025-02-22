package test;

import main.Message;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;


/**
 * Test Class to test methods in Message.java
 *
 */
public class MessageTest {
    Message msg;
    LocalTime localTime;
    String timeString;

    /**
     * Creating Message
     *
     */
    @BeforeEach
    void setUp(){
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        timeString = "14:50:45";
        localTime = LocalTime.parse(timeString, timeFormatter);
        msg  = new Message("ACTIVE_FIRE",1,1, "HIGH", localTime, timeString,3,4,10);
    }

    /**
     * Testing getter for type for Message
     *
     */
    @Test
    void testType(){
        assertEquals("ACTIVE_FIRE",msg.getType());
    }

    /**
     * Testing getter for droneID for Message
     *
     */
    @Test
    void testDroneID(){
        assertEquals(1,msg.getDroneID());
    }


    /**
     * Testing getter for zoneID for Message
     *
     */
    @Test
    void testZoneID(){
        assertEquals(1,msg.getZoneID());
    }

    /**
     * Testing getter for severity for Message
     *
     */
    @Test
    void testSeverity(){
        assertEquals("HIGH",msg.getSeverity());
    }

    /**
     * Testing getter for eventTime for Message
     *
     */
    @Test
    void testEventTime(){
        assertEquals(localTime,msg.getEventTime());
    }

    /**
     * Testing getter for testTimeString for Message
     *
     */
    @Test
    void testTimeString(){
        assertEquals(timeString,msg.getEventTimeString());
    }

    /**
     * Testing getter for center X for Message
     *
     */
    @Test
    void testCenterX(){
        assertEquals(3,msg.getCenterX());
    }

    /**
     * Testing getter for center Y for Message
     *
     */
    @Test
    void testCenterY(){
        assertEquals(4,msg.getCenterY());
    }

    /**
     * Testing getter for remaining foam for Message
     *
     */
    @Test
    void testFoam(){
        assertEquals(10,msg.getRemainingFoamNeeded());
    }

    /**
     * Testing toString for Message
     *
     */
    @Test
    void testMessageString(){
        String expectedString = "Message{time='14:50:45', type='ACTIVE_FIRE', droneID=1, zoneID=1, severity='HIGH', center=(3,4), remainingFoamNeeded=10.0}";
        assertEquals(expectedString,msg.toString());
    }
}
