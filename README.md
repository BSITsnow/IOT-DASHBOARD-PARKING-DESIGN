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

## Backend, database and accounts (Spring Boot + H2)

The `backend/` folder receives sensor frames, stores every slot change in a database, and serves
a web portal with two kinds of accounts.

    cd backend
    ./mvnw spring-boot:run          (Windows: mvnw.cmd spring-boot:run)

Open http://localhost:8080 and sign in.

| Account | What they see |
|---|---|
| **Client** | `/client/index.html`: live availability (green/red slots, counts, occupancy %, full-lot alert) |
| **Admin** | `/admin/index.html`: analytics, user management, sign-in history, raw slot changes |

First administrator (created automatically when no admin exists; change it in
`backend/src/main/resources/application.properties`):

    username: admin
    password: admin123      <- change this after the first sign-in (Admin > My account)

Visitors can create their own **client** account on the login page
(`parking.registration.enabled=false` turns that off; admins can still create accounts).

### Database tables (H2 file: `backend/data/parking`)
- `slot_event`: one row per slot status change (slot number, occupied or vacant, time). Raw data.
- `app_user`: accounts (username, role ADMIN/CLIENT, active flag, salted password hash, last sign-in)
- `login_log`: every sign-in attempt (who, success/failed, reason, IP, time)

Passwords are stored only as salted PBKDF2 hashes. Nobody, including admins, can read them back;
an admin can reset a password. Five wrong passwords lock that username and IP for one minute.

### Analytics (admin only)
Computed from `slot_event` by `Analytics.java` for the last 1, 7, 30 or 90 days: occupancy now,
average occupancy, arrivals, average and longest stay, peak hour, busiest slot, occupancy by hour of
day, arrivals per day, and a per-slot table. Hours use `parking.timezone` (default `Asia/Manila`).
Time before the first recorded change is not counted, and a slot is assumed to keep its last status
while the backend is switched off.

### Who can reach what
- `POST /api/frame` and `GET /api/frame` stay open: the sensor board posts there and the JavaFX
  dashboard polls it (`--server=http://localhost:8080/api/frame`).
- Everything else needs a sign-in. `/admin/**`, `/api/admin/**` and `/api/events` are admin only.
- Disabling an account or changing its role takes effect on the next request.

### H2 file location
The database lives in `backend/data/`. Delete that folder to start fresh (this removes all accounts and history).
