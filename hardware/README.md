# Fire Detection Hardware Setup

This folder contains ready-to-upload sketches for:

- `arduino_uno_sensor_node/arduino_uno_sensor_node.ino`
- `esp32_firestore_sender/esp32_firestore_sender.ino`

## 1) Upload Arduino Uno sketch

1. Open Arduino IDE.
2. Install library: `DHT sensor library` (Adafruit).
3. Open `arduino_uno_sensor_node.ino`.
4. Select board: `Arduino Uno`.
5. Select correct COM port.
6. Upload.
7. Open Serial Monitor at `9600` baud and verify lines like:
   - `SAFE,210,0,29.40`
   - `WARNING,365,0,31.20`
   - `FIRE,612,1,38.40`

## 2) Upload ESP32 sketch

1. Install ESP32 boards package from Boards Manager.
2. Install library: `Firebase ESP Client` (Mobizt).
3. Open `esp32_firestore_sender.ino`.
4. Update:
   - `WIFI_SSID`
   - `WIFI_PASSWORD`
   - `API_KEY` (from Firebase project web app config)
   - `FIREBASE_PROJECT_ID` (already set to `firedetectionsystem1-dacb8`)
5. Select board: `ESP32 Dev Module`.
6. Select correct COM port.
7. Upload.
8. Open Serial Monitor at `115200` baud and verify:
   - `WiFi connected.`
   - `Firebase anonymous signup OK.`
   - `Firestore updated: ...`

## 3) Wiring between Arduino and ESP32

- Arduino `TX (D1)` -> ESP32 `RX2 (GPIO16)` through level shift (5V -> 3.3V safe divider)
- Arduino `GND` -> ESP32 `GND`

## 4) Firestore expected document

Collection: `fire_status`  
Document: `current`

Fields written by ESP32:

- `status` (string: `SAFE`, `WARNING`, `FIRE`)
- `smokeLevel` (integer)
- `flameDetected` (boolean)
- `temperature` (double)
- `updatedAt` (timestamp)

## 5) App side

Android app dashboard already listens to:

- `fire_status/current`

and updates the UI in real-time.
