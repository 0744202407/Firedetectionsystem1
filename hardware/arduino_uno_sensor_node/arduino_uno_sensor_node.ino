#include <DHT.h>

#define DHTPIN 3
#define DHTTYPE DHT22

const int FLAME_PIN = 2;
const int MQ2_PIN = A0;
const int RELAY_PIN = 7;
const int BUZZER_PIN = 8;
const int LED_RED = 9;
const int LED_GREEN = 10;

const int SMOKE_WARNING = 320;
const int SMOKE_FIRE = 500;

DHT dht(DHTPIN, DHTTYPE);

String statusText = "SAFE";

void setup() {
  Serial.begin(9600);

  pinMode(FLAME_PIN, INPUT);
  pinMode(RELAY_PIN, OUTPUT);
  pinMode(BUZZER_PIN, OUTPUT);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);

  dht.begin();
  setSafeState();
}

void loop() {
  int smokeRaw = analogRead(MQ2_PIN);
  int flameDetected = digitalRead(FLAME_PIN) == LOW ? 1 : 0;
  float temp = dht.readTemperature();
  if (isnan(temp)) temp = -1.0;

  bool fire = false;
  bool warning = false;

  if (flameDetected == 1 || smokeRaw >= SMOKE_FIRE) {
    fire = true;
    statusText = "FIRE";
  } else if (smokeRaw >= SMOKE_WARNING) {
    warning = true;
    statusText = "WARNING";
  } else {
    statusText = "SAFE";
  }

  if (fire || warning) {
    digitalWrite(RELAY_PIN, HIGH);
    digitalWrite(BUZZER_PIN, HIGH);
    digitalWrite(LED_RED, HIGH);
    digitalWrite(LED_GREEN, LOW);
  } else {
    setSafeState();
  }

  // CSV format sent to ESP32:
  // status,smokeLevel,flameDetected,temperature
  // Example: FIRE,612,1,38.40
  Serial.print(statusText);
  Serial.print(",");
  Serial.print(smokeRaw);
  Serial.print(",");
  Serial.print(flameDetected);
  Serial.print(",");
  Serial.println(temp, 2);

  delay(2000);
}

void setSafeState() {
  digitalWrite(RELAY_PIN, LOW);
  digitalWrite(BUZZER_PIN, LOW);
  digitalWrite(LED_RED, LOW);
  digitalWrite(LED_GREEN, HIGH);
}
