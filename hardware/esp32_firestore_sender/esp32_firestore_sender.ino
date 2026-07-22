#include <WiFi.h>
#include <Firebase_ESP_Client.h>
#include <time.h>

#include "addons/TokenHelper.h"
#include "addons/FirestoreHelper.h"

// =========================
// Update these credentials
// =========================
#define WIFI_SSID "YOUR_WIFI_NAME"
#define WIFI_PASSWORD "YOUR_WIFI_PASSWORD"
#define API_KEY "YOUR_FIREBASE_WEB_API_KEY"
#define FIREBASE_PROJECT_ID "firedetectionsystem1-dacb8"

FirebaseData fbdo;
FirebaseAuth auth;
FirebaseConfig config;

bool signupOK = false;

void setup() {
  Serial.begin(115200);               // debug monitor
  Serial2.begin(9600, SERIAL_8N1, 16, 17); // RX2=16 (from Arduino TX), TX2=17

  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);
  while (WiFi.status() != WL_CONNECTED) {
    delay(300);
    Serial.print(".");
  }
  Serial.println("\nWiFi connected.");

  configTime(0, 0, "pool.ntp.org", "time.nist.gov");
  waitForNtpTime();

  config.api_key = API_KEY;
  config.token_status_callback = tokenStatusCallback;

  if (Firebase.signUp(&config, &auth, "", "")) {
    signupOK = true;
    Serial.println("Firebase anonymous signup OK.");
  } else {
    Serial.printf("Firebase signup failed: %s\n", config.signer.signupError.message.c_str());
  }

  Firebase.begin(&config, &auth);
  Firebase.reconnectWiFi(true);
}

void loop() {
  if (!signupOK || !Firebase.ready()) return;
  if (!Serial2.available()) return;

  String line = Serial2.readStringUntil('\n');
  line.trim();
  if (line.length() == 0) return;

  String status;
  int smokeLevel = 0;
  int flameInt = 0;
  float temperature = -1.0;
  if (!parseCsv(line, status, smokeLevel, flameInt, temperature)) {
    Serial.println("Parse failed: " + line);
    return;
  }

  bool flameDetected = flameInt == 1;
  String timestampIso = currentIso8601Utc();

  FirebaseJson content;
  content.set("fields/status/stringValue", status);
  content.set("fields/smokeLevel/integerValue", String(smokeLevel));
  content.set("fields/flameDetected/booleanValue", flameDetected);
  content.set("fields/temperature/doubleValue", temperature);
  content.set("fields/updatedAt/timestampValue", timestampIso);

  const char* updateMask = "status,smokeLevel,flameDetected,temperature,updatedAt";
  if (Firebase.Firestore.patchDocument(
          &fbdo,
          FIREBASE_PROJECT_ID,
          "",
          "fire_status/current",
          content.raw(),
          updateMask)) {
    Serial.println("Firestore updated: " + status + " smoke=" + String(smokeLevel));
  } else {
    Serial.println("Firestore error: " + fbdo.errorReason());
  }

  delay(500);
}

bool parseCsv(const String& line, String& status, int& smokeLevel, int& flameInt, float& temperature) {
  int p1 = line.indexOf(',');
  int p2 = line.indexOf(',', p1 + 1);
  int p3 = line.indexOf(',', p2 + 1);
  if (p1 < 0 || p2 < 0 || p3 < 0) return false;

  status = line.substring(0, p1);
  smokeLevel = line.substring(p1 + 1, p2).toInt();
  flameInt = line.substring(p2 + 1, p3).toInt();
  temperature = line.substring(p3 + 1).toFloat();
  return true;
}

void waitForNtpTime() {
  time_t now = time(nullptr);
  int retries = 0;
  while (now < 100000 && retries < 20) {
    delay(500);
    now = time(nullptr);
    retries++;
  }
}

String currentIso8601Utc() {
  time_t now = time(nullptr);
  struct tm* utc = gmtime(&now);
  char buf[25];
  strftime(buf, sizeof(buf), "%Y-%m-%dT%H:%M:%SZ", utc);
  return String(buf);
}
