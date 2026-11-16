package parking;                                                  // same package as the other data sources

import java.net.DatagramPacket;                                   // one UDP message
import java.net.DatagramSocket;                                   // a UDP socket, sends and receives messages
import java.net.InetAddress;                                      // an IP address
import java.net.InterfaceAddress;                                 // an address of a network adapter, includes its broadcast address
import java.net.NetworkInterface;                                 // a network adapter, such as Wi-Fi or Ethernet
import java.net.URI;                                              // a web address
import java.net.http.HttpClient;                                  // Java's built-in web client
import java.net.http.HttpRequest;                                 // describes one web request
import java.net.http.HttpResponse;                                // holds the server's reply
import java.nio.charset.StandardCharsets;                         // text encoding
import java.time.Duration;                                        // used for timeouts
import java.util.Collections;                                     // helper to turn an enumeration into a list
import java.util.concurrent.Executors;                            // creates background schedulers
import java.util.concurrent.ScheduledExecutorService;             // runs a task repeatedly
import java.util.concurrent.TimeUnit;                             // SECONDS, etc.
import java.util.function.Consumer;                               // the callback type used by DataSource

/** Reads the latest frame from the Spring Boot backend, either at a fixed address or found automatically with "auto". */
public class HttpDataSource implements DataSource {

    private static final int DISCOVERY_PORT = 4210;                              // UDP port the backend listens on, same as DiscoveryResponder
    private static final String DISCOVER_MESSAGE = "PARKING_DISCOVER";           // the question we broadcast
    private static final String REPLY_PREFIX = "PARKING_BACKEND:";               // the answer starts with this, followed by the web port
    private static final String LOCAL_FALLBACK_URL = "http://localhost:8080/api/frame";   // tried when the broadcast gets no answer
    private static final int FAILS_BEFORE_REDISCOVERY = 3;                       // after this many failed requests in a row, search again

    private final boolean auto;                                   // true when the address is "auto"
    private final int slots;                                      // expected number of slots, from the layout
    private final HttpClient client = HttpClient.newBuilder()
            .connectTimeout(Duration.ofSeconds(2))                // give up connecting after 2 seconds
            .build();
    private volatile String url;                                  // the backend address in use, null while still searching
    private int failures = 0;                                    // failed requests in a row
    private ScheduledExecutorService scheduler;                   // the repeating background task

    public HttpDataSource(String url, int slots) {                // constructor called from ParkingDashboard
        this.auto = url.equalsIgnoreCase("auto");                 // "auto" means search for the backend
        this.url = auto ? null : url;                             // with a fixed address, use it right away
        this.slots = slots;                                       // save the slot count
    }

    @Override
    public void start(Consumer<boolean[]> onFrame) {              // called once by the dashboard to begin receiving data
        scheduler = Executors.newSingleThreadScheduledExecutor(r -> {
            Thread t = new Thread(r, "http-parking");             // name the thread for easier debugging
            t.setDaemon(true);                                    // daemon thread, so it never blocks app shutdown
            return t;
        });
        scheduler.scheduleWithFixedDelay(() -> {                  // run this block repeatedly
            try {
                poll(onFrame);                                    // do one round of work
            } catch (Exception ignored) {
                // never let an error stop the repeating task
            }
        }, 0, 1, TimeUnit.SECONDS);                               // start now, then wait 1 second between rounds
    }

    private void poll(Consumer<boolean[]> onFrame) {              // one round: find the backend if needed, then read the latest frame
        if (url == null) {                                        // we do not know where the backend is yet
            String found = findBackend();                         // search for it
            if (found == null) return;                            // not found, try again next round
            url = found;                                          // remember it
            failures = 0;                                         // start fresh
            System.out.println("[HttpDataSource] Using backend " + url);   // tell the console where we connected
        }
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(url))
                    .timeout(Duration.ofSeconds(2))               // give up waiting for a reply after 2 seconds
                    .GET()                                        // a plain GET request
                    .build();
            HttpResponse<String> response =
                    client.send(request, HttpResponse.BodyHandlers.ofString());   // send it and read the reply as text
            if (response.statusCode() == 200) {                   // the backend answered
                failures = 0;                                     // the connection is healthy
                boolean[] frame = FrameParser.parse(response.body(), slots);      // reuse your existing validation
                if (frame != null) onFrame.accept(frame);                         // pass valid frames to the dashboard
            } else {
                registerFailure();                                // an error reply counts as a failure
            }
        } catch (Exception e) {
            registerFailure();                                    // no connection counts as a failure
        }
    }

    private void registerFailure() {                              // counts failures and forgets the address when it seems to have moved
        failures++;                                               // one more failure
        if (auto && failures >= FAILS_BEFORE_REDISCOVERY) {       // only in auto mode, after several failures
            System.out.println("[HttpDataSource] Backend not reachable, searching again...");   // tell the console
            url = null;                                           // forget the address, the next round searches again
            failures = 0;                                         // start counting again
        }
    }

    private String findBackend() {                                // looks for the backend, returns its address or null
        String found = discoverByBroadcast();                     // first, ask the whole network
        if (found != null) return found;                          // someone answered
        return probe(LOCAL_FALLBACK_URL) ? LOCAL_FALLBACK_URL : null;   // otherwise, check if it runs on this same laptop
    }

    private boolean probe(String candidate) {                     // true if the address answers with HTTP 200
        try {
            HttpRequest request = HttpRequest.newBuilder(URI.create(candidate))
                    .timeout(Duration.ofSeconds(1))               // do not wait long
                    .GET()                                        // a plain GET request
                    .build();
            return client.send(request, HttpResponse.BodyHandlers.ofString()).statusCode() == 200;   // success only on 200
        } catch (Exception e) {
            return false;                                         // no answer
        }
    }

    private String discoverByBroadcast() {                        // broadcasts the question and waits for the backend's answer
        try (DatagramSocket socket = new DatagramSocket()) {      // open a UDP socket on any free port
            socket.setBroadcast(true);                            // allow sending to broadcast addresses
            socket.setSoTimeout(1500);                            // wait at most 1.5 seconds for an answer
            byte[] message = DISCOVER_MESSAGE.getBytes(StandardCharsets.UTF_8);   // the question as bytes

            for (NetworkInterface nic : Collections.list(NetworkInterface.getNetworkInterfaces())) {   // every network adapter
                if (!nic.isUp() || nic.isLoopback()) continue;    // skip adapters that are off, and the internal loopback
                for (InterfaceAddress address : nic.getInterfaceAddresses()) {   // every address of this adapter
                    InetAddress broadcast = address.getBroadcast();              // its broadcast address, null for IPv6
                    if (broadcast == null) continue;              // skip addresses without one
                    try {
                        socket.send(new DatagramPacket(message, message.length, broadcast, DISCOVERY_PORT));   // shout on this adapter
                    } catch (Exception ignored) {
                        // some adapters refuse, keep trying the others
                    }
                }
            }

            byte[] buffer = new byte[128];                        // space for the answer
            DatagramPacket reply = new DatagramPacket(buffer, buffer.length);    // an empty packet to fill
            socket.receive(reply);                                // wait here for an answer, throws if the time runs out
            String text = new String(reply.getData(), 0, reply.getLength(), StandardCharsets.UTF_8).trim();   // answer as text
            if (text.startsWith(REPLY_PREFIX)) {                  // it is our backend answering
                int port = Integer.parseInt(text.substring(REPLY_PREFIX.length()).trim());   // the number after the colon is the web port
                return "http://" + reply.getAddress().getHostAddress() + ":" + port + "/api/frame";   // sender's address + port + endpoint
            }
        } catch (Exception ignored) {
            // timeout or network error, report "not found"
        }
        return null;                                              // nobody answered
    }

    @Override
    public void stop() {                                          // called by the dashboard when it closes
        if (scheduler != null) scheduler.shutdownNow();           // stop the repeating task
    }
}