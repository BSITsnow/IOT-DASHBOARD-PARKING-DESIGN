package parking;

import java.util.function.Consumer;

/** Anything that can feed valid slot frames to the dashboard. */
public interface DataSource {
    /** Begin delivering frames. true = occupied. Called from a background thread. */
    void start(Consumer<boolean[]> onFrame);

    void stop();
}
