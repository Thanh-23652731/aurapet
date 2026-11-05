/*
 * ==============================================================================
 * AURAPET AIoT - ESP32 SMART PET FEEDER (EMQX MQTT, KẾT NỐI WEB + APP AURAPET)
 * ==============================================================================
 * Luồng điều khiển:
 *   Web / App  --REST-->  Backend AuraPet  --MQTT-->  broker  --MQTT-->  ESP32
 *   ESP32 gửi trạng thái 2 giây/lần (cả khi đang nhả hạt) để web biết máy online.
 *
 * Topic (PREFIX đặt trong secrets.h, PHẢI giống MQTT_TOPIC_PREFIX trong .env):
 *   PREFIX/telemetry  ESP32 -> server  dữ liệu cảm biến
 *   PREFIX/log        ESP32 -> server  báo cáo sau mỗi lần nhả hạt
 *   PREFIX/status     ESP32 -> server  "online" / "offline" (retained + Last Will)
 *   PREFIX/command    server -> ESP32  lệnh điều khiển (JSON)
 *
 * Lệnh nhận được (backend gửi):
 *   {"action":"FEED","gram":30,"source":"Web"}   cho ăn ngay (gram chỉ dùng cho lần này)
 *   {"action":"STOP"}                            dừng động cơ
 *   {"action":"TARE"}                            trừ bì cân về 0 g
 *   {"action":"SET_TARGET","gram":40}            đổi khẩu phần mặc định
 *   {"action":"AUTO","enabled":true}             bật/tắt tự cho ăn khi mèo đến
 *   {"action":"PUMP",...} / {"action":"PUMP_AUTO",...}  bỏ qua (máy chưa lắp bơm)
 *
 * Giữ nguyên phần cứng: động cơ bước có gia tốc, cân HX711, siêu âm,
 * cảm biến mèo, 4 nút bấm, LCD I2C, LED.
 *
 * Thư viện (Arduino IDE > Library Manager): PubSubClient (Nick O'Leary),
 * HX711 (Bogdan Necula), LiquidCrystal I2C (Frank de Brabander).
 * ==============================================================================
 */

#include "HX711.h"
#include "secrets.h" // Wi-Fi, broker, TOPIC_PREFIX (copy từ secrets.example.h)
#include <LiquidCrystal_I2C.h>
#include <PubSubClient.h>
#include <WiFi.h>
#include <WiFiClient.h>
#include <Wire.h>

// ==============================================================================
// 1. MQTT
// ==============================================================================
String TOPIC_TELEMETRY, TOPIC_COMMAND, TOPIC_LOG, TOPIC_STATUS;

const unsigned long TELEMETRY_INTERVAL = 2000;      // backend coi là offline nếu > 10 s không có tin
const unsigned long FEED_TELEMETRY_INTERVAL = 1000; // khi đang nhả hạt gửi dày hơn để web cập nhật
unsigned long lastTelemetryTime = 0;

WiFiClient espClient;
PubSubClient mqttClient(espClient);

// ==============================================================================
// 2. CHÂN PHẦN CỨNG (GIỮ NGUYÊN SƠ ĐỒ DÂY)
// ==============================================================================
#define TRIG 5
#define ECHO 18
#define SENSOR_PIN 19
#define BTN_FEED 32 // Nút cứng CHO ĂN
#define BTN_STOP 33 // Nút cứng DỪNG KHẨN CẤP
#define BTN_AUTO 25 // Nút cứng TỰ ĐỘNG
#define BTN_TARE 26 // Nút cứng TARE (Về 0 cân)
#define LED_RED 27
#define LED_GREEN 14
#define STEP_PIN 13
#define DIR_PIN 23
#define EN_PIN 17
#define HX_DT 16
#define HX_SCK 4
#define SDA_PIN 21
#define SCL_PIN 22

// ==============================================================================
// 3. THÔNG SỐ VẬN HÀNH & HIỆU CHỈNH CÂN NẶNG / MOTOR
// ==============================================================================
LiquidCrystal_I2C *lcd = nullptr;
bool lcdOk = false;
HX711 scale;

float CALIB_FACTOR = 420.0;       // Hệ số hiệu chuẩn cân HX711
float TARGET_GRAM = 30.0;         // Khẩu phần mặc định (web đồng bộ lại khi máy kết nối)
const float OVERSHOOT_GRAM = 2.0; // Dừng sớm một chút trừ hao quán tính hạt rơi
const float MIN_GRAM = 5.0, MAX_GRAM = 150.0; // giống giới hạn trên web

const bool MOTOR_TEST_ON_BOOT = true; // Quay thử motor 1 vòng khi khởi động (sẽ rơi hạt)

// TMC2209 / A4988: 1/16 bước (3200 xung / vòng)
const int STEPS_PER_REV = 3200;
const int DELAY_START_US = 500; // Tốc độ ban đầu (chậm)
const int DELAY_MIN_US = 250;   // Tốc độ tối đa khi đạt gia tốc
const int RAMP_STEPS = 960;     // Số xung tăng tốc
const int CHUNK_STEPS = 1600;   // Số xung giữa 2 lần đọc cân
const unsigned long FEED_TIMEOUT = 20000;

const float LOW_LEVEL_CM = 15.0;     // Khoảng cách > 15cm: Cảnh báo sắp hết hạt
const bool SENSOR_ACTIVE_LOW = true; // OUT = 0 là có mèo
const int STABLE_COUNT = 3;
// Tự cho ăn khi mèo đến: chỉ khi khay gần hết và cách lần trước ít nhất 1 phút
// (đúng như mô tả trên web: "Chỉ nhả khi khay dưới 10 g, cách nhau ít nhất 1 phút")
const float EMPTY_BOWL_GRAM = 10.0;
const unsigned long AUTO_COOLDOWN = 60000;

// ==============================================================================
// 4. BIẾN TRẠNG THÁI HỆ THỐNG
// ==============================================================================
bool autoMode = false;
unsigned long lastAuto = 0;
unsigned long lastPrint = 0;
unsigned long lastLcd = 0;

int feedCount = 0;
bool petPresent = false;
int sameCount = 0;
float weight = 0.0;
float currentDist = -1.0;          // khoảng cách hợp lệ gần nhất (-1 = không đo được)
float lastGoodDist = -1.0;
unsigned long lastGoodDistAt = 0;
bool lowFood = false;              // có độ trễ để không nhấp nháy quanh ngưỡng
const float LOW_HYSTERESIS_CM = 2.0;
const unsigned long DIST_HOLD_MS = 5000; // siêu âm mất tiếng vọng thoáng qua thì giữ số cũ
bool scaleOk = false;
bool isFeedingNow = false;
bool mqttStopRequested = false;
String lastStatusMsg = "San sang";

// Lệnh nhận từ MQTT được xếp hàng và chạy trong loop(), không chạy trong callback
bool pendingFeed = false;
float pendingFeedGram = 0;
String pendingFeedSource = "";
bool pendingTare = false;
unsigned long lastFeedCommandTime = 0;

// ==============================================================================
// 5. CÁC HÀM TIỆN ÍCH PHẦN CỨNG (LCD, CÂN, CẢM BIẾN, MOTOR)
// ==============================================================================
void logMsg(const char *msg) {
  lastStatusMsg = String(msg);
  Serial.print("[");
  Serial.print(millis() / 1000.0, 1);
  Serial.print("s] ");
  Serial.println(msg);
}

bool i2cExists(byte addr) {
  Wire.beginTransmission(addr);
  return Wire.endTransmission() == 0;
}

void lcdLine(int row, String s) {
  if (!lcdOk)
    return;
  while (s.length() < 16)
    s += ' ';
  lcd->setCursor(0, row);
  lcd->print(s.substring(0, 16));
}

void setupLcd() {
  byte addr = 0;
  if (i2cExists(0x27))
    addr = 0x27;
  else if (i2cExists(0x3F))
    addr = 0x3F;

  if (addr == 0) {
    logMsg("LCD: KHONG TIM THAY (Kiem tra SDA:21, SCL:22)");
    return;
  }
  lcd = new LiquidCrystal_I2C(addr, 16, 2);
  lcd->init();
  lcd->backlight();
  lcd->clear();
  lcdOk = true;
  lcdLine(0, "AuraPet Feeder");
  lcdLine(1, "Khoi dong...");
}

bool pressed(int pin) {
  if (digitalRead(pin) == LOW) {
    delay(30);
    if (digitalRead(pin) == LOW) {
      while (digitalRead(pin) == LOW)
        delay(5);
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
  if (d == 0)
    return -1;
  return d * 0.0343 / 2.0;
}

float readWeight(int times) {
  if (!scaleOk || !scale.is_ready())
    return weight;
  weight = scale.get_units(times);
  return weight;
}

void doTare() {
  if (scale.wait_ready_timeout(1000)) {
    scale.tare();
    scaleOk = true;
    weight = 0.0;
    logMsg("TARE: Can da ve 0g");
    lcdLine(0, "Da TARE ve 0g");
  } else {
    scaleOk = false;
    logMsg("HX711 KHONG PHAN HOI (Kiem tra DT:16, SCK:4)");
    lcdLine(0, "Loi HX711");
  }
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

void motorTest() {
  logMsg("TEST MOTOR: Quay 1 vong kiem tra...");
  lcdLine(0, "Test motor...");
  digitalWrite(EN_PIN, LOW);
  digitalWrite(DIR_PIN, HIGH);
  delay(20);
  for (int i = 0; i < STEPS_PER_REV; i++) {
    int d = 700 - (long)(700 - 350) * min(i, 800) / 800;
    digitalWrite(STEP_PIN, HIGH);
    delayMicroseconds(d);
    digitalWrite(STEP_PIN, LOW);
    delayMicroseconds(d);
    if (i % 400 == 0) {
      mqttClient.loop();
      yield();
    }
  }
  digitalWrite(EN_PIN, HIGH);
  logMsg("TEST MOTOR xong.");
}

// Đọc siêu âm, giữ số hợp lệ gần nhất và tính "sắp hết hạt" có độ trễ
void updateFoodLevel() {
  float d = readDistanceCm();
  if (d > 0) {
    lastGoodDist = d;
    lastGoodDistAt = millis();
  }
  currentDist = (lastGoodDist > 0 && millis() - lastGoodDistAt < DIST_HOLD_MS) ? lastGoodDist : -1;
  if (currentDist > LOW_LEVEL_CM)
    lowFood = true;
  else if (currentDist > 0 && currentDist < LOW_LEVEL_CM - LOW_HYSTERESIS_CM)
    lowFood = false;
}

void updateLeds() {
  digitalWrite(LED_RED, lowFood ? HIGH : LOW);
  digitalWrite(LED_GREEN, lowFood ? LOW : HIGH);
}

void showLcd() {
  String l0 = String(autoMode ? "TU DONG " : "THU CONG ") +
              "M:" + String(TARGET_GRAM, 0) + "g";
  String l1 = "Can:" + String(weight, 0) + "g ";
  l1 += lowFood ? "HET HAT" : "Hat:OK";
  lcdLine(0, l0);
  lcdLine(1, l1);
}

// ==============================================================================
// 6. ĐỌC JSON ĐƠN GIẢN (không cần thư viện ArduinoJson)
// ==============================================================================
// Trả về vị trí ngay sau dấu ':' của "key", hoặc -1 nếu không có
int jsonValueStart(const String &msg, const char *key) {
  String k = String("\"") + key + "\"";
  int i = msg.indexOf(k);
  if (i < 0)
    return -1;
  i = msg.indexOf(':', i + k.length());
  if (i < 0)
    return -1;
  i++;
  while (i < (int)msg.length() && msg[i] == ' ')
    i++;
  return i;
}

bool jsonNumber(const String &msg, const char *key, float &out) {
  int i = jsonValueStart(msg, key);
  if (i < 0)
    return false;
  int j = i;
  while (j < (int)msg.length() &&
         (isDigit(msg[j]) || msg[j] == '.' || msg[j] == '-'))
    j++;
  if (j == i)
    return false;
  out = msg.substring(i, j).toFloat();
  return true;
}

bool jsonBool(const String &msg, const char *key, bool &out) {
  int i = jsonValueStart(msg, key);
  if (i < 0)
    return false;
  if (msg.startsWith("true", i)) {
    out = true;
    return true;
  }
  if (msg.startsWith("false", i)) {
    out = false;
    return true;
  }
  return false;
}

String jsonString(const String &msg, const char *key) {
  int i = jsonValueStart(msg, key);
  if (i < 0 || i >= (int)msg.length() || msg[i] != '"')
    return "";
  String out = "";
  for (int j = i + 1; j < (int)msg.length(); j++) {
    char c = msg[j];
    if (c == '\\' && j + 1 < (int)msg.length()) {
      out += msg[++j];
    } else if (c == '"') {
      break;
    } else {
      out += c;
    }
  }
  return out;
}

// Thoát ký tự đặc biệt khi ghi chuỗi vào JSON
String jsonEscape(const String &s) {
  String out = "";
  for (unsigned int i = 0; i < s.length(); i++) {
    char c = s[i];
    if (c == '"' || c == '\\') {
      out += '\\';
      out += c;
    } else if ((uint8_t)c < 0x20) {
      out += ' ';
    } else {
      out += c;
    }
  }
  return out;
}

// ==============================================================================
// 7. GỬI DỮ LIỆU LÊN BACKEND QUA MQTT
// ==============================================================================
// Trạng thái định kỳ -> PREFIX/telemetry (tên trường khớp DeviceService của backend)
void publishTelemetryToMqtt() {
  if (!mqttClient.connected())
    return;

  // Không đo được (siêu âm không phản hồi) thì gửi -1 để web hiện "Không rõ",
  // không gửi số giả.
  int foodPercent = -1;
  if (currentDist > 0) {
    foodPercent = (int)constrain(map((long)currentDist, 3, 18, 100, 0), 0, 100);
  }

  String json = "{";
  json += "\"weight\":" + String(weight, 1) + ",";
  json += "\"target\":" + String(TARGET_GRAM, 1) + ",";
  json += "\"dist\":" + String(currentDist, 1) + ",";
  json += "\"foodLevelPercent\":" + String(foodPercent) + ",";
  json += "\"lowFood\":" + String(lowFood ? "true" : "false") + ",";
  json += "\"petPresent\":" + String(petPresent ? "true" : "false") + ",";
  json += "\"autoMode\":" + String(autoMode ? "true" : "false") + ",";
  json += "\"isFeeding\":" + String(isFeedingNow ? "true" : "false") + ",";
  json += "\"feedCount\":" + String(feedCount) + ",";
  json += "\"scaleOk\":" + String(scaleOk ? "true" : "false") + ",";
  json += "\"pumpSupported\":false,\"pumpOn\":false,"; // máy này chưa lắp bơm
  json += "\"ip\":\"" + WiFi.localIP().toString() + "\",";
  json += "\"rssi\":" + String(WiFi.RSSI()) + ",";
  json += "\"msg\":\"" + jsonEscape(lastStatusMsg) + "\"";
  json += "}";

  mqttClient.publish(TOPIC_TELEMETRY.c_str(), json.c_str());
}

// Báo cáo hoàn tất lượt cho ăn -> PREFIX/log (backend ghi vào lịch sử cho ăn)
// result: REACHED (đủ gram) | STOPPED (bị dừng) | TIMEOUT (hết thời gian)
void reportFeedingDoneToMqtt(float addedGram, float finalWeight,
                             const String &source, const char *result) {
  if (!mqttClient.connected())
    return;

  String json = "{";
  json += "\"source\":\"" + jsonEscape(source) + "\",";
  json += "\"result\":\"" + String(result) + "\",";
  json += "\"addedGram\":" + String(addedGram, 1) + ",";
  json += "\"finalWeight\":" + String(finalWeight, 1) + ",";
  json += "\"feedCount\":" + String(feedCount);
  json += "}";

  mqttClient.publish(TOPIC_LOG.c_str(), json.c_str());
  Serial.println("[MQTT] Da gui nhat ky cho an: " + json);
}

// ==============================================================================
// 8. CHO ĂN
// ==============================================================================
void feed(const String &source, float gram) {
  if (isFeedingNow)
    return;

  gram = constrain(gram, MIN_GRAM, MAX_GRAM);
  isFeedingNow = true;
  mqttStopRequested = false;
  feedCount++;
  float startW = readWeight(3);
  float goal = startW + gram - OVERSHOOT_GRAM;

  Serial.println("\n-------------------------------------------");
  Serial.print(">>> CHO AN lan #");
  Serial.print(feedCount);
  Serial.print(" | Nguon: ");
  Serial.println(source);
  Serial.print("  Can ban dau: ");
  Serial.print(startW, 1);
  Serial.print("g | Muc tieu: ");
  Serial.print(gram, 0);
  Serial.println("g");
  Serial.println("-------------------------------------------");

  lastStatusMsg = "Dang cho an " + String(gram, 0) + "g";
  lcdLine(0, "Dang cho an...");
  lcdLine(1, "Moc: " + String(gram, 0) + "g");
  publishTelemetryToMqtt(); // báo web ngay: "Đang nhả hạt"
  lastTelemetryTime = millis();

  digitalWrite(EN_PIN, LOW);
  digitalWrite(DIR_PIN, HIGH);
  delay(15);

  unsigned long t0 = millis();
  long stepCount = 0;
  bool stopped = false, reached = false;

  while ((millis() - t0 < FEED_TIMEOUT) && !reached && !stopped) {
    for (int i = 0; i < CHUNK_STEPS; i++) {
      long pos = min((long)RAMP_STEPS, stepCount);
      int d = DELAY_START_US -
              (long)(DELAY_START_US - DELAY_MIN_US) * pos / RAMP_STEPS;
      digitalWrite(STEP_PIN, HIGH);
      delayMicroseconds(d);
      digitalWrite(STEP_PIN, LOW);
      delayMicroseconds(d);
      stepCount++;

      // Nhận lệnh (vd. STOP) và giữ kết nối MQTT trong lúc motor quay
      if (i % 400 == 0) {
        mqttClient.loop();
        yield();
        if (millis() - lastTelemetryTime >= FEED_TELEMETRY_INTERVAL) {
          lastTelemetryTime = millis();
          publishTelemetryToMqtt();
        }
      }

      // Dừng bằng nút bấm hoặc lệnh STOP từ web/app
      if (digitalRead(BTN_STOP) == LOW || mqttStopRequested) {
        while (digitalRead(BTN_STOP) == LOW)
          delay(5);
        mqttStopRequested = false;
        stopped = true;
        break;
      }
    }

    mqttClient.loop();
    yield();

    float w = readWeight(1);
    if (w >= goal) {
      reached = true;
      break;
    }
  }

  digitalWrite(EN_PIN, HIGH);
  delay(250);
  float endW = readWeight(3);
  float actualAdded = max(0.0f, endW - startW);

  const char *result = stopped ? "STOPPED" : reached ? "REACHED" : "TIMEOUT";
  if (stopped)
    logMsg("Da DUNG motor");
  else if (reached)
    logMsg("Dat moc gram! Da tat motor");
  else
    logMsg("Het thoi gian toi da (Timeout)");

  lcdLine(0, "Xong! +" + String(actualAdded, 0) + "g");
  delay(600);

  isFeedingNow = false;
  reportFeedingDoneToMqtt(actualAdded, endW, source, result);
  publishTelemetryToMqtt(); // báo web: đã xong
  lastTelemetryTime = millis();
}

// ==============================================================================
// 9. NHẬN LỆNH TỪ WEB / APP (qua backend -> broker -> PREFIX/command)
// ==============================================================================
void mqttCallback(char *topic, byte *payload, unsigned int length) {
  String message = "";
  message.reserve(length);
  for (unsigned int i = 0; i < length; i++) {
    message += (char)payload[i];
  }

  Serial.println("\n[MQTT] " + String(topic) + " : " + message);
  String action = jsonString(message, "action");
  action.toUpperCase();
  float g;
  bool b;

  if (action == "FEED") {
    // Bỏ qua lệnh trùng (máy đang nhả hoặc vừa nhận lệnh < 3 giây)
    if (isFeedingNow || pendingFeed || millis() - lastFeedCommandTime < 3000) {
      Serial.println("[MQTT] Bo qua FEED: may dang ban hoac vua nhan lenh.");
      return;
    }
    lastFeedCommandTime = millis();
    pendingFeedGram = jsonNumber(message, "gram", g) ? g : TARGET_GRAM;
    pendingFeedSource = jsonString(message, "source");
    if (pendingFeedSource.length() == 0)
      pendingFeedSource = "Web";
    pendingFeed = true; // loop() sẽ chạy động cơ
    logMsg("Nhan lenh CHO AN tu web/app");
  } else if (action == "STOP") {
    // Chỉ có tác dụng khi đang nhả, tránh làm lần cho ăn sau bị dừng ngay
    pendingFeed = false;
    if (isFeedingNow)
      mqttStopRequested = true;
    logMsg("Nhan lenh DUNG tu web/app");
  } else if (action == "TARE") {
    if (!isFeedingNow)
      pendingTare = true;
  } else if (action == "SET_TARGET") {
    if (jsonNumber(message, "gram", g) && g >= MIN_GRAM && g <= MAX_GRAM) {
      TARGET_GRAM = g;
      logMsg(("Doi khau phan -> " + String(TARGET_GRAM, 0) + "g").c_str());
      publishTelemetryToMqtt();
    }
  } else if (action == "AUTO") {
    autoMode = jsonBool(message, "enabled", b) ? b : !autoMode;
    logMsg(autoMode ? "Web: Bat TU DONG" : "Web: Chuyen THU CONG");
    publishTelemetryToMqtt();
  } else if (action == "PUMP" || action == "PUMP_AUTO") {
    Serial.println("[MQTT] May nay chua lap bom nuoc - bo qua lenh.");
  } else {
    Serial.println("[MQTT] Lenh khong ro: " + action);
  }
}

// Kết nối (lại) broker, không chặn chương trình quá lâu
void mqttReconnect() {
  if (WiFi.status() != WL_CONNECTED || mqttClient.connected())
    return;

  static unsigned long lastMqttAttempt = 0;
  if (lastMqttAttempt != 0 && millis() - lastMqttAttempt < 5000)
    return; // thử lại mỗi 5 giây
  lastMqttAttempt = millis();

  Serial.print("[MQTT] Dang ket noi ");
  Serial.print(MQTT_HOST);
  Serial.print(":");
  Serial.print(MQTT_PORT);
  Serial.print(" ... ");

  // Client ID duy nhất theo địa chỉ MAC
  String clientId = "ESP32_AuraPet_" + WiFi.macAddress();
  clientId.replace(":", "");

  // Last Will: nếu máy mất điện/mất mạng, broker tự báo "offline" cho backend
  bool hasAuth = strlen(MQTT_USER) > 0;
  bool success = mqttClient.connect(clientId.c_str(),
                                    hasAuth ? MQTT_USER : nullptr,
                                    hasAuth ? MQTT_PASS : nullptr,
                                    TOPIC_STATUS.c_str(), 1, true, "offline");

  if (success) {
    Serial.println("THANH CONG!");
    lcdLine(0, "MQTT: OK!");
    lcdLine(1, "San sang!");
    mqttClient.publish(TOPIC_STATUS.c_str(), "online", true);
    mqttClient.subscribe(TOPIC_COMMAND.c_str(), 1);
    Serial.println("[MQTT] Nghe lenh tai: " + TOPIC_COMMAND);
    publishTelemetryToMqtt(); // backend sẽ gửi lại khẩu phần + chế độ tự động
    lastTelemetryTime = millis();
  } else {
    Serial.print("That bai, ma loi rc=");
    Serial.println(mqttClient.state());
    lcdLine(0, "Loi MQTT rc=");
    lcdLine(1, String(mqttClient.state()));
  }
}

// ==============================================================================
// 10. KHỞI TẠO WI-FI
// ==============================================================================
void setupWifi() {
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASS);

  Serial.println();
  Serial.print("Dang ket noi Wi-Fi '");
  Serial.print(WIFI_SSID);
  Serial.print("'");
  lcdLine(0, "Ket noi WiFi...");
  lcdLine(1, WIFI_SSID);

  unsigned long startAttempt = millis();
  while (WiFi.status() != WL_CONNECTED && millis() - startAttempt < 15000) {
    delay(400);
    Serial.print(".");
  }
  Serial.println();

  if (WiFi.status() == WL_CONNECTED) {
    String ip = WiFi.localIP().toString();
    Serial.println("\n=======================================================");
    Serial.println("  WI-FI KET NOI THANH CONG!");
    Serial.print("  IP ESP32 : ");
    Serial.println(ip);
    Serial.print("  Broker   : ");
    Serial.println(MQTT_HOST);
    Serial.println("=======================================================\n");
    lcdLine(0, "WiFi OK! IP:");
    lcdLine(1, ip);
    delay(1500);
    mqttReconnect();
  } else {
    // Wi-Fi tự kết nối lại ở nền; loop() sẽ nối MQTT khi có mạng
    logMsg("Loi Wi-Fi! Chay che do Offline.");
    lcdLine(0, "Loi WiFi!");
    lcdLine(1, "Chay Offline...");
    delay(1500);
  }
}

// ==============================================================================
// 11. SETUP & LOOP CHÍNH
// ==============================================================================
void setup() {
  Serial.begin(115200);
  delay(400);

  Serial.println("\n=======================================================");
  Serial.println("   AURAPET AIoT - MAY CHO AN (KET NOI WEB + APP)       ");
  Serial.println("=======================================================");

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

  String prefix = TOPIC_PREFIX;
  while (prefix.endsWith("/"))
    prefix.remove(prefix.length() - 1);
  TOPIC_TELEMETRY = prefix + "/telemetry";
  TOPIC_COMMAND = prefix + "/command";
  TOPIC_LOG = prefix + "/log";
  TOPIC_STATUS = prefix + "/status";

  // Cấu hình MQTT luôn được đặt, kể cả khi lúc khởi động chưa có Wi-Fi
  mqttClient.setServer(MQTT_HOST, MQTT_PORT);
  mqttClient.setCallback(mqttCallback);
  mqttClient.setBufferSize(512);
  mqttClient.setKeepAlive(20);

  setupWifi();

  if (MOTOR_TEST_ON_BOOT) {
    motorTest();
  }

  scale.begin(HX_DT, HX_SCK);
  scale.set_scale(CALIB_FACTOR);
  doTare();

  petPresent = ((digitalRead(SENSOR_PIN) == LOW) == SENSOR_ACTIVE_LOW);
  logMsg("He thong san sang!");
}

void loop() {
  // 1. Duy trì kết nối MQTT và nhận lệnh
  if (WiFi.status() == WL_CONNECTED) {
    if (!mqttClient.connected())
      mqttReconnect();
    else
      mqttClient.loop();
  }

  // 2. Đọc cảm biến liên tục
  updateFoodLevel();
  int raw = digitalRead(SENSOR_PIN);
  readWeight(1);

  // 3. Lệnh từ web/app đã xếp hàng
  if (pendingTare) {
    pendingTare = false;
    logMsg("Web: TARE can");
    doTare();
    publishTelemetryToMqtt();
  }
  if (pendingFeed) {
    pendingFeed = false;
    feed(pendingFeedSource, pendingFeedGram);
  }

  // 4. Nút bấm vật lý trên hộp máy
  if (pressed(BTN_AUTO)) {
    autoMode = !autoMode; // backend đọc autoMode trong telemetry và cập nhật web
    logMsg(autoMode ? "Nut cung: Bat TU DONG" : "Nut cung: Chuyen THU CONG");
    publishTelemetryToMqtt();
  }

  if (pressed(BTN_TARE)) {
    doTare();
    publishTelemetryToMqtt();
    delay(400);
  }

  if (pressed(BTN_FEED)) {
    logMsg("Nut vat ly: CHO AN");
    feed("Nút trên máy", TARGET_GRAM);
  }

  // 5. Cảm biến phát hiện mèo đến gần
  if (updateSensor(raw)) {
    Serial.print("[");
    Serial.print(millis() / 1000.0, 1);
    Serial.print("s] Cam bien: ");
    Serial.println(petPresent ? "CO MEO DEN!" : "Meo da roi di");
    publishTelemetryToMqtt();

    if (petPresent) {
      if (!autoMode)
        logMsg("Phat hien meo (Dang o THU CONG)");
      else if (weight >= EMPTY_BOWL_GRAM)
        logMsg("Co meo nhung khay con hat - khong nha them");
      else if (lastAuto != 0 && millis() - lastAuto <= AUTO_COOLDOWN)
        logMsg("Cooldown tu dong chua het (60s)");
    }
  }

  // 6. Tự động cho ăn: chế độ Auto + có mèo + khay gần hết + hết thời gian chờ
  if (autoMode && petPresent && weight < EMPTY_BOWL_GRAM &&
      (lastAuto == 0 || millis() - lastAuto > AUTO_COOLDOWN)) {
    lastAuto = millis();
    feed("Tự động (cảm biến mèo)", TARGET_GRAM);
  }

  // 7. Đèn LED trạng thái
  updateLeds();

  // 8. LCD (mỗi 500ms)
  if (millis() - lastLcd > 500) {
    lastLcd = millis();
    showLcd();
  }

  // 9. Serial Monitor (mỗi 1000ms)
  if (millis() - lastPrint > 1000) {
    lastPrint = millis();
    Serial.print("Can: ");
    Serial.print(weight, 1);
    Serial.print("g | Hat: ");
    Serial.print(currentDist, 1);
    Serial.print("cm | Meo: ");
    Serial.print(petPresent ? "CO" : "KHONG");
    Serial.print(" | ");
    Serial.print(autoMode ? "TU DONG" : "THU CONG");
    Serial.print(" | MQTT: ");
    Serial.println(mqttClient.connected() ? "CONNECTED" : "DISCONNECTED");
  }

  // 10. Gửi trạng thái lên backend mỗi 2 giây
  if (millis() - lastTelemetryTime >= TELEMETRY_INTERVAL) {
    lastTelemetryTime = millis();
    publishTelemetryToMqtt();
  }

  delay(15);
}
