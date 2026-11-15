package parking; // must match the folder, same package as ParkingDashboard

import java.net.URI; // represents a web address
import java.net.http.HttpClient; // Java's built-in web client
import java.net.http.HttpRequest; // describes one request
import java.net.http.HttpResponse; // holds the server's reply
import java.time.Duration; // used for timeouts
import java.util.concurrent.Executors; // creates background schedulers
import java.util.concurrent.ScheduledExecutorService; // runs a task repeatedly
import java.util.concurrent.TimeUnit; // SECONDS, etc.
import java.util.function.Consumer; // the callback type used by DataSource

/** Polls the Spring Boot backend for the latest frame, e.g. "S:1,0". */
public class HttpDataSource implements DataSource { // implements the same interface as your other data sources

    private final String url; // backend address, e.g. http://localhost:8080/api/frame
    private final int slots; // expected number of slots, from the layout
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2)) // give up connecting after 2 seconds
            .build();
    private ScheduledExecutorService scheduler; // the repeating background task

    public HttpDataSource(String url, int slots) { // constructor called from ParkingDashboard
        this.url = url; // save the address
        this.slots = slots; // save the slot count
    }

    @Override
    public void start(Consumer<boolean[]> onFrame) { // called once by the dashboard to begin receiving data
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "http-parking"); // name the thread for easier debugging
            t.setDaemon(true); // daemon thread, so it never blocks app shutdown
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> { // run this block repeatedly
            try {
                HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                        .timeout(Duration.ofSeconds(2)) // give up waiting for a reply after 2 seconds
                        .GET() // a plain GET request
                        .build();
                HttpResponse<String> response = client.send(request, HttpResponse.BodyHandlers.ofString()); // send it
                                                                                                            // and read
                                                                                                            // the reply
                                                                                                            // as text
                if (response.statusCode() == 200) { // only handle successful replies
                    boolean[] frame = FrameParser.parse(response.body(), slots); // reuse your existing validation
                    if (frame != null)
                        onFrame.accept(frame); // pass valid frames to the dashboard
                }
            } catch (Exception ignored) {
                // backend not running or network error: skip this round and try again
            }
        }, 0, 1, TimeUnit.SECONDS); // start now, then wait 1 second between rounds
    }

    @Override
    public void stop() { // called by the dashboard when it closes
        if (scheduler != null)
            scheduler.shutdownNow(); // stop the repeating task
    }
}