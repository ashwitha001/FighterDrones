package test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import static org.junit.jupiter.api.Assertions.assertEquals;
import main.Coordinates;

/**
 * Test Class to test methods in Coordinates.java
 *
 */
public class CoordinatesTest {
    Coordinates coords;

    /**
     * Creating Coordinates
     *
     */
    @BeforeEach
    void setUp(){
       coords  = new Coordinates(100,200,300,400);
    }

    /**
     * Testing getter for x1 variable for Coordinates
     */
    @Test
    void testX1(){
    assertEquals(100,coords.getX1());
    }

    /**
     * Testing getter for y1 variable for Coordinates
     */
    @Test
    void testY1(){
        assertEquals(200,coords.getY1());
    }

    /**
     * Testing getter for x2 variable for Coordinates
     */
    @Test
    void testX2(){
        assertEquals(300,coords.getX2());
    }

    /**
     * Testing getter for y2 variable for Coordinates
     */
    @Test
    void testY2(){
        assertEquals(400,coords.getY2());
    }

    /**
     * Testing toString for Coordinates
     */
    @Test
    void testCoordTS(){
        assertEquals("(100, 200) to (300, 400)",coords.toString());
    }
}
