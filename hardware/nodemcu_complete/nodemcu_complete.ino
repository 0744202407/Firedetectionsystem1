/**
 * Fire Detection System - NodeMCU ESP8266 Only
 * Inachukua sensors zote, WiFi na Firebase bila Arduino
 * 
 * Sensors: MQ-2, Flame, DHT11
 * Outputs: LED, Buzzer, Relay
 */

#include <ESP8266WiFi.h>
#include <Firebase_ESP_Client.h>
#include <DHT.h>
#include <time.h>

#include "addons/TokenHelper.h"
#include "addons/FirestoreHelper.h"

// =========================
// UPDATE THESE VALUES
// =========================
#define WIFI_SSID "YOUR_WIFI_NAME"          // Jina la WiFi yako
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"  // Password ya WiFi
#define API_KEY "YOUR_FIREBASE_WEB_API_KEY" // Firebase Web API Key
#define FIREBASE_PROJECT_ID "firedetectionsystem1-dacb8"

// Pin Connections
#define MQ2_SENSOR_PIN A0       // MQ-2 Smoke Sensor (Analog)
#define FLAME_SENSOR_PIN D1     // Flame Sensor (Digital)
#define DHT_PIN D2              // DHT11 Sensor (Digital)
#define LED_RED_PIN D3          // Red LED (Alert)
#define LED_GREEN_PIN D4        // Green LED (Safe)
#define BUZZER_PIN D5           // Buzzer
#define RELAY_PIN D6            // Relay (for external device)

// DHT Type
#define DHTTYPE DHT11

// Thresholds
const int SMOKE_WARNING = 320;
const int SMOKE_FIRE = 500;

// Firebase objects
FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

bool signupOK = false;
bool fireDetected = false;
bool pumpActivated = false;
int smokeLevel = 0;
float temperature = 0;
float humidity = 0;
String statusText = "SAFE";

unsigned long lastUpdate = 0;
const unsigned long UPDATE_INTERVAL = 5000; // 5 seconds
unsigned long lastCommandCheck = 0;

DHT dht(DHT_PIN, DHTTYPE);

void setup() {
  Serial.begin(115200);
  Serial.println("\n=== Fire Detection System ===");
  
  // Configure pins
  pinMode(FLAME_SENSOR_PIN, INPUT);
  pinMode(LED_RED_PIN, OUTPUT);
  pinMode(LED_GREEN_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(RELAY_PIN, OUTPUT);
  
  // Initialize DHT
  dht.begin();
  
  // Initial safe state
  setSafeState();
  
  // Connect to WiFi
  connectWiFi();
  
  // Configure Firebase
  configFirebase();
  
  Serial.println("System ready!");
}

void loop() {
  // Read all sensors
  readSensors();
  
  // Determine status
  determineStatus();
  
  // Control outputs
  controlOutputs();
  
  // Send to Firebase every 5 seconds
  if (millis() - lastUpdate >= UPDATE_INTERVAL) {
    lastUpdate = millis();
    sendToFirebase();
  }
  
  // Check commands every 5 seconds
  if (millis() - lastCommandCheck >= UPDATE_INTERVAL) {
    lastCommandCheck = millis();
    checkCommands();
  }
  
  delay(1000);
}

void readSensors() {
  // Read MQ-2 Smoke Sensor
  int smokeRaw = analogRead(MQ2_SENSOR_PIN);
  smokeLevel = smokeRaw;
  
  // Read Flame Sensor (LOW = fire detected)
  int flameValue = digitalRead(FLAME_SENSOR_PIN);
  bool flameDetected = (flameValue == LOW);
  
  // Read DHT11
  float t = dht.readTemperature();
  float h = dht.readHumidity();
  
  if (!isnan(t)) temperature = t;
  if (!isnan(h)) humidity = h;
  
  // Debug output
  Serial.print("Smoke: ");
  Serial.print(smokeLevel);
  Serial.print(" | Flame: ");
  Serial.print(flameDetected ? "DETECTED" : "None");
  Serial.print(" | Temp: ");
  Serial.print(temperature);
  Serial.print("C | Humidity: ");
  Serial.print(humidity);
  Serial.println("%");
}

void determineStatus() {
  int flameValue = digitalRead(FLAME_SENSOR_PIN);
  bool flameDetected = (flameValue == LOW);
  
  if (flameDetected || smokeLevel >= SMOKE_FIRE) {
    statusText = "FIRE";
    fireDetected = true;
    pumpActivated = true;
  } else if (smokeLevel >= SMOKE_WARNING) {
    statusText = "WARNING";
    fireDetected = false;
  } else {
    statusText = "SAFE";
    fireDetected = false;
  }
}

void controlOutputs() {
  if (statusText == "FIRE") {
    // Fire state - all alerts on
    digitalWrite(LED_RED_PIN, HIGH);
    digitalWrite(LED_GREEN_PIN, LOW);
    digitalWrite(BUZZER_PIN, HIGH);
  } else if (statusText == "WARNING") {
    // Warning state - LED on, buzzer off
    digitalWrite(LED_RED_PIN, HIGH);
    digitalWrite(LED_GREEN_PIN, LOW);
    digitalWrite(BUZZER_PIN, LOW);
  } else {
    // Safe state
    setSafeState();
  }
  
  // Control pump
  if (pumpActivated) {
    digitalWrite(RELAY_PIN, HIGH);
  } else {
    digitalWrite(RELAY_PIN, LOW);
  }
}

void setSafeState() {
  digitalWrite(LED_RED_PIN, LOW);
  digitalWrite(LED_GREEN_PIN, HIGH);
  digitalWrite(BUZZER_PIN, LOW);
  digitalWrite(RELAY_PIN, LOW);
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

void sendToFirebase() {
  if (!signupOK || !Firebase.ready()) {
    Serial.println("Firebase not ready!");
    return;
  }
  
  // Create JSON document
  FirebaseJson json;
  json.set("fields/status/stringValue", statusText);
  json.set("fields/smokeLevel/integerValue", smokeLevel);
  json.set("fields/flameDetected/booleanValue", fireDetected ? "true" : "false");
  json.set("fields/temperature/doubleValue", String(temperature, 2));
  json.set("fields/humidity/doubleValue", String(humidity, 2));
  
  String documentPath = "fire_status/current";
  
  if (Firebase.Firestore.patchDocument(&fbdo, FIREBASE_PROJECT_ID, "", documentPath.c_str(), json.raw())) {
    Serial.println("Firebase updated successfully!");
  } else {
    Serial.println("Firebase update failed!");
    Serial.println(fbdo.errorReason());
  }
}

void checkCommands() {
  if (Firebase.ready()) {
    if (Firebase.Firestore.getDocument(&fbdo, FIREBASE_PROJECT_ID, "", "commands/pump", "")) {
      FirebaseJson json;
      json.setJsonData(fbdo.payload().c_str());
      bool activate = false;
      if (json.getBool("fields/activate/booleanValue", activate)) {
        pumpActivated = activate;
      }
    }
  }
}