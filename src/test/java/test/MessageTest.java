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
        //msg  = new Message("ACTIVE_FIRE",1,"HIGH", localTime, timeString);
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
     * Testing getter for zoneID for Message
     *
     */
    @Test
    void testID(){
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
     * Testing toString for Message
     *
     */
    @Test
    void testMessageString(){
        String expectedString = "main.Message{time='14:50:45', type='ACTIVE_FIRE', zoneID=1, severity='HIGH'}";
        assertEquals(expectedString,msg.toString());
    }
}
