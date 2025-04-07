package test;

import main.DroneSubsystem;
import main.Utility;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.*;
import java.io.IOException;
import java.io.File;
import java.io.FileWriter;
import java.net.InetSocketAddress;


public class UtilityTest {

    //check the file reading behaviour of countEventLines
    @Test
    public void readLinesTest() throws IOException {
        File emptyFile = File.createTempFile("empty", ".csv");
        File eventFile = File.createTempFile("events", ".csv");
        try (FileWriter eventWriter = new FileWriter(eventFile)) {
            eventWriter.write("header\n");
            eventWriter.write("fire1\n");
            eventWriter.write("fire2\n");
            eventWriter.flush();
        } catch (IOException e) {
            throw new RuntimeException(e);
        }

        //reading the empty file should give 0 lines
        assertEquals(0, Utility.countEventLines(emptyFile.getAbsolutePath()));

        //reading the event file should give 2 events, one for each fire(header is ignored)
        assertEquals(2, Utility.countEventLines(eventFile.getAbsolutePath()));
    }

    //test proper computation of travel time in different scenarios
    @Test
    public void travelTimeTest() {

        //3 sec to accelerate/decelerate, full distance for accel/decel is 58.32m
        //thus it should take longer than 6 sec to get to an area whose distance is over 58.32m
        assertTrue(Utility.computeTravelTime(0, 0, 60, 0) > 6.0);
        assertEquals(6.012923076923077, Utility.computeTravelTime(0, 0, 60, 0));

        //for a distance of 36m, which is below full accel/decel distance, different calculation is used
        assertEquals(1.8229308607505998, Utility.computeTravelTime(0, 0, 36, 0));
    }

    //visual check of progress bar, no assertions needed
    @Test
    public void progressBarTest() throws InterruptedException {
        //visual test
        final InetSocketAddress address = new InetSocketAddress("localhost", 5001);
        DroneSubsystem droneSubsystem = new DroneSubsystem(0, address);
        Utility.showProgress(1.0, "test of progress bar", droneSubsystem, 1, 1, 1, 1);
    }

    //test of calculations for nozzleDropTime
    @Test
    public void nozzleDropTest() {
        //confirm that nozzle opening time is 0.01, so payload = 0
        assertEquals(0.01, Utility.nozzleDropTime(0));

        //confirm that calculations are correct, 18/9 flow rate + 0.01 open time = 2.01 sec
        assertEquals(2.01, Utility.nozzleDropTime(18));
    }
}