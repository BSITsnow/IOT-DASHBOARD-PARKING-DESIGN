// Pin numbers for slot 1's sensor
const int TRIG1 = 5;    // GPIO 5 sends the trigger pulse for sensor 1
const int ECHO1 = 18;   // GPIO 18 reads the echo pulse from sensor 1

// Pin numbers for slot 2's sensor
const int TRIG2 = 19;   // GPIO 19 sends the trigger pulse for sensor 2
const int ECHO2 = 21;   // GPIO 21 reads the echo pulse from sensor 2

// If the measured distance is below this value in cm, we say the slot is occupied
const int OCCUPIED_CM = 15;  // adjust this after testing with a real car or object

// A function that measures distance for any sensor, you pass in its trig and echo pins
float readDistanceCm(int trigPin, int echoPin) {
  digitalWrite(trigPin, LOW);        // make sure the trigger pin starts at LOW
  delayMicroseconds(2);              // wait 2 microseconds to settle
  digitalWrite(trigPin, HIGH);       // start the trigger pulse
  delayMicroseconds(10);             // the sensor needs a 10 microsecond HIGH pulse to fire
  digitalWrite(trigPin, LOW);        // end the pulse, sensor now sends out the ultrasonic burst

  // pulseIn measures how long ECHO stays HIGH in microseconds, 30000 is a timeout (30 ms)
  long duration = pulseIn(echoPin, HIGH, 30000);

  if (duration == 0) {               // 0 means no echo came back before the timeout
    return -1;                       // return -1 to signal "no valid reading"
  }

  // Sound travels about 0.034 cm per microsecond, divide by 2 because the sound goes there and back
  return duration * 0.034 / 2;
}

void setup() {
  Serial.begin(115200);              // start serial communication so we can print to the Serial Monitor
  pinMode(TRIG1, OUTPUT);            // trig pins send signals out, so they are outputs
  pinMode(ECHO1, INPUT);             // echo pins receive signals, so they are inputs
  pinMode(TRIG2, OUTPUT);            // same for sensor 2 trig
  pinMode(ECHO2, INPUT);             // same for sensor 2 echo
}

void loop() {
  float d1 = readDistanceCm(TRIG1, ECHO1);   // measure distance for slot 1
  delay(60);                                  // short pause so sensor 1's echo does not interfere with sensor 2
  float d2 = readDistanceCm(TRIG2, ECHO2);   // measure distance for slot 2

  // Slot is occupied if we got a valid reading (not -1) and it is closer than the threshold
  bool slot1Occupied = (d1 > 0 && d1 < OCCUPIED_CM);
  bool slot2Occupied = (d2 > 0 && d2 < OCCUPIED_CM);

  Serial.print("Slot 1: ");                         // print label
  Serial.print(d1);                                  // print the distance value
  Serial.print(" cm, ");                             // print the unit
  Serial.println(slot1Occupied ? "OCCUPIED" : "FREE");   // print status, the ? : picks one of two texts

  Serial.print("Slot 2: ");
  Serial.print(d2);
  Serial.print(" cm, ");
  Serial.println(slot2Occupied ? "OCCUPIED" : "FREE");

  Serial.println("---");                            // separator line between readings
  delay(500);                                        // wait half a second before the next round
}