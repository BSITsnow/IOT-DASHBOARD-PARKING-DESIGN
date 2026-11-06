package parking;

import java.util.Random;
import java.util.concurrent.Executors;
import java.util.concurrent.ScheduledExecutorService;
import java.util.concurrent.TimeUnit;
import java.util.function.Consumer;

/** Fake sensor data so the dashboard can be demoed without hardware. */
public class SimulatedDataSource implements DataSource {

    private final int slots;
    private final Random random = new Random();
    private ScheduledExecutorService scheduler;

    public SimulatedDataSource(int slots) {
        this.slots = slots;
    }

    @Override
    public void start(Consumer<boolean[]> onFrame) {
        boolean[] state = new boolean[slots];
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "simulated-parking");
            t.setDaemon(true);
            return t;
        });
        scheduler.scheduleAtFixedRate(() -> {
            int i = random.nextInt(slots);
            state[i] = !state[i];
            onFrame.accept(state.clone());
        }, 0, 2, TimeUnit.SECONDS);
    }

    @Override
    public void stop() {
        if (scheduler != null) scheduler.shutdownNow();
    }
}
