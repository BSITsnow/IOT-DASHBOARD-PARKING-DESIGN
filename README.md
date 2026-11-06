# Smart Parking Dashboard (JavaFX)

Monitors parking availability for the IoT prototype and shows a lot map
with a route to the nearest vacant slot.

## Features
- Total, available and occupied slot counts
- Occupancy percentage with progress bar
- Parking lot map: lane, slots (green = vacant, red = occupied with a car icon)
- Route from the entrance to the nearest vacant slot, recalculated on every update
- Full-capacity alert banner when every slot is occupied
- Last valid update time, turns amber if no valid data for 10 s

## Requirements
JDK 17+ and Maven.

## Run
    mvn javafx:run                                           # simulated data
    mvn javafx:run -Djavafx.args="--layout=C:\lots\my-lot.txt"
    mvn javafx:run -Djavafx.args="--port=COM3 --baud=9600"   # real hardware

## Designing the lot (no code changes)
Edit `src/main/resources/lot-layout.txt`, or copy it and pass `--layout=`.
It sets the map size, lane, entrance, exit and every slot's position:

    canvas 680 410
    lane 20 170 640 100
    entrance 52 220
    slot P1 110 48 130 102 down     # id x y width height direction

- `down`: slot sits above the lane and opens toward it
- `up`: slot sits below the lane
- The number of slot lines is the number of monitored slots
- Slot order = order of values in the sensor data

Colors, fonts and sizes are in `src/main/resources/dashboard.css`.

## Hardware data format
Print one line per reading over serial, one value per slot in layout order
(1 = occupied):

    S:1,0,1,0,1,1

Lines with the wrong length or bad characters are ignored and do not
refresh the "last update" time.

Example Arduino sketch for 6 IR sensors (LOW = vehicle detected):

    const int pins[6] = {2, 3, 4, 5, 6, 7};
    void setup() { Serial.begin(9600); for (int p : pins) pinMode(p, INPUT); }
    void loop() {
      Serial.print("S:");
      for (int i = 0; i < 6; i++) {
        Serial.print(digitalRead(pins[i]) == LOW ? 1 : 0);
        if (i < 5) Serial.print(",");
      }
      Serial.println();
      delay(1000);
    }

## Files
- ParkingDashboard.java: window, stat cards, updates
- ParkingMap.java: draws the lot and the route
- LotLayout.java: reads lot-layout.txt
- FrameParser.java: validates incoming lines
- SimulatedDataSource / SerialDataSource: demo vs. real hardware
- resources/lot-layout.txt, resources/dashboard.css: your design files
