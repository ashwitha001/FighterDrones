package test;

import main.*;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.net.InetSocketAddress;

import static org.junit.jupiter.api.Assertions.*;

public class FaultTest {
    private DroneSubsystem droneSubsystem;

    @BeforeEach
    public void setUp() {
        //initialize the DroneSubsystem with a mock scheduler address.
        droneSubsystem = new DroneSubsystem(1, new InetSocketAddress("localhost", 9999));
    }

    @Test
    public void testNozzleJamFault() throws InterruptedException {
        sendFaultMessage("NOZZLE_JAM");
        Thread.sleep(100); //allow time for processing
        assertEquals("FAULT", getCurrentStateName());
    }

    @Test
    public void testStuckEnRouteFault() throws InterruptedException {
        sendFaultMessage("STUCK_EN_ROUTE");
        Thread.sleep(100);
        assertEquals("FAULT", getCurrentStateName());
    }

    @Test
    public void testCommsFault() throws InterruptedException {
        sendFaultMessage("COMMS_FAULT");
        Thread.sleep(100);
        assertEquals("FAULT", getCurrentStateName());
    }

    private void sendFaultMessage(String faultType) {
        Message faultMessage = new Message(
                "DRONE_FAULT",
                1,
                0,
                "FAULT",
                java.time.LocalTime.now(),
                java.time.LocalTime.now().toString(),
                0,
                0,
                droneSubsystem.getFoamRemaining(),
                "FAULT_TEST",
                faultType,
                1.0
        );
        droneSubsystem.setLastKnownMessage(faultMessage);
        droneSubsystem.setState("FAULT"); // Simulating state transition
    }

    private String getCurrentStateName() {
        for (var entry : droneSubsystem.getStateMap().entrySet()) {
            if (entry.getValue().equals(droneSubsystem.getCurrentState())) {
                return entry.getKey();
            }
        }
        return "UNKNOWN";
    }
}

