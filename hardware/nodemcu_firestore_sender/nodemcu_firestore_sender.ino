/**
 * NodeMCU (ESP8266) Fire Detection System
 * Connects to WiFi and sends sensor data to Firebase Firestore
 */

#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include <time.h>

#include "addons/TokenHelper.h"
#include "addons/FirestoreHelper.h"

// =========================
// UPDATE THESE CREDENTIALS
// =========================
#define WIFI_SSID "YOUR_WIFI_NAME"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
#define API_KEY "YOUR_FIREBASE_WEB_API_KEY"
#define FIREBASE_PROJECT_ID "firedetectionsystem1-dacb8"

// Pin definitions for sensors
#define FLAME_SENSOR_PIN D1      // Digital pin for flame sensor
#define MQ2_SENSOR_PIN A0        // Analog pin for MQ-2 smoke sensor
#define LED_STATUS_PIN D4        // Built-in LED for status

// Firebase objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

bool signupOK = false;
bool fireDetected = false;
int smokeLevel = 0;

// Thresholds
const int SMOKE_THRESHOLD = 400;    // Adjust based on testing
const unsigned long UPDATE_INTERVAL = 5000; // 5 seconds
unsigned long lastUpdate = 0;

void setup() {
  Serial.begin(115200);
  Serial.println("\n=== NodeMCU Fire Detection System ===");
  
  // Configure pins
  pinMode(FLAME_SENSOR_PIN, INPUT);
  pinMode(LED_STATUS_PIN, OUTPUT);
  
  // Initial LED state
  digitalWrite(LED_STATUS_PIN, LOW);  // LED on = not connected
  
  // Connect to WiFi
  connectWiFi();
  
  // Configure Firebase
  configFirebase();
  
  digitalWrite(LED_STATUS_PIN, HIGH); // LED off = connected
  Serial.println("System ready!");
}

void loop() {
  if (!signupOK || !Firebase.ready()) {
    delay(100);
    return;
  }
  
  // Read sensors
  readSensors();
  
  // Send data to Firebase every interval
  if (millis() - lastUpdate >= UPDATE_INTERVAL) {
    lastUpdate = millis();
    sendToFirebase();
  }
  
  delay(100);
}

void connectWiFi() {
  Serial.print("Connecting to WiFi");
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  
  Serial.println("\nWiFi connected!");
  Serial.print("IP Address: ");
  Serial.println(WiFi.localIP());
}

void configFirebase() {
  // Configure time
  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  waitForNtpTime();
  
  // Configure Firebase
  config.api_key = API_KEY;
  config.token_status_callback = tokenStatusCallback;
  
  // Anonymous signup
  if (Firebase.signUp(&config, &auth, "", "")) {
    signupOK = true;
    Serial.println("Firebase signup OK.");
  } else {
    Serial.printf("Firebase signup failed: %s\n", config.signer.signupError.message.c_str());
  }
  
  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

void waitForNtpTime() {
  time_t now = time(nullptr);
  int retries = 0;
  while (now < 100000 && retries < 20) {
    delay(500);
    now = time(nullptr);
    retries++;
    Serial.print(".");
  }
  Serial.println(" NTP time synced!");
}

void readSensors() {
  // Read flame sensor (digital)
  int flameValue = digitalRead(FLAME_SENSOR_PIN);
  // Flame sensor: LOW = fire detected, HIGH = no fire
  fireDetected = (flameValue == LOW);
  
  // Read smoke sensor (analog)
  int analogValue = analogRead(MQ2_SENSOR_PIN);
  // Map analog value to percentage (0-100)
  smokeLevel = map(analogValue, 0, 1023, 0, 100);
  
  // Debug output
  Serial.print("Flame: ");
  Serial.print(fireDetected ? "DETECTED" : "None");
  Serial.print(" | Smoke: ");
  Serial.print(smokeLevel);
  Serial.println("%");
  
  // LED indicator
  digitalWrite(LED_STATUS_PIN, fireDetected ? LOW : HIGH);
}

void sendToFirebase() {
  // Determine status
  String status = "SAFE";
  if (fireDetected) {
    status = "FIRE";
  } else if (smokeLevel > 70) {
    status = "WARNING";
  } else if (smokeLevel > 40) {
    status = "CAUTION";
  }
  
  // Get timestamp
  String timestampIso = currentIso8601Utc();
  
  // Create JSON payload
  FirebaseJson content;
  content.set("fields/status/stringValue", status);
  content.set("fields/smokeLevel/integerValue", String(smokeLevel));
  content.set("fields/flameDetected/booleanValue", fireDetected);
  content.set("fields/updatedAt/timestampValue", timestampIso);
  
  // Update Firestore
  const char* updateMask = "status,smokeLevel,flameDetected,updatedAt";
  
  if (Firebase.Firestore.patchDocument(
          &fbdo,
          FIREBASE_PROJECT_ID,
          "",
          "fire_status/current",
          content.raw(),
          updateMask)) {
    Serial.println("Firebase updated: " + status);
  } else {
    Serial.println("Firebase error: " + fbdo.errorReason());
  }
}

String currentIso8601Utc() {
  time_t now = time(nullptr);
  struct tm* utc = gmtime(&now);
  char buf[30];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", utc);
  return String(buf);
}

/**
 * Alternative: Receive data from Arduino via Serial
 * Uncomment this function and modify loop() to use serial input
 */
/*
void readFromSerial() {
  if (Serial.available()) {
    String line = Serial.readStringUntil('\n');
    line.trim();
    if (line.length() > 0) {
      // Format: STATUS,SMOKE_LEVEL,FLAME_DETECTED
      // Example: FIRE,85,1
      int p1 = line.indexOf(',');
      int p2 = line.indexOf(',', p1 + 1);
      
      if (p1 > 0 && p2 > 0) {
        String status = line.substring(0, p1);
        smokeLevel = line.substring(p1 + 1, p2).toInt();
        fireDetected = line.substring(p2 + 1).toInt() == 1;
        
        Serial.println("Received: " + status + " Smoke: " + smokeLevel);
      }
    }
  }
}
*/