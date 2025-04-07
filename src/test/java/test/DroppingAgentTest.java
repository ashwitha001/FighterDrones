package test;

import main.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;
import java.time.LocalTime;
import java.time.format.DateTimeFormatter;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class DroppingAgentTest {

    private DroneSubsystem droneSubsystem;
    private DroppingAgentState droppingAgentState;
    Message msg1, msg2;
    LocalTime localTime;
    String timeString;

    @BeforeEach
    public void setUp() {
        //initialize the DroneSubsystem with a mock scheduler address.
        droneSubsystem = new DroneSubsystem(1, new InetSocketAddress("localhost", 9999));
        droppingAgentState = new DroppingAgentState();
        DateTimeFormatter timeFormatter = DateTimeFormatter.ofPattern("HH:mm:ss");
        timeString = "14:50:45";
        localTime = LocalTime.parse(timeString, timeFormatter);
        msg1  = new Message("ACTIVE_FIRE",1,1,"HIGH", localTime, timeString,1,1,5,"1","", 0.0);
        msg2  = new Message("ACTIVE_FIRE",1,1,"HIGH", localTime, timeString,1,1,5,"1","NOZZLE_JAM", 0.0);
    }

    @Test
    public void testDroppingEventRefill(){
        try{
            droppingAgentState.handleEvent(droneSubsystem,DroneEvent.START_DROPPING,msg1);
            assertEquals(droneSubsystem.getFoamRemaining(),15.0);
            assertEquals(droneSubsystem.getCurrentState(), droneSubsystem.getStateMap().get("IDLE"));
        }
        catch(Exception e){}

    }

    @Test
    public void testFaultEventRefill(){
        try{
            droppingAgentState.handleEvent(droneSubsystem,DroneEvent.DRONE_FAULT,msg2);
            assertEquals(droneSubsystem.getCurrentState(), droneSubsystem.getStateMap().get("FAULT"));
            assertEquals(droneSubsystem.getFoamRemaining(),15.0);
        }
        catch(Exception e){}

    }

}

