#include <Servo.h>

#define BT_SERIAL Serial1

const int relayPins[8] = {2, 3, 4, 5, 6, 7, 8, 9};

const int mqAnalogPin = A0;
const int mqDigitalPin = 12;

const int flameAnalogPin = A1;
const int flameDigitalPin = 10;

const int buzzerPin = 11;
const int servoPin = 22;

const int gasThreshold = 350;
const bool gasActiveLow = true;
const bool flameActiveLow = true;

Servo windowServo;

char data;
bool smartSystem = false;
bool manualState[8] = {false, false, false, false, false, false, false, false};

bool alarmActive = false;
bool dangerAlreadyDetected = false;
bool lastGasDetected = false;
bool lastFlameDetected = false;

int lastGasValue = 0;
int lastFlameValue = 0;
int lastGasDigital = HIGH;
int lastFlameDigital = HIGH;

unsigned long startTime = 0;
unsigned long alarmStartTime = 0;
unsigned long lastSensorRead = 0;
unsigned long lastStatusSent = 0;

void setup() {
  Serial.begin(9600);
  BT_SERIAL.begin(9600);

  for (int i = 0; i < 8; i++) {
    pinMode(relayPins[i], OUTPUT);
    digitalWrite(relayPins[i], HIGH);
  }

  pinMode(mqDigitalPin, INPUT_PULLUP);
  pinMode(flameDigitalPin, INPUT_PULLUP);
  pinMode(buzzerPin, OUTPUT);
  noTone(buzzerPin);

  windowServo.attach(servoPin);
  windowServo.write(0);

  sendReady();
  sendStatus();
}

void loop() {
  readBluetoothCommands();

  if (millis() - lastSensorRead >= 500) {
    lastSensorRead = millis();
    checkGasAndFlame();
  }

  if (smartSystem) {
    runSmartSystem();
  } else {
    for (int i = 0; i < 8; i++) {
      applyManualState(i);
    }
  }

  runAlarm();

  if (millis() - lastStatusSent >= 1000) {
    lastStatusSent = millis();
    sendStatus();
  }
}

void readBluetoothCommands() {
  while (BT_SERIAL.available()) {
    data = BT_SERIAL.read();

    switch (data) {
      case 'A':
        smartSystem = true;
        startTime = millis();
        sendStatus();
        break;

      case 'H':
        smartSystem = false;
        manualState[4] = false;
        manualState[5] = false;
        manualState[6] = false;
        digitalWrite(relayPins[4], HIGH);
        digitalWrite(relayPins[5], HIGH);
        digitalWrite(relayPins[6], HIGH);
        sendStatus();
        break;

      case 'L': setManual(0, true); sendStatus(); break;
      case 'P': setManual(0, false); sendStatus(); break;

      case 'R':
        setManual(1, true);
        windowServo.write(80);
        sendStatus();
        break;

      case 'T':
        setManual(1, false);
        windowServo.write(180);
        sendStatus();
        break;

      case 'Q': setManual(2, true); sendStatus(); break;
      case 'W': setManual(2, false); sendStatus(); break;

      case 'G': setManual(3, true); sendStatus(); break;
      case 'F': setManual(3, false); sendStatus(); break;

      case 'D': setManual(4, true); sendStatus(); break;
      case 'V': setManual(4, false); sendStatus(); break;

      case 'E': setManual(5, true); sendStatus(); break;
      case 'N': setManual(5, false); sendStatus(); break;

      case 'C': setManual(6, true); sendStatus(); break;
      case 'M': setManual(6, false); sendStatus(); break;

      case 'I': setManual(7, true); sendStatus(); break;
      case 'O': setManual(7, false); sendStatus(); break;

      case 'Y':
        turnOffAllLoads();
        sendStatus();
        break;

      case 'X':
        noTone(buzzerPin);
        alarmActive = false;
        sendStatus();
        break;

      case 'S':
        sendStatus();
        break;
    }
  }
}

void setManual(int relayIndex, bool state) {
  manualState[relayIndex] = state;

  if (!smartSystem) {
    applyManualState(relayIndex);
  }
}

void applyManualState(int relayIndex) {
  if (manualState[relayIndex]) {
    digitalWrite(relayPins[relayIndex], LOW);
  } else {
    digitalWrite(relayPins[relayIndex], HIGH);
  }
}

void turnOffAllLoads() {
  smartSystem = false;

  for (int i = 0; i < 8; i++) {
    manualState[i] = false;
    digitalWrite(relayPins[i], HIGH);
  }
}

void checkGasAndFlame() {
  lastGasValue = analogRead(mqAnalogPin);
  lastFlameValue = analogRead(flameAnalogPin);
  lastGasDigital = digitalRead(mqDigitalPin);
  lastFlameDigital = digitalRead(flameDigitalPin);

  bool digitalGasDetected = gasActiveLow ? (lastGasDigital == LOW) : (lastGasDigital == HIGH);
  bool digitalFlameDetected = flameActiveLow ? (lastFlameDigital == LOW) : (lastFlameDigital == HIGH);

  lastGasDetected = digitalGasDetected || (lastGasValue >= gasThreshold);
  lastFlameDetected = digitalFlameDetected;

  bool dangerDetected = lastGasDetected || lastFlameDetected;

  if (dangerDetected && !dangerAlreadyDetected) {
    dangerAlreadyDetected = true;
    turnOffAllLoads();
    windowServo.write(90);
    alarmActive = true;
    alarmStartTime = millis();
    sendAlert();
  }

  if (!dangerDetected && dangerAlreadyDetected) {
    dangerAlreadyDetected = false;
    sendSafe();
  }
}

void runAlarm() {
  if (alarmActive) {
    if (millis() - alarmStartTime < 20000) {
      tone(buzzerPin, 3000);
    } else {
      noTone(buzzerPin);
      alarmActive = false;
    }
  }
}

void runSmartSystem() {
  unsigned long elapsedTime = millis() - startTime;

  for (int i = 0; i < 8; i++) {
    applyManualState(i);
  }

  if (elapsedTime < 20000) {
    digitalWrite(relayPins[6], HIGH);
    digitalWrite(relayPins[4], HIGH);
    digitalWrite(relayPins[5], HIGH);
  } else if (elapsedTime < 40000) {
    digitalWrite(relayPins[6], LOW);
    digitalWrite(relayPins[4], HIGH);
    digitalWrite(relayPins[5], HIGH);
  } else if (elapsedTime < 60000) {
    digitalWrite(relayPins[6], LOW);
    digitalWrite(relayPins[4], LOW);
    digitalWrite(relayPins[5], HIGH);
  } else if (elapsedTime < 80000) {
    digitalWrite(relayPins[6], HIGH);
    digitalWrite(relayPins[4], HIGH);
    digitalWrite(relayPins[5], LOW);
  } else {
    smartSystem = false;
    manualState[4] = false;
    manualState[5] = false;
    manualState[6] = false;
    digitalWrite(relayPins[4], HIGH);
    digitalWrite(relayPins[5], HIGH);
    digitalWrite(relayPins[6], HIGH);
  }
}

void sendReady() {
  BT_SERIAL.println("READY,ECOGUARD_HOME");
  Serial.println("READY,ECOGUARD_HOME");
}

void sendSafe() {
  BT_SERIAL.println("SAFE,FIRE=0,GAS=0");
  Serial.println("SAFE,FIRE=0,GAS=0");
}

void sendAlert() {
  BT_SERIAL.print("ALERT");
  printStatusPayload(BT_SERIAL);
  BT_SERIAL.println();

  Serial.print("ALERT");
  printStatusPayload(Serial);
  Serial.println();
}

void sendStatus() {
  BT_SERIAL.print("STATUS");
  printStatusPayload(BT_SERIAL);
  BT_SERIAL.println();

  Serial.print("STATUS");
  printStatusPayload(Serial);
  Serial.println();
}

void printStatusPayload(Stream &port) {
  port.print(",FIRE=");
  port.print(lastFlameDetected ? 1 : 0);
  port.print(",GAS=");
  port.print(lastGasDetected ? 1 : 0);
  port.print(",GAS_ADC=");
  port.print(lastGasValue);
  port.print(",FLAME_ADC=");
  port.print(lastFlameValue);
  port.print(",SMART=");
  port.print(smartSystem ? 1 : 0);
  port.print(",ALARM=");
  port.print(alarmActive ? 1 : 0);

  for (int i = 0; i < 8; i++) {
    port.print(",R");
    port.print(i + 1);
    port.print("=");
    port.print(digitalRead(relayPins[i]) == LOW ? 1 : 0);
  }
}
