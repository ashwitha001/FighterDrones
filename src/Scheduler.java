package src;

import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.BlockingQueue;

/**
 * Scheduler:
 * - Acts as a central router between FireIncidentSubsystem and DroneSubsystem.
 * - Passes fire events to available drones.
 * - Notifies FireIncidentSubsystem when fires are extinguished.
 */
public class Scheduler implements Runnable {
    private final BlockingQueue<Message> incidentQueue;
    private final BlockingQueue<Message> dronesQueue;
    private final BlockingQueue<Message> droneCompletionQueue;
    private final BlockingQueue<Message> incidentCompletionQueue;
    private final Map<Integer, DroneState> droneStatus;

    public Scheduler(BlockingQueue<Message> incidentQueue, BlockingQueue<Message> dronesQueue,
                     BlockingQueue<Message> droneCompletionQueue, BlockingQueue<Message> incidentCompletionQueue) {
        this.incidentQueue = incidentQueue;
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
        this.incidentCompletionQueue = incidentCompletionQueue;
        this.droneStatus = new HashMap<>();
        droneStatus.put(0, DroneState.IDLE); // Initialize drone status
    }

    @Override
    public void run() {
        while (true) {
            try {
                // Receive fire event from FireIncidentSubsystem
                Message event = incidentQueue.take();
                Logger.log("[Scheduler]", "Received Fire Event: " + event);

                // Check for an available drone
                int availableDrone = -1;
                for (Map.Entry<Integer, DroneState> entry : droneStatus.entrySet()) {
                    if (entry.getValue() == DroneState.IDLE) {
                        availableDrone = entry.getKey();
                        break;
                    }
                }

                if (availableDrone != -1) {
                    droneStatus.put(availableDrone, DroneState.EN_ROUTE);
                    dronesQueue.put(event);
                    Logger.log("[Scheduler]", "Dispatched Drone " + availableDrone + " to Zone " + event.getZoneID());
                } else {
                    Logger.log("[Scheduler]", "No available drones for Zone " + event.getZoneID() + "! Fire queued.");
                }

                // Wait for drone status updates
                Message droneUpdate = droneCompletionQueue.take();
                if ("DRONE_EN_ROUTE".equals(droneUpdate.getType())) {
                    droneStatus.put(droneUpdate.getDroneID(), DroneState.EN_ROUTE);
                    Logger.log("[Scheduler]", "Drone " + droneUpdate.getDroneID() + " is en route to Zone " + droneUpdate.getZoneID());
                } else if ("FIRE_EXTINGUISHED".equals(droneUpdate.getType())) {
                    droneStatus.put(droneUpdate.getDroneID(), DroneState.RETURNING);
                    Logger.log("[Scheduler]", "Fire Extinguished at Zone " + droneUpdate.getZoneID());
                    incidentCompletionQueue.put(droneUpdate);
                } else if ("DRONE_IDLE".equals(droneUpdate.getType())) {
                    droneStatus.put(droneUpdate.getDroneID(), DroneState.IDLE);
                    Logger.log("[Scheduler]", "Drone " + droneUpdate.getDroneID() + " is now IDLE.");
                }
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }
    }
}

