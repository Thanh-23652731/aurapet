/*
 * =============================================================================
 *  AURAPET - ESP32 MÁY CHO ĂN THÔNG MINH (bản 2.1)
 * =============================================================================
 *  - Nhận lệnh từ Web/App qua MQTT (backend chuyển tiếp), gửi trạng thái 2s/lần
 *    (1s/lần khi đang nhả hạt)
 *  - Cân HX711 đo đúng số gram nhả ra, động cơ bước có gia tốc
 *  - Tự động cho ăn khi phát hiện mèo VÀ khay < 10g (đúng lưu đồ Hình 6)
 *  - Bình nước dùng bơm tuần hoàn chạy liên tục; mực nước do ESP32 máy dọn cát
 *    (firmware/esp32_litter_water) đo và báo về web/app - máy này KHÔNG điều khiển bơm
 *  - Topic bí mật + Last Will "offline" để web biết máy mất kết nối ngay
 *
 *  CÁCH CHẠY (bản 2.1):
 *    - Callback MQTT chỉ GHI NHẬN lệnh (đặt cờ), không quay động cơ trong callback.
 *    - Nhả hạt là một "state machine" chạy trong loop(): mỗi vòng loop quay 1 đoạn
 *      ngắn rồi đọc cân, nên MQTT, nút bấm, LCD vẫn chạy trong lúc nhả hạt.
 *
 *  ĐỔI WI-FI KHÔNG CẦN NẠP LẠI CODE:
 *    - Nếu không vào được Wi-Fi đã lưu, máy tự phát Wi-Fi "AuraPet-Setup"
 *    - Hoặc GIỮ NÚT TRỪ BÌ (GPIO 26) khi cắm điện/bấm RESET để mở chế độ cài đặt
 *    - Điện thoại kết nối "AuraPet-Setup" (mật khẩu trong secrets.h, mặc định aurapet123)
 *      -> trang cài đặt tự mở (hoặc vào 192.168.4.1) -> chọn mạng -> nhập mật khẩu -> Save
 *
 *  Thư viện cần cài (Arduino IDE > Library Manager):
 *    WiFiManager (tzapu), PubSubClient (Nick O'Leary), ArduinoJson (Benoit Blanchon, v7),
 *    HX711 (Bogdan Necula), LiquidCrystal I2C (Frank de Brabander)
 *
 *  Cấu hình Wi-Fi/MQTT nằm trong secrets.h (copy từ secrets.example.h).
 *
 *  Lệnh nhận từ backend (PREFIX/command):
 *    {"action":"FEED","gram":30,"source":"Web"}   cho ăn ngay (gram chỉ dùng cho lần này)
 *    {"action":"STOP"}                            dừng động cơ (chỉ khi đang nhả)
 *    {"action":"TARE"}                            trừ bì cân về 0 g
 *    {"action":"SET_TARGET","gram":40}            đổi khẩu phần mặc định
 *    {"action":"AUTO","enabled":true,"sensor":true}  bật/tắt tự cho ăn khi mèo đến;
 *                                                 sensor=false: camera AI đang chạy, máy không tự nhả
 *                                                 theo cảm biến mà chờ backend gửi FEED đúng khẩu phần của bé
 * =============================================================================
 */

#include "HX711.h"
#include "secrets.h"
#include <ArduinoJson.h>
#include <LiquidCrystal_I2C.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiManager.h> // tzapu WiFiManager: cài Wi-Fi qua điện thoại
#include <Wire.h>
#if MQTT_USE_TLS
#include <WiFiClientSecure.h>
#endif

// =============================================================================
// 1. CHÂN PHẦN CỨNG
// =============================================================================
#define TRIG 5
#define ECHO 18
#define SENSOR_PIN 19 // cảm biến phát hiện mèo (IR/PIR)
#define BTN_FEED 32
#define BTN_STOP 33
#define BTN_AUTO 25
#define BTN_TARE 26
#define LED_RED 27
#define LED_GREEN 14
#define STEP_PIN 13
#define DIR_PIN 23
#define EN_PIN 17
#define HX_DT 16
#define HX_SCK 4
#define SDA_PIN 21
#define SCL_PIN 22

// =============================================================================
// 2. THÔNG SỐ VẬN HÀNH
// =============================================================================
float CALIB_FACTOR = 420.0;       // hệ số hiệu chuẩn cân
float TARGET_GRAM = 30.0;         // khẩu phần mặc định (backend đồng bộ lại khi kết nối)
const float OVERSHOOT_GRAM = 2.0; // dừng sớm bù quán tính hạt rơi
const float EMPTY_BOWL_GRAM = 10.0; // khay < 10g mới được tự cho ăn
const float MIN_GRAM = 5.0, MAX_GRAM = 150.0;

const bool MOTOR_TEST_ON_BOOT = false; // true = quay thử 1 vòng khi khởi động (sẽ rơi hạt!)

const int STEPS_PER_REV = 3200; // TMC2209/A4988 1/16 bước
const int DELAY_START_US = 500;
const int DELAY_MIN_US = 250;
const int RAMP_STEPS = 960;
const int CHUNK_STEPS = 800;          // số xung mỗi vòng loop() trước khi đọc cân
const int MQTT_POLL_STEPS = 400;      // giữa đoạn quay vẫn nhận lệnh STOP
const unsigned long FEED_TIMEOUT = 20000;
const unsigned long SETTLE_MS = 300;  // chờ hạt rơi hết rồi mới cân lần cuối

const float FULL_DIST_CM = 3.0;   // khoảng cách cảm biến siêu âm khi thùng đầy
const float EMPTY_DIST_CM = 18.0; // khi thùng rỗng
const float LOW_LEVEL_CM = 15.0;  // > 15cm: cảnh báo sắp hết hạt
const float LOW_HYSTERESIS_CM = 2.0;     // hết báo khi < 13cm, tránh nhấp nháy quanh ngưỡng
const unsigned long DIST_HOLD_MS = 5000; // siêu âm mất tiếng vọng thoáng qua thì giữ số cũ
const bool SENSOR_ACTIVE_LOW = true;
const int STABLE_COUNT = 3;
const unsigned long AUTO_COOLDOWN = 60000;       // tối thiểu 60s giữa 2 lần tự cho ăn
const unsigned long FEED_CMD_DEDUP_MS = 3000;    // bỏ lệnh FEED lặp lại trong 3s (bấm 2 lần)
const unsigned long TELEMETRY_INTERVAL = 2000;
const unsigned long FEED_TELEMETRY_INTERVAL = 1000;

// =============================================================================
// 3. MQTT
// =============================================================================
#if MQTT_USE_TLS
WiFiClientSecure netClient;
#else
WiFiClient netClient;
#endif
PubSubClient mqtt(netClient);
String tTelemetry, tCommand, tLog, tStatus;

// =============================================================================
// 4. TRẠNG THÁI
// =============================================================================
LiquidCrystal_I2C *lcd = nullptr;
bool lcdOk = false;
HX711 scale;
bool scaleOk = false;

bool autoMode = false;
// false = camera AI đang chạy: backend nhận ra từng bé rồi gửi lệnh FEED đúng khẩu phần của bé,
// nên máy KHÔNG tự nhả khi cảm biến thấy mèo (tránh nhả khẩu phần chung trước khi camera nhận ra bé)
bool sensorFeed = true;
bool petPresent = false;
int sameCount = 0;
float weight = 0.0;
float currentDist = -1.0; // khoảng cách hợp lệ gần nhất (-1 = không đo được)
float lastGoodDist = -1.0;
unsigned long lastGoodDistAt = 0;
bool lowFood = false;
int feedCount = 0;
unsigned long lastAuto = 0;
unsigned long lastTelemetry = 0, lastLcd = 0, lastPrint = 0;
String statusMsg = "San sang";

// ---- Lệnh MQTT đã ghi nhận, loop() sẽ thực hiện ----
volatile bool stopRequested = false;
bool pendingFeed = false;
float pendingFeedGram = 0;
String pendingFeedSource;
bool pendingTare = false;
bool telemetryDue = false;
unsigned long lastFeedCmdAt = 0;

// ---- Lượt nhả hạt hiện tại (state machine) ----
enum FeedPhase : uint8_t { FEED_IDLE, FEED_RUNNING, FEED_SETTLING };

struct FeedJob {
  FeedPhase phase = FEED_IDLE;
  float gram = 0;
  float startW = 0;
  float goal = 0;
  String source;
  unsigned long startedAt = 0;
  unsigned long settleAt = 0;
  long steps = 0;
  const char *result = "";
};
FeedJob job;

bool isFeedingNow() { return job.phase != FEED_IDLE; }

// =============================================================================
// 5. TIỆN ÍCH PHẦN CỨNG
// =============================================================================
void logMsg(const String &msg) {
  statusMsg = msg;
  Serial.printf("[%.1fs] %s\n", millis() / 1000.0, msg.c_str());
}

void lcdLine(int row, String s) {
  if (!lcdOk) return;
  while (s.length() < 16) s += ' ';
  lcd->setCursor(0, row);
  lcd->print(s.substring(0, 16));
}

bool i2cExists(byte addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void setupLcd() {
  byte addr = i2cExists(0x27) ? 0x27 : (i2cExists(0x3F) ? 0x3F : 0);
  if (addr == 0) {
    logMsg("LCD: khong tim thay (SDA 21, SCL 22)");
    return;
  }
  lcd = new LiquidCrystal_I2C(addr, 16, 2);
  lcd->init();
  lcd->backlight();
  lcdOk = true;
  lcdLine(0, "AuraPet Feeder");
  lcdLine(1, "Khoi dong...");
}

bool pressed(int pin) {
  if (digitalRead(pin) == LOW) {
    delay(30);
    if (digitalRead(pin) == LOW) {
      while (digitalRead(pin) == LOW) delay(5);
      return true;
    }
  }
  return false;
}

float readDistanceCm() {
  digitalWrite(TRIG, LOW);
  delayMicroseconds(2);
  digitalWrite(TRIG, HIGH);
  delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  long d = pulseIn(ECHO, HIGH, 25000);
  return d == 0 ? -1 : d * 0.0343 / 2.0;
}

float readWeight(int times) {
  if (!scaleOk || !scale.is_ready()) return weight;
  weight = scale.get_units(times);
  if (weight < 0 && weight > -2) weight = 0; // khử nhiễu quanh 0
  return weight;
}

void doTare() {
  if (scale.wait_ready_timeout(1000)) {
    scale.tare();
    scaleOk = true;
    weight = 0.0;
    logMsg("Da tru bi can ve 0g");
  } else {
    scaleOk = false;
    logMsg("HX711 khong phan hoi (DT 16, SCK 4)");
  }
}

// Đọc siêu âm, giữ số hợp lệ gần nhất và tính "sắp hết hạt" có độ trễ
void updateFoodLevel() {
  float d = readDistanceCm();
  if (d > 0) {
    lastGoodDist = d;
    lastGoodDistAt = millis();
  }
  currentDist = (lastGoodDist > 0 && millis() - lastGoodDistAt < DIST_HOLD_MS) ? lastGoodDist : -1;
  if (currentDist > LOW_LEVEL_CM) lowFood = true;
  else if (currentDist > 0 && currentDist < LOW_LEVEL_CM - LOW_HYSTERESIS_CM) lowFood = false;
}

int foodLevelPercent() {
  if (currentDist <= 0) return -1; // không đo được -> web hiện "Không rõ"
  float p = (EMPTY_DIST_CM - currentDist) * 100.0 / (EMPTY_DIST_CM - FULL_DIST_CM);
  return (int)constrain(p, 0, 100);
}

bool updateSensor(int raw) {
  bool now = (raw == LOW) == SENSOR_ACTIVE_LOW;
  if (now == petPresent) {
    sameCount = 0;
    return false;
  }
  if (++sameCount >= STABLE_COUNT) {
    petPresent = now;
    sameCount = 0;
    return true;
  }
  return false;
}

void updateLeds() {
  digitalWrite(LED_RED, lowFood ? HIGH : LOW);
  digitalWrite(LED_GREEN, lowFood ? LOW : HIGH);
}

void showLcd() {
  if (isFeedingNow()) {
    lcdLine(0, "Dang cho an...");
    lcdLine(1, String(max(0.0f, weight - job.startW), 0) + "/" + String(job.gram, 0) + "g");
    return;
  }
  String l0 = String(!autoMode ? "THU CONG " : sensorFeed ? "TU DONG " : "CAMERA ") + String(TARGET_GRAM, 0) + "g";
  l0 += mqtt.connected() ? " *" : " -";
  String l1 = "Khay:" + String(weight, 0) + "g " + (lowFood ? "HET HAT" : "Hat OK");
  lcdLine(0, l0);
  lcdLine(1, l1);
}

float clampGram(float g) { return constrain(g, MIN_GRAM, MAX_GRAM); }

// =============================================================================
// 6. MQTT: GỬI DỮ LIỆU
// =============================================================================
void publishTelemetry() {
  telemetryDue = false;
  lastTelemetry = millis();
  if (!mqtt.connected()) return;
  JsonDocument doc;
  doc["weight"] = round(weight * 10) / 10.0;
  doc["target"] = TARGET_GRAM;
  doc["dist"] = round(currentDist * 10) / 10.0;
  doc["foodLevelPercent"] = foodLevelPercent();
  doc["lowFood"] = lowFood;
  doc["petPresent"] = petPresent;
  doc["autoMode"] = autoMode;
  doc["sensorFeed"] = sensorFeed;
  doc["isFeeding"] = isFeedingNow();
  doc["feedCount"] = feedCount;
  doc["ip"] = WiFi.localIP().toString();
  doc["rssi"] = WiFi.RSSI();
  doc["ssid"] = WiFi.SSID();
  doc["msg"] = statusMsg;
  doc["scaleOk"] = scaleOk;
  char buf[640];
  serializeJson(doc, buf, sizeof(buf));
  mqtt.publish(tTelemetry.c_str(), buf);
}

void publishFeedLog(float added, float finalW, const char *source, const char *result) {
  if (!mqtt.connected()) return;
  JsonDocument doc;
  doc["source"] = source;
  doc["addedGram"] = round(added * 10) / 10.0;
  doc["finalWeight"] = round(finalW * 10) / 10.0;
  doc["feedCount"] = feedCount;
  doc["result"] = result;
  char buf[256];
  serializeJson(doc, buf, sizeof(buf));
  mqtt.publish(tLog.c_str(), buf);
}

// =============================================================================
// 7. CHO ĂN (state machine: IDLE -> RUNNING -> SETTLING -> IDLE)
// =============================================================================
// Bắt đầu 1 lượt nhả hạt. Trả về false nếu máy đang bận.
bool startFeed(const String &source, float gram) {
  if (isFeedingNow()) return false;
  job.gram = clampGram(gram);
  job.source = source;
  job.startW = readWeight(3);
  job.goal = job.startW + job.gram - OVERSHOOT_GRAM;
  job.steps = 0;
  job.result = "";
  job.startedAt = millis();
  job.phase = FEED_RUNNING;
  stopRequested = false;
  feedCount++;

  logMsg("Dang cho an " + String(job.gram, 0) + "g (" + source + ")");
  showLcd();
  digitalWrite(EN_PIN, LOW);
  digitalWrite(DIR_PIN, HIGH);
  delay(15);
  publishTelemetry(); // báo web "đang nhả hạt" ngay lập tức
  return true;
}

// Tắt động cơ, chờ hạt rơi hết rồi mới cân lần cuối (ở pha SETTLING)
void stopMotor(const char *result) {
  digitalWrite(EN_PIN, HIGH);
  job.result = result;
  job.settleAt = millis() + SETTLE_MS;
  job.phase = FEED_SETTLING;
}

// Gọi mỗi vòng loop(). Không chặn lâu: tối đa 1 đoạn CHUNK_STEPS xung + 1 lần đọc cân.
void runFeed() {
  if (job.phase == FEED_RUNNING) {
    bool stopNow = stopRequested;
    for (int i = 0; i < CHUNK_STEPS && !stopNow; i++) {
      long pos = min((long)RAMP_STEPS, job.steps);
      int d = DELAY_START_US - (long)(DELAY_START_US - DELAY_MIN_US) * pos / RAMP_STEPS;
      digitalWrite(STEP_PIN, HIGH);
      delayMicroseconds(d);
      digitalWrite(STEP_PIN, LOW);
      delayMicroseconds(d);
      job.steps++;
      if (digitalRead(BTN_STOP) == LOW) stopNow = true;
      if (i % MQTT_POLL_STEPS == MQTT_POLL_STEPS - 1) {
        mqtt.loop(); // an toàn: callback chỉ đặt cờ, không gọi lại runFeed()
        yield();
        if (stopRequested) stopNow = true;
      }
    }

    if (stopNow) {
      stopMotor("STOPPED");
    } else if (readWeight(1) >= job.goal) {
      stopMotor("DONE");
    } else if (millis() - job.startedAt >= FEED_TIMEOUT) {
      stopMotor("TIMEOUT");
    }
    return;
  }

  if (job.phase == FEED_SETTLING && (long)(millis() - job.settleAt) >= 0) {
    float endW = readWeight(3);
    float added = max(0.0f, endW - job.startW);
    bool stopped = strcmp(job.result, "STOPPED") == 0;
    bool done = strcmp(job.result, "DONE") == 0;
    logMsg(String(stopped ? "Da dung" : (done ? "Xong" : "Het thoi gian")) + " +" + String(added, 0) + "g");
    lcdLine(0, "Xong! +" + String(added, 0) + "g");

    job.phase = FEED_IDLE;
    stopRequested = false;
    publishFeedLog(added, endW, job.source.c_str(), job.result);
    publishTelemetry();
  }
}

// =============================================================================
// 8. MQTT: NHẬN LỆNH (chỉ ghi nhận, KHÔNG quay động cơ ở đây)
// =============================================================================
void onMqttMessage(char *topic, byte *payload, unsigned int length) {
  JsonDocument doc;
  if (deserializeJson(doc, payload, length)) {
    Serial.println("[MQTT] JSON loi, bo qua");
    return;
  }
  String action = doc["action"] | "";
  action.toUpperCase();
  Serial.println("[MQTT] Lenh: " + action);

  if (action == "STOP") {
    pendingFeed = false; // huỷ lệnh cho ăn đang chờ
    if (isFeedingNow()) stopRequested = true; // không làm lượt sau bị dừng ngay
    logMsg("Nhan lenh DUNG");
  } else if (action == "FEED") {
    if (isFeedingNow() || pendingFeed || (lastFeedCmdAt != 0 && millis() - lastFeedCmdAt < FEED_CMD_DEDUP_MS)) {
      Serial.println("[MQTT] Bo qua FEED: may dang ban hoac vua nhan lenh");
      return;
    }
    lastFeedCmdAt = millis();
    pendingFeedGram = doc["gram"].is<float>() ? doc["gram"].as<float>() : TARGET_GRAM;
    pendingFeedSource = doc["source"] | "Tu xa";
    pendingFeed = true;
  } else if (action == "TARE") {
    if (!isFeedingNow()) pendingTare = true;
  } else if (action == "SET_TARGET") {
    if (doc["gram"].is<float>()) TARGET_GRAM = clampGram(doc["gram"].as<float>());
    logMsg("Khau phan " + String(TARGET_GRAM, 0) + "g");
  } else if (action == "AUTO") {
    autoMode = doc["enabled"] | !autoMode;
    sensorFeed = doc["sensor"] | true; // backend cũ không gửi "sensor" -> giữ cách cũ
    logMsg(!autoMode ? "Chuyen THU CONG" : sensorFeed ? "Bat TU DONG" : "TU DONG theo camera");
  } else {
    Serial.println("[MQTT] Lenh khong ro: " + action);
    return;
  }
  telemetryDue = true; // loop() gửi trạng thái mới, không publish trong callback
}

void mqttReconnect() {
  static unsigned long lastTry = 0;
  if (WiFi.status() != WL_CONNECTED || mqtt.connected() || millis() - lastTry < 5000) return;
  lastTry = millis();

  String clientId = "AuraPet_ESP32_" + WiFi.macAddress();
  clientId.replace(":", "");
  const char *user = strlen(MQTT_USER) > 0 ? MQTT_USER : nullptr;
  const char *pass = strlen(MQTT_PASS) > 0 ? MQTT_PASS : nullptr;

  Serial.printf("[MQTT] Ket noi %s:%d ... ", MQTT_HOST, MQTT_PORT);
  // Last Will: nếu ESP32 mất điện/mất mạng, broker tự báo "offline"
  if (mqtt.connect(clientId.c_str(), user, pass, tStatus.c_str(), 1, true, "offline")) {
    Serial.println("OK");
    mqtt.subscribe(tCommand.c_str(), 1);
    mqtt.publish(tStatus.c_str(), "online", true);
    logMsg("Da ket noi may chu");
    publishTelemetry(); // backend sẽ gửi lại khẩu phần + chế độ tự động
  } else {
    Serial.printf("loi rc=%d\n", mqtt.state());
  }
}

// ---- Chế độ cài Wi-Fi ----
#ifndef SETUP_AP_NAME
#define SETUP_AP_NAME "AuraPet-Setup"
#endif
#ifndef SETUP_AP_PASS
#define SETUP_AP_PASS "aurapet123" // tối thiểu 8 ký tự
#endif
const unsigned long PORTAL_TIMEOUT_S = 180; // hết 3 phút không cài -> chạy offline

void onPortalStarted(WiFiManager *wm) {
  logMsg(String("Mo che do cai WiFi: ") + SETUP_AP_NAME);
  lcdLine(0, "Cai WiFi: ket noi");
  lcdLine(1, SETUP_AP_NAME);
  digitalWrite(LED_RED, HIGH);
  digitalWrite(LED_GREEN, HIGH);
}

bool waitWifi(unsigned long ms) {
  unsigned long t0 = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - t0 < ms) {
    delay(300);
    Serial.print(".");
  }
  Serial.println();
  return WiFi.status() == WL_CONNECTED;
}

// forcePortal = true khi người dùng giữ nút TRỪ BÌ lúc khởi động
void setupWifi(bool forcePortal) {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);

  WiFiManager wm;
  wm.setTitle("AuraPet - Cai dat WiFi");
  wm.setConnectTimeout(15);
  wm.setConfigPortalTimeout(PORTAL_TIMEOUT_S);
  wm.setAPCallback(onPortalStarted);
  wm.setDebugOutput(false);

  bool ok = false;
  if (forcePortal) {
    ok = wm.startConfigPortal(SETUP_AP_NAME, SETUP_AP_PASS);
  } else if (!wm.getWiFiIsSaved() && strlen(WIFI_SSID) > 0) {
    // Lần đầu: thử Wi-Fi ghi trong secrets.h (sẽ được lưu lại cho lần sau)
    lcdLine(0, "Ket noi WiFi...");
    lcdLine(1, WIFI_SSID);
    WiFi.begin(WIFI_SSID, WIFI_PASS);
    ok = waitWifi(15000) || wm.autoConnect(SETUP_AP_NAME, SETUP_AP_PASS);
  } else {
    lcdLine(0, "Ket noi WiFi...");
    lcdLine(1, wm.getWiFiSSID());
    ok = wm.autoConnect(SETUP_AP_NAME, SETUP_AP_PASS); // tự mở portal nếu mạng đã lưu không vào được
  }

  if (ok && WiFi.status() == WL_CONNECTED) {
    logMsg("WiFi OK (" + WiFi.SSID() + "), IP " + WiFi.localIP().toString());
    lcdLine(0, "WiFi: " + WiFi.SSID());
    lcdLine(1, WiFi.localIP().toString());
  } else {
    logMsg("Chua co WiFi - van chay offline. Giu nut TRU BI + RESET de cai lai");
    lcdLine(0, "Loi WiFi!");
    lcdLine(1, "Giu TARE+RESET");
  }
  digitalWrite(LED_RED, LOW);
  delay(800);
}

// =============================================================================
// 9. SETUP & LOOP
// =============================================================================
void setup() {
  Serial.begin(115200);
  delay(300);
  Serial.println("\n=== AURAPET ESP32 FEEDER v2.1 ===");

  pinMode(TRIG, OUTPUT);
  pinMode(ECHO, INPUT);
  pinMode(SENSOR_PIN, INPUT);
  pinMode(BTN_FEED, INPUT_PULLUP);
  pinMode(BTN_STOP, INPUT_PULLUP);
  pinMode(BTN_AUTO, INPUT_PULLUP);
  pinMode(BTN_TARE, INPUT_PULLUP);
  pinMode(LED_RED, OUTPUT);
  pinMode(LED_GREEN, OUTPUT);
  pinMode(STEP_PIN, OUTPUT);
  pinMode(DIR_PIN, OUTPUT);
  pinMode(EN_PIN, OUTPUT);
  digitalWrite(EN_PIN, HIGH);

  Wire.begin(SDA_PIN, SCL_PIN);
  setupLcd();

  String p = String(TOPIC_PREFIX);
  while (p.endsWith("/")) p.remove(p.length() - 1);
  tTelemetry = p + "/telemetry";
  tCommand = p + "/command";
  tLog = p + "/log";
  tStatus = p + "/status";

#if MQTT_USE_TLS
#ifdef MQTT_CA_CERT
  netClient.setCACert(MQTT_CA_CERT); // xác thực chứng chỉ broker (khuyên dùng)
#else
  netClient.setInsecure(); // mã hoá nhưng CHƯA xác thực broker; đặt MQTT_CA_CERT trong secrets.h
#endif
#endif
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setCallback(onMqttMessage);
  mqtt.setBufferSize(768);
  mqtt.setKeepAlive(30);

  // Giữ nút TRỪ BÌ khi khởi động ~2 giây -> mở chế độ cài Wi-Fi
  bool forcePortal = false;
  if (digitalRead(BTN_TARE) == LOW) {
    lcdLine(0, "Giu nut 2 giay");
    lcdLine(1, "de cai WiFi...");
    unsigned long t0 = millis();
    while (digitalRead(BTN_TARE) == LOW && millis() - t0 < 2000) delay(20);
    forcePortal = (millis() - t0 >= 2000);
    while (digitalRead(BTN_TARE) == LOW) delay(20); // chờ nhả nút để không trừ bì nhầm
  }
  setupWifi(forcePortal);

  if (MOTOR_TEST_ON_BOOT) {
    digitalWrite(EN_PIN, LOW);
    for (int i = 0; i < STEPS_PER_REV; i++) {
      digitalWrite(STEP_PIN, HIGH);
      delayMicroseconds(400);
      digitalWrite(STEP_PIN, LOW);
      delayMicroseconds(400);
    }
    digitalWrite(EN_PIN, HIGH);
  }

  scale.begin(HX_DT, HX_SCK);
  scale.set_scale(CALIB_FACTOR);
  doTare();

  petPresent = ((digitalRead(SENSOR_PIN) == LOW) == SENSOR_ACTIVE_LOW);
  logMsg("San sang");
}

void loop() {
  // 1. Kết nối + nhận lệnh (callback chỉ đặt cờ)
  if (WiFi.status() == WL_CONNECTED) {
    if (!mqtt.connected()) mqttReconnect();
    else mqtt.loop();
  }

  // 2. Lượt nhả hạt đang chạy: quay 1 đoạn rồi trả quyền cho phần còn lại của loop()
  runFeed();
  bool feeding = isFeedingNow();

  // 3. Cảm biến (siêu âm/cân làm động cơ khựng lại, nên bỏ qua khi đang nhả hạt)
  if (!feeding) {
    updateFoodLevel();
    readWeight(1);
  }

  // 4. Lệnh từ web/app đã ghi nhận
  if (pendingTare && !feeding) {
    pendingTare = false;
    doTare();
    telemetryDue = true;
  }
  if (pendingFeed && !feeding) {
    pendingFeed = false;
    startFeed(pendingFeedSource, pendingFeedGram);
    feeding = true;
  }

  // 5. Nút bấm vật lý (nút DỪNG được xử lý trong runFeed)
  if (pressed(BTN_AUTO)) {
    autoMode = !autoMode; // backend đọc autoMode trong telemetry và cập nhật web
    logMsg(autoMode ? "Nut: Bat TU DONG" : "Nut: THU CONG");
    telemetryDue = true;
  }
  if (!feeding && pressed(BTN_TARE)) {
    doTare();
    telemetryDue = true;
  }
  if (!feeding && pressed(BTN_FEED)) {
    startFeed("Nut tren may", TARGET_GRAM);
    feeding = true;
  }

  // 6. Mèo đến / đi
  if (updateSensor(digitalRead(SENSOR_PIN))) {
    logMsg(petPresent ? "Co meo den" : "Meo da di");
    telemetryDue = true;
  }

  // 7. Tự động cho ăn theo cảm biến: có mèo + khay < 10g + hết thời gian chờ
  //    (khi camera chạy, sensorFeed = false: backend tự gửi lệnh theo bé camera nhận ra)
  if (!feeding && autoMode && sensorFeed && petPresent && weight < EMPTY_BOWL_GRAM
      && (lastAuto == 0 || millis() - lastAuto > AUTO_COOLDOWN)) {
    lastAuto = millis();
    startFeed("Tu dong (cam bien)", TARGET_GRAM);
    feeding = true;
  }

  // 8. Hiển thị
  updateLeds();
  if (millis() - lastLcd > 500) {
    lastLcd = millis();
    showLcd();
  }
  if (millis() - lastPrint > 2000) {
    lastPrint = millis();
    Serial.printf("Khay %.1fg | Thung %d%% | Meo %s | %s | %s | MQTT %s\n", weight, foodLevelPercent(),
                  petPresent ? "CO" : "KHONG", autoMode ? "AUTO" : "TAY", feeding ? "DANG NHA" : "-",
                  mqtt.connected() ? "OK" : "--");
  }

  // 9. Telemetry: ngay khi có thay đổi, hoặc định kỳ (dày hơn khi đang nhả hạt)
  unsigned long interval = feeding ? FEED_TELEMETRY_INTERVAL : TELEMETRY_INTERVAL;
  if (telemetryDue || millis() - lastTelemetry >= interval) publishTelemetry();

  if (!feeding) delay(15); // đang nhả hạt thì quay tiếp ngay, không nghỉ
}
