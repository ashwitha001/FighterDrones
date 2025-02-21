package main;

/**
 * Represents coordinates for drones (single x,y) or fire zones (two corners).
 * - If x2,y2 == null, it's a single point (drone usage).
 * - Otherwise, (x1,y1) to (x2,y2) is a fire zone rectangle.
 */
public class Coordinates {
    private final int x1, y1;
    private final Integer x2, y2; // Nullable for single-point usage

    // Constructor for single points
    public Coordinates(int x, int y) {
        this.x1 = x;
        this.y1 = y;
        this.x2 = null;
        this.y2 = null;
    }

    // Constructor for rectangles
    public Coordinates(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public Integer getX2() { return x2; }
    public Integer getY2() { return y2; }

    @Override
    public String toString() {
        if (x2 == null || y2 == null) {
            return "(" + x1 + ", " + y1 + ")";
        } else {
            return "(" + x1 + ", " + y1 + ") to (" + x2 + ", " + y2 + ")";
        }
    }
}