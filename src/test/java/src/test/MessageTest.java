package src.test;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import src.Message;

import java.time.LocalTime;
import java.time.format.DateTimeFormatter;
import static org.junit.jupiter.api.Assertions.*;

public class MessageTest {
    Message msg;
    LocalTime localTime;
    String timeString;

    @BeforeEach
    void setUp(){
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        timeString = "14:50:45";
        localTime = LocalTime.parse(timeString, timeFormatter);
        msg  = new Message("ACTIVE_FIRE",1,"HIGH", localTime, timeString);
    }

    @Test
    void testType(){
        assertEquals("ACTIVE_FIRE",msg.getType());
    }

    @Test
    void testID(){
        assertEquals(1,msg.getZoneID());
    }

    @Test
    void testSeverity(){
        assertEquals("HIGH",msg.getSeverity());
    }

    @Test
    void testEventTime(){
        assertEquals(localTime,msg.getEventTime());
    }

    @Test
    void testTimeString(){
        assertEquals(timeString,msg.getEventTimeString());
    }

    @Test
    void testMessageString(){
        String expectedString = "Message{time='14:50:45', type='ACTIVE_FIRE', zoneID=1, severity='HIGH'}";
        assertEquals(expectedString,msg.toString());
    }
}
