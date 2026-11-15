package parking.backend;                                        // same package as the other backend classes

import org.springframework.beans.factory.annotation.Value;      // reads values from application.properties
import org.springframework.boot.ApplicationArguments;           // arguments passed at startup, unused here
import org.springframework.boot.ApplicationRunner;              // lets us run code once the app has started
import org.springframework.stereotype.Component;                // makes Spring create and run this class

import java.net.DatagramPacket;                                 // one UDP message
import java.net.DatagramSocket;                                 // a UDP socket, sends and receives packets
import java.nio.charset.StandardCharsets;                       // text encoding

@Component                                                      // Spring finds this class automatically at startup
public class DiscoveryResponder implements ApplicationRunner {

    private static final int DISCOVERY_PORT = 4210;             // UDP port the ESP32 broadcasts to

    @Value("${server.port:8080}")                               // the web server's port, 8080 if not set
    private int httpPort;

    @Override
    public void run(ApplicationArguments args) {                // called once when the backend has started
        Thread thread = new Thread(this::listen, "udp-discovery");   // run the listener in the background
        thread.setDaemon(true);                                 // do not block the app from shutting down
        thread.start();                                         // start listening
    }

    private void listen() {
        try (DatagramSocket socket = new DatagramSocket(DISCOVERY_PORT)) {   // open UDP port 4210 on all network interfaces
            byte[] buffer = new byte[256];                      // space for one incoming message
            while (true) {                                      // keep listening forever
                DatagramPacket request = new DatagramPacket(buffer, buffer.length);   // an empty packet to fill
                socket.receive(request);                        // wait here until a message arrives
                String message = new String(request.getData(), 0, request.getLength(),
                        StandardCharsets.UTF_8).trim();         // turn the received bytes into text
                if (message.equals("PARKING_DISCOVER")) {       // only answer our own discovery message
                    byte[] reply = ("PARKING_BACKEND:" + httpPort).getBytes(StandardCharsets.UTF_8);   // e.g. "PARKING_BACKEND:8080"
                    socket.send(new DatagramPacket(reply, reply.length,
                            request.getAddress(), request.getPort()));   // answer to whoever asked
                }
            }
        } catch (Exception e) {
            System.err.println("Discovery responder stopped: " + e.getMessage());   // for example if port 4210 is already in use
        }
    }
}