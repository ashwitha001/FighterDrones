package main;

import javax.swing.*;
import java.awt.*;
import java.io.BufferedReader;
import java.io.FileReader;
import java.io.IOException;
import java.util.HashMap;
import java.util.Map;

public class SimulationUI extends JFrame {
    // Window dimensions
    private static final int WINDOW_WIDTH = 800;
    private static final int WINDOW_HEIGHT = 600;
    private static final int LEGEND_WIDTH = 120; // Reduced width
    private static final int SCALE = 2; // Scale factor to convert coordinates
    private static final int GRID_SIZE = 100; // Base grid size before scaling

    // Maps to store simulation state
    private final Map<Integer, Rectangle> zones = new HashMap<>();         // Zone ID -> Zone boundaries
    private final Map<Integer, String> fires = new HashMap<>();           // Zone ID -> Fire severity
    private final Map<Integer, Coordinates> droneLocations = new HashMap<>();
    private final Map<Integer, String> droneStatuses = new HashMap<>(); // OUTBOUND, RETURNING, WORKING, FAULT

    private final JPanel mainPanel;
    private int maxX = 0;
    private int maxY = 0;

    public SimulationUI() {
        // Basic window setup
        setTitle("Drone Fire Fighting Simulation");
        setSize(WINDOW_WIDTH + LEGEND_WIDTH, WINDOW_HEIGHT);
        setDefaultCloseOperation(JFrame.EXIT_ON_CLOSE);
        setLayout(new BorderLayout());

        // Create main display panel
        mainPanel = new JPanel() {
            @Override
            protected void paintComponent(Graphics g) {
                super.paintComponent(g);
                drawSimulation(g);
            }
        };
        mainPanel.setBackground(Color.WHITE);
        add(mainPanel, BorderLayout.CENTER);

        // Add legend on the right
        add(createLegend(), BorderLayout.EAST);

        // Load zone data from file
        loadZones("zones.csv");
    }

    // Create legend panel with color explanations
    private JPanel createLegend() {
        JPanel legend = new JPanel();
        legend.setLayout(new BoxLayout(legend, BoxLayout.Y_AXIS));
        legend.setBorder(BorderFactory.createTitledBorder("Legend"));
        legend.setPreferredSize(new Dimension(LEGEND_WIDTH, WINDOW_HEIGHT));

        // Create sections for fires and drones
        JPanel fireSection = new JPanel();
        fireSection.setLayout(new BoxLayout(fireSection, BoxLayout.Y_AXIS));
        fireSection.setBorder(BorderFactory.createTitledBorder("Fires"));

        JPanel droneSection = new JPanel();
        droneSection.setLayout(new BoxLayout(droneSection, BoxLayout.Y_AXIS));
        droneSection.setBorder(BorderFactory.createTitledBorder("Drones"));

        // Add fire items
        addLegendItem(fireSection, Color.RED, "High");
        addLegendItem(fireSection, Color.ORANGE, "Medium");
        addLegendItem(fireSection, Color.YELLOW, "Low");
        addLegendItem(fireSection, Color.GREEN, "Extinguished");

        // Add drone items
        addLegendItem(droneSection, Color.GRAY, "Drone");
        addLegendItem(droneSection, Color.RED, "Fault");

        // Add sections to legend
        legend.add(fireSection);
        legend.add(Box.createVerticalStrut(10)); // Add spacing
        legend.add(droneSection);

        return legend;
    }

    // Helper to add items to legend
    private void addLegendItem(JPanel section, Color color, String text) {
        JPanel item = new JPanel(new FlowLayout(FlowLayout.LEFT, 5, 2)); // Reduced spacing
        item.setMaximumSize(new Dimension(LEGEND_WIDTH - 20, 25)); // Control height

        JPanel colorBox = new JPanel();
        colorBox.setPreferredSize(new Dimension(12, 12)); // Smaller color boxes
        colorBox.setBackground(color);

        item.add(colorBox);
        item.add(new JLabel(text));
        section.add(item);
    }

    // Parse coordinates in format "(x;y)" - same as FireIncidentSubsystem
    private int[] parseCoords(String c) {
        c = c.trim().replace("(", "").replace(")", "");
        String[] xy = c.split(";");
        int x = Integer.parseInt(xy[0].trim());
        int y = Integer.parseInt(xy[1].trim());
        return new int[]{x, y};
    }

    // Load zone data from CSV file - similar to FireIncidentSubsystem's loadZoneData
    private void loadZones(String filename) {
        try (BufferedReader br = new BufferedReader(new FileReader(filename))) {
            br.readLine(); // Skip header
            String line;
            while ((line = br.readLine()) != null) {
                String[] parts = line.split(",");
                int zoneId = Integer.parseInt(parts[0].trim());
                int[] startC = parseCoords(parts[1]);
                int[] endC = parseCoords(parts[2]);

                // Update maximum coordinates for grid sizing
                maxX = Math.max(maxX, Math.max(startC[0], endC[0]));
                maxY = Math.max(maxY, Math.max(startC[1], endC[1]));

                // Create zone rectangle with scaled coordinates
                zones.put(zoneId, new Rectangle(
                        startC[0] / SCALE,
                        startC[1] / SCALE,
                        (endC[0] - startC[0]) / SCALE,
                        (endC[1] - startC[1]) / SCALE
                ));
                System.out.println("[SimulationUI] Loaded zone " + zoneId + ": " + zones.get(zoneId));
            }
        } catch (IOException e) {
            System.err.println("[SimulationUI] Could not read " + filename + ": " + e.getMessage());
        }

        // Add base zone (0,0,0,0) like FireIncidentSubsystem does
        zones.put(0, new Rectangle(0, 0, 0, 0));
        System.out.println("[SimulationUI] Loaded zone 0: " + zones.get(0) + " (base)");

        // Adjust window size based on maximum coordinates
        int scaledMaxX = (maxX / SCALE) + 50; // Add padding
        int scaledMaxY = (maxY / SCALE) + 50;
        setSize(Math.max(WINDOW_WIDTH, scaledMaxX) + LEGEND_WIDTH, Math.max(WINDOW_HEIGHT, scaledMaxY));
    }

    // Main drawing method
    private void drawSimulation(Graphics g) {
        Graphics2D g2d = (Graphics2D) g;
        g2d.setStroke(new BasicStroke(1));

        // Draw grid
        drawGrid(g2d);

        // Draw zones and fires with thicker lines
        g2d.setStroke(new BasicStroke(2));
        for (Map.Entry<Integer, Rectangle> entry : zones.entrySet()) {
            int zoneId = entry.getKey();
            Rectangle zone = entry.getValue();

            // Skip drawing the base zone (0,0,0,0)
            if (zoneId == 0) continue;

            // Draw zone
            g2d.setColor(Color.BLACK);
            g2d.drawRect(zone.x, zone.y, zone.width, zone.height);
            g2d.drawString("Z(" + zoneId + ")", zone.x + 5, zone.y + 20);

            // Draw fire if present
            if (fires.containsKey(zoneId)) {
                drawFire(g2d, zone, fires.get(zoneId));
            }
        }

        // Draw drones
        for (Map.Entry<Integer, Coordinates> entry : droneLocations.entrySet()) {
            int droneId = entry.getKey();
            Coordinates pos = entry.getValue();
            String status = droneStatuses.getOrDefault(droneId, "OUTBOUND");

            Color color = switch (status) {
                case "OUTBOUND" -> Color.BLUE;
                case "RETURNING" -> new Color(128, 0, 128);
                case "WORKING" -> new Color(30, 110, 50);
                case "FAULT" -> Color.RED;
                default -> Color.GRAY;
            };

            g2d.setColor(color);
            g2d.fillOval(pos.getX1() - 5, pos.getY1() - 5, 10, 10); // Small circle for drone
            g2d.drawString("D" + droneId, pos.getX1() + 6, pos.getY1());
        }

    }

    // Draw grid lines aligned with zone boundaries
    private void drawGrid(Graphics2D g) {
        g.setColor(Color.LIGHT_GRAY);

        // Calculate grid spacing based on GRID_SIZE and SCALE
        int spacing = GRID_SIZE / SCALE;

        // Draw vertical lines
        for (int x = 0; x <= maxX / SCALE; x += spacing) {
            g.drawLine(x, 0, x, Math.max(WINDOW_HEIGHT, maxY / SCALE));
        }

        // Draw horizontal lines
        for (int y = 0; y <= maxY / SCALE; y += spacing) {
            g.drawLine(0, y, Math.max(WINDOW_WIDTH, maxX / SCALE), y);
        }
    }

    // Draw fire in a zone
    private void drawFire(Graphics2D g, Rectangle zone, String severity) {
        Color color = switch (severity) {
            case "HIGH" -> Color.RED;
            case "MODERATE" -> Color.ORANGE;
            case "LOW" -> Color.YELLOW;
            case "EXTINGUISHED" -> Color.GREEN;
            default -> Color.GRAY;
        };

        g.setColor(color);
        g.fillRect(zone.x + zone.width / 2 - 10, zone.y + zone.height / 2 - 10, 20, 20);
    }

    // Public methods to update simulation state
    public void updateFireStatus(int zoneId, String severity) {
        fires.put(zoneId, severity);
        mainPanel.repaint();
    }

    public void clearFireStatus(int zoneId) {
        fires.remove(zoneId);
        mainPanel.repaint();
    }

    public void updateDroneLocation(int droneId, int x, int y, String status) {
        SwingUtilities.invokeLater(() -> {
            Coordinates coords = new Coordinates(x / SCALE, y / SCALE); // Scale down the coordinates
            droneLocations.put(droneId, coords);
            droneStatuses.put(droneId, status);
            mainPanel.repaint();
        });
    }

    public void removeDrone(int droneId) {
        droneLocations.remove(droneId);
        droneStatuses.remove(droneId);
        mainPanel.repaint();
    }
}