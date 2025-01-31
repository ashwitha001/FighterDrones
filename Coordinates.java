/**
 * Represents the coordinates of fire incident zones.
 * This will be used to determine drone deployment/navigation between zones.
 */
public class Coordinates {
    private final int x1, y1, x2, y2;

    public Coordinates(int x1, int y1, int x2, int y2) {
        this.x1 = x1;
        this.y1 = y1;
        this.x2 = x2;
        this.y2 = y2;
    }

    public int getX1() { return x1; }
    public int getY1() { return y1; }
    public int getX2() { return x2; }
    public int getY2() { return y2; }

    @Override
    public String toString() {
        return "(" + x1 + ", " + y1 + ") to (" + x2 + ", " + y2 + ")";
    }
}
