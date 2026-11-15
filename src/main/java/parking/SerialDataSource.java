package parking;

import com.fazecast.jSerialComm.SerialPort;

import java.io.BufferedReader;
import java.io.InputStreamReader;
import java.nio.charset.StandardCharsets;
import java.util.function.Consumer;

/** Reads lines such as "S:1,0,1,0" from an Arduino/ESP32 over USB serial. */
public class SerialDataSource implements DataSource {

    private final String portName;
    private final int baud;
    private final int slots;
    private volatile boolean running;
    private SerialPort port;

    public SerialDataSource(String portName, int baud, int slots) {
        this.portName = portName;
        this.baud = baud;
        this.slots = slots;
    }

    @Override
    public void start(Consumer<boolean[]> onFrame) {
        running = true;
        Thread t = new Thread(() -> {
            while (running) {
                try {
                    port = SerialPort.getCommPort(portName);
                    port.setBaudRate(baud);
                    port.setComPortTimeouts(SerialPort.TIMEOUT_READ_SEMI_BLOCKING, 2000, 0);
                    if (!port.openPort()) {
                        Thread.sleep(2000);   // retry until the board is plugged in
                        continue;
                    }
                    try (BufferedReader in = new BufferedReader(
                            new InputStreamReader(port.getInputStream(), StandardCharsets.US_ASCII))) {
                        String line;
                        while (running && (line = in.readLine()) != null) {
                            boolean[] frame = FrameParser.parse(line, slots);
                            if (frame != null) onFrame.accept(frame);   // invalid lines are ignored
                        }
                    }
                } catch (Exception ignored) {
                    // read timeout or unplugged board: close and retry
                } finally {
                    if (port != null) port.closePort();
                }
                try { Thread.sleep(1000); } catch (InterruptedException e) { return; }
            }
        }, "serial-parking");
        t.setDaemon(true);
        t.start();
    }

    @Override
    public void stop() {
        running = false;
        if (port != null) port.closePort();
    }
}
