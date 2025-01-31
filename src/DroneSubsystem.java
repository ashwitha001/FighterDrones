package src;

import java.util.concurrent.BlockingQueue;

public class DroneSubsystem implements Runnable {
    private final BlockingQueue<String> dronesQueue;
    private final BlockingQueue<String> droneCompletionQueue;


    public DroneSubsystem(BlockingQueue<String> dronesQueue, BlockingQueue<String> droneCompletionQueue) {
        this.dronesQueue = dronesQueue;
        this.droneCompletionQueue = droneCompletionQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                String eventFire = dronesQueue.take(); //  Send to DroneSubsystem
                System.out.println("[DroneSubsystem] Received Event: " + eventFire);
                System.out.println("[DroneSubsystem] Drone dispatched...");
                Thread.sleep(2000);
                System.out.println("[DroneSubsystem] FIRE REMOVED at event: " + eventFire);

                droneCompletionQueue.put(eventFire);
                //System.out.println("[DroneSubsystem] Sent completion message: " + eventFire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
