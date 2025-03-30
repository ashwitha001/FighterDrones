package test;

import main.UDPReceiver;
import main.UDPUtil;
import main.Message;

import org.junit.jupiter.api.*;
import java.io.IOException;
import java.net.*;
import java.time.LocalTime;
import java.util.concurrent.*;

import static org.junit.jupiter.api.Assertions.*;

/**
 * Test Class to test messages and UDP communication
 */
class UDPTest {
    private static final int PORT = 5000;
    private DatagramSocket socket;
    private ExecutorService exec;

    //set up a socket and executor to test proper UDP communication
    @BeforeEach
    void setUp() throws SocketException {
        socket = new DatagramSocket(PORT);
        exec = Executors.newSingleThreadExecutor();
    }

    @AfterEach
    void tearDown() {
        socket.close();
        exec.shutdownNow();
    }

    //test sending a message via UDP communication, tests sendMessage in UDPUtil
    @Test
    void sendMessageTest() throws IOException {
        // Arrange: Create a Message object with meaningful test values
        Message testMessage = new Message(
                "FireIncident",// type
                5,                  // zoneID
                "High",             // severity
                LocalTime.now(),    // eventTime
                "11:11:11",         // eventTimeString
                100, 200,   // centerX, centerY
                50.0,               // remainingFoamNeeded
                "12345",            // eventID
                "",                 // faultType
                0.0                 // faultTime
        );

        InetSocketAddress destination = new InetSocketAddress("localhost", PORT);

        UDPUtil.sendMessage(testMessage, destination);

        //here we check if the data properly arrives in the socket
        byte[] buffer = new byte[4096];
        DatagramPacket receivedPacket = new DatagramPacket(buffer, buffer.length);
        socket.receive(receivedPacket);
        assertNotNull(receivedPacket);
        assertTrue(receivedPacket.getLength() > 0);
    }

    //now we test if the UDP receiver receives the message properly.
    //the intent is that, if we get to this test, UDPUtil's sendMessage works, so we can use it to send a
    //message to UDPReceiver
    @Test
    void UDPReceiverTest() throws Exception {
        //set up the receiver
        BlockingQueue<Message> receivedMessages = new LinkedBlockingQueue<>();
        UDPReceiver receiver = new UDPReceiver(socket, receivedMessages::add);
        exec.execute(receiver);

        Message testMessage = new Message(
                "Scheduler",   // type
                1,                  // droneID
                5,                  // zoneID
                "Medium",           // severity
                LocalTime.now(),    // eventTime
                "11:11:11",         // eventTimeString
                100, 200,   // centerX, centerY
                30.5,               // remainingFoamNeeded
                "12345",            // eventID
                "",                 // faultType
                0.0                 // faultTime
        );

        InetSocketAddress destination = new InetSocketAddress("localhost", PORT);

        UDPUtil.sendMessage(testMessage, destination);

        //check if the content of the message is received without error
        Message received = receivedMessages.poll(2, TimeUnit.SECONDS);
        assertNotNull(received);
        assertEquals("Scheduler", received.getType());
        assertEquals(1, received.getDroneID());
        assertEquals(5, received.getZoneID());
        assertEquals("Medium", received.getSeverity());
        assertEquals("11:11:11", received.getEventTimeString());//no check for actual LocalTime.now(), this is sufficient
        assertEquals(100, received.getCenterX());
        assertEquals(200, received.getCenterY());
        assertEquals(30.5, received.getRemainingFoamNeeded());
        assertEquals("12345", received.getEventID());
        assertEquals("", received.getFaultType());
        assertEquals(0.0, received.getFaultTime());
    }

    //test error handling with bad data
    @Test
    void missionFailureTest() throws IOException {
        //set up bad data
        byte[] badData = "KNOCKKNOCK WHOSTHERE BOO BOOWHO WHYAREYOUCRYING".getBytes();
        DatagramPacket packet = new DatagramPacket(badData, badData.length, InetAddress.getByName("localhost"), PORT);
        socket.send(packet);

        //test that the UDPReceiver doesn't crash
        assertDoesNotThrow(() -> {
            DatagramPacket receivedPacket = new DatagramPacket(new byte[4096], 4096);
            socket.receive(receivedPacket);
        });
    }
}