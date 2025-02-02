package test.java;

import main.java.Logger;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.io.ByteArrayOutputStream;
import java.io.PrintStream;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class LoggerTest {
    Logger logger;

    private final ByteArrayOutputStream outputStream = new ByteArrayOutputStream();
    private final PrintStream originalOut = System.out;

    @BeforeEach
    void setUp() {
        logger = new Logger();
        System.setOut(new PrintStream(outputStream));
    }

    @AfterEach
    void tearDown() {
        System.setOut(originalOut);
    }

    @Test
    void testValidOutput() {
        logger.log("testSubsystem", "testMessage");
        String output = String.format("%-" + 30 + "s %s%n", "testSubsystem", "testMessage");

        assertEquals(output, outputStream.toString());
    }
}
