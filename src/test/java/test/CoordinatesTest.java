package test;

import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import main.Coordinates;

import static org.junit.jupiter.api.Assertions.assertEquals;

public class CoordinatesTest {
    Coordinates coords;
    @BeforeEach
    void setUp(){
       coords  = new Coordinates(100,200,300,400);
    }

    @Test
    void testX1(){
    assertEquals(100,coords.getX1());
    }

    @Test
    void testY1(){
        assertEquals(200,coords.getY1());
    }

    @Test
    void testX2(){
        assertEquals(300,coords.getX2());
    }

    @Test
    void testY2(){
        assertEquals(400,coords.getY2());
    }

    @Test
    void testCoordTS(){
        assertEquals("(100, 200) to (300, 400)",coords.toString());
    }
}
