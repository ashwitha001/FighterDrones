import java.util.concurrent.BlockingQueue;

public class DroneSubsystem implements Runnable {
    private final BlockingQueue<String> dronesQueue;

    public DroneSubsystem(BlockingQueue<String> dronesQueue) {
        this.dronesQueue = dronesQueue;
    }

    @Override
    public void run() {
        while (true) {
            try {
                String eventFire = dronesQueue.take(); //  Send to DroneSubsystem
                System.out.println("[DroneSubsystem] Received event from Scheduler: " + eventFire);
                System.out.println("[DroneSubsystem] Drone dispatched...");
                System.out.println("[DroneSubsystem] FIRE REMOVED");

//                dronesQueue.put(eventFire);
//                System.out.println("[DroneSubsystem] Send Completion to Scheduler: " + eventFire);
            } catch (InterruptedException e) {
                e.printStackTrace();
            }
        }

    }
}
