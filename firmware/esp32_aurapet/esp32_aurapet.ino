/*
 * ===================================================================
 * AuraPet AIoT - ESP32 Smart Pet Feeder (Tích hợp Động cơ bước + Web)
 * Kết nối Website Dashboard và giữ nguyên toàn bộ phần cứng của bạn:
 * - Động cơ bước: STEP=13, DIR=23, EN=17
 * - Cân tải trọng: HX711 (DT=16, SCK=4)
 * - Cảm biến siêu âm phễu: TRIG=5, ECHO=18
 * - Cảm biến phát hiện mèo: SENSOR_PIN=19
 * - Màn hình I2C LCD: SDA=21, SCL=22
 * - Nút bấm: BTN_FEED=32, BTN_STOP=33, BTN_AUTO=25, BTN_TARE=26
 * - Đèn LED: LED_RED=27, LED_GREEN=14
 * ===================================================================
 */

#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include "HX711.h"
#include <WiFi.h>
#include <HTTPClient.h>
#include <ArduinoJson.h> // Cài đặt qua Arduino Library Manager: "ArduinoJson" (bản 6.x hoặc 7.x)

// ===================================================================
// 1. CẤU HÌNH WIFI VÀ ĐỊA CHỈ MÁY CHỦ WEBSITE
// ===================================================================
const char* WIFI_SSID     = "YOUR_WIFI_NAME";     // Thay bằng tên WiFi nhà bạn
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD"; // Thay bằng mật khẩu WiFi

// Địa chỉ Backend Website:
// - Chạy cùng mạng WiFi ở nhà: "http://10.10.59.64:8080" (hoặc IP máy tính của bạn)
// - Dùng Cloudflare Tunnel:    "https://your-tunnel.trycloudflare.com"
// - Đã deploy lên Render:      "https://aurapet-backend.onrender.com"
String backendServerUrl = "http://10.10.59.64:8080";

// Thời gian chu kỳ gửi/nhận lệnh với Website (2000ms = 2 giây)
const unsigned long SYNC_INTERVAL = 2000;
unsigned long lastSyncTime = 0;

// ===================================================================
// 2. ĐỊNH NGHĨA CHÂN PHẦN CỨNG (GIỮ NGUYÊN 100% CỦA BẠN)
// ===================================================================
#define TRIG       5
#define ECHO       18
#define SENSOR_PIN 19
#define BTN_FEED   32   // CHO AN
#define BTN_STOP   33   // DUNG
#define BTN_AUTO   25   // TU DONG
#define BTN_TARE   26   // TARE (ve 0 can)
#define LED_RED    27
#define LED_GREEN  14
#define STEP_PIN   13
#define DIR_PIN    23
#define EN_PIN     17
#define HX_DT      16
#define HX_SCK     4
#define SDA_PIN    21
#define SCL_PIN    22

// ===================================================================
// 3. THÔNG SỐ VẬN HÀNH
// ===================================================================
LiquidCrystal_I2C *lcd = nullptr;
bool lcdOk = false;
HX711 scale;

float CALIB_FACTOR = 420.0;           // Hiệu chỉnh hệ số cân
float TARGET_GRAM  = 30.0;            // Mốc gram mỗi lần cho ăn (tự đồng bộ từ Web)
const float OVERSHOOT_GRAM = 2.0;

const int  DELAY_START_US = 3500;     // Tốc độ bắt đầu (chậm)
const int  DELAY_MIN_US   = 2500;     // Tốc độ tối đa (nhỏ hơn = nhanh hơn)
const int  RAMP_STEPS     = 60;       // Số bước tăng tốc
const int  CHUNK_STEPS    = 600;      // Chạy 600 bước rồi đọc cân 1 lần
const unsigned long FEED_TIMEOUT = 20000; // Tối đa 20s mỗi lần cho ăn

const float LOW_LEVEL_CM = 15.0;      // Khoảng cách phễu > 15cm là sắp hết hạt
const bool  SENSOR_ACTIVE_LOW = true; // OUT = 0 là có mèo
const int   STABLE_COUNT = 3;
const unsigned long AUTO_COOLDOWN = 30000; // 30s giữa các lần tự động

bool autoMode = false;
unsigned long lastAuto = 0, lastPrint = 0, lastLcd = 0;
int feedCount = 0;
bool petPresent = false;
int sameCount = 0;
float weight = 0;
bool scaleOk = false;
bool isFeedingNow = false;

// ===================================================================
// 4. HÀM TIỆN ÍCH LCD, CẢM BIẾN, CÂN NẶNG
// ===================================================================
void logMsg(const char* msg) {
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
  if (!lcdOk) return;
  while (s.length() < 16) s += ' ';
  lcd->setCursor(0, row);
  lcd->print(s.substring(0, 16));
}

void setupLcd() {
  byte addr = 0;
  if (i2cExists(0x27)) addr = 0x27;
  else if (i2cExists(0x3F)) addr = 0x3F;

  if (addr == 0) {
    logMsg("LCD: KHONG TIM THAY. Kiem tra VSS/VDD, SDA(GPIO21), SCL(GPIO22)");
    return;
  }
  Serial.print("LCD: tim thay o dia chi 0x");
  Serial.println(addr, HEX);

  lcd = new LiquidCrystal_I2C(addr, 16, 2);
  lcd->init();
  lcd->backlight();
  lcd->clear();
  lcdOk = true;
  lcdLine(0, "AuraPet AIoT");
  lcdLine(1, "Dang ket noi...");
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
  digitalWrite(TRIG, LOW);  delayMicroseconds(2);
  digitalWrite(TRIG, HIGH); delayMicroseconds(10);
  digitalWrite(TRIG, LOW);
  long d = pulseIn(ECHO, HIGH, 30000);
  if (d == 0) return -1;
  return d * 0.0343 / 2.0;
}

float readWeight(int times) {
  if (!scaleOk || !scale.is_ready()) return weight;
  weight = scale.get_units(times);
  return weight;
}

void doTare() {
  if (scale.wait_ready_timeout(1000)) {
    scale.tare();
    scaleOk = true;
    weight = 0;
    logMsg("TARE: can ve 0");
    lcdLine(0, "Da TARE");
  } else {
    scaleOk = false;
    logMsg("HX711 KHONG PHAN HOI. Kiem tra DT(GPIO16), SCK(GPIO4)");
    lcdLine(0, "Loi HX711");
  }
}

bool updateSensor(int raw) {
  bool now = (raw == LOW) == SENSOR_ACTIVE_LOW;
  if (now == petPresent) { sameCount = 0; return false; }
  if (++sameCount >= STABLE_COUNT) {
    petPresent = now; sameCount = 0; return true;
  }
  return false;
}

void updateLeds(float dist) {
  bool low = (dist > LOW_LEVEL_CM);
  digitalWrite(LED_RED,   low ? HIGH : LOW);
  digitalWrite(LED_GREEN, low ? LOW  : HIGH);
}

void showLcd(float dist) {
  String l0 = String(autoMode ? "TU DONG " : "THU CONG ") + "M:" + String(TARGET_GRAM, 0) + "g";
  String l1 = "Can:" + String(weight, 0) + "g ";
  l1 += (dist > LOW_LEVEL_CM) ? "HET HAT" : "Hat:OK";
  lcdLine(0, l0);
  lcdLine(1, l1);
}

// ===================================================================
// 5. HÀM CHO ĂN (ĐỘNG CƠ BƯỚC + BÁO CÁO CHO WEBSITE)
// ===================================================================
void feed(const char* source) {
  isFeedingNow = true;
  feedCount++;
  float startW = readWeight(3);
  float goal = startW + TARGET_GRAM - OVERSHOOT_GRAM;

  Serial.print("[");
  Serial.print(millis() / 1000.0, 1);
  Serial.print("s] >>> CHO AN lan ");
  Serial.print(feedCount);
  Serial.print(" (");
  Serial.print(source);
  Serial.print(") | moc ");
  Serial.print(TARGET_GRAM, 0);
  Serial.print("g | can luc dau ");
  Serial.print(startW, 1);
  Serial.println("g");

  lcdLine(0, "Dang cho an...");
  lcdLine(1, "Muc tieu " + String(TARGET_GRAM, 0) + "g");

  digitalWrite(EN_PIN, LOW);
  digitalWrite(DIR_PIN, HIGH);
  delay(10);

  unsigned long t0 = millis();
  long stepCount = 0;
  bool stopped = false, reached = false;

  while (millis() - t0 < FEED_TIMEOUT && !reached && !stopped) {
    for (int i = 0; i < CHUNK_STEPS; i++) {
      long pos = min((long)RAMP_STEPS, stepCount);
      int d = DELAY_START_US - (long)(DELAY_START_US - DELAY_MIN_US) * pos / RAMP_STEPS;
      digitalWrite(STEP_PIN, HIGH); delayMicroseconds(d);
      digitalWrite(STEP_PIN, LOW);  delayMicroseconds(d);
      stepCount++;
      if (digitalRead(BTN_STOP) == LOW) {
        while (digitalRead(BTN_STOP) == LOW) delay(5);
        stopped = true;
        break;
      }
    }
    float w = readWeight(1);
    if (w >= goal) reached = true;
  }

  digitalWrite(EN_PIN, HIGH);
  delay(300);
  float endW = readWeight(5);
  float added = endW - startW;
  if (added < 0) added = 0;

  Serial.print("  Tong so buoc: "); Serial.print(stepCount);
  Serial.print(" | can cuoi: "); Serial.print(endW, 1);
  Serial.print("g | da them: "); Serial.print(added, 1); Serial.println("g");

  if (stopped) logMsg("Da bam DUNG, ngung motor");
  else if (reached) logMsg("Dat moc, motor da tat");
  else logMsg("HET THOI GIAN (timeout)");
  
  lcdLine(0, "Xong! +" + String(added, 0) + "g");
  delay(600);

  // Gửi báo cáo kết quả nhả hạt lên Website & CSDL
  sendReportToWebsite(false, (int)endW);
  isFeedingNow = false;
}

// ===================================================================
// 6. GIAO TIẾP 2 CHIỀU VỚI WEBSITE (ĐỒNG BỘ LỆNH & DỮ LIỆU)
// ===================================================================
void syncWithWebsite() {
  if (WiFi.status() != WL_CONNECTED || isFeedingNow) return;

  HTTPClient http;
  String url = backendServerUrl + "/api/v1/system/food";
  http.begin(url);
  http.setTimeout(1500);
  int httpCode = http.GET();

  if (httpCode == 200) {
    String payload = http.getString();
    DynamicJsonDocument doc(1024);
    DeserializationError error = deserializeJson(doc, payload);

    if (!error && doc["success"]) {
      JsonObject data = doc["data"];
      
      // 1. Nhận lệnh "Cho ăn ngay" từ nút bấm trên Website
      bool webTriggerFeed = data["machines"] | false;
      
      // 2. Nhận cấu hình khẩu phần từ Website
      if (data.containsKey("percel")) {
        int webPercel = data["percel"];
        if (webPercel >= 5 && webPercel <= 200) {
          TARGET_GRAM = (float)webPercel;
        }
      }

      // 3. Nhận trạng thái bật/tắt Tự Động từ Website
      if (data.containsKey("autoMode")) {
        autoMode = data["autoMode"];
      }

      // Nếu trên Website người dùng nhấn "Cho ăn ngay"
      if (webTriggerFeed) {
        Serial.println("\n[Website Trigger] Nhan lenh CHO AN tu Website Dashboard!");
        feed("Website");
      }
    }
  }
  http.end();

  // Gửi cập nhật thông số cảm biến lên Website
  sendTelemetryToWebsite();
}

void sendTelemetryToWebsite() {
  if (WiFi.status() != WL_CONNECTED || isFeedingNow) return;

  float dist = readDistanceCm();
  int foodPercent = 85;
  if (dist > 0) {
    // Giả sử phễu sâu 20cm: dist = 3cm -> 100%, dist >= 18cm -> 0%
    foodPercent = map((long)dist, 3, 18, 100, 0);
    foodPercent = constrain(foodPercent, 0, 100);
  }

  HTTPClient http;
  String url = backendServerUrl + "/api/v1/system/food";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");
  http.setTimeout(1500);

  DynamicJsonDocument doc(256);
  doc["autoMode"]          = autoMode;
  doc["machines"]          = isFeedingNow;
  doc["foodLevelPercent"]  = foodPercent;
  doc["scaleWeightGram"]   = (int)weight;
  doc["catDetected"]       = petPresent;
  doc["ledGreen"]          = (dist <= LOW_LEVEL_CM);
  doc["ledRed"]            = (dist > LOW_LEVEL_CM);

  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

void sendReportToWebsite(bool isRunning, int currentScaleWeight) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = backendServerUrl + "/api/v1/system/food";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  DynamicJsonDocument doc(256);
  doc["machines"]        = isRunning;
  doc["scaleWeightGram"] = currentScaleWeight;
  doc["catDetected"]     = petPresent;

  String body;
  serializeJson(doc, body);
  http.POST(body);
  http.end();
}

// ===================================================================
// 7. SETUP VÀ VÒNG LẶP CHÍNH (LOOP)
// ===================================================================
void setup() {
  Serial.begin(115200);
  delay(500);
  Serial.println("\n=============================================");
  Serial.println("  AuraPet AIoT - May Cho An Thong Minh ESP32 ");
  Serial.println("=============================================");

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
  digitalWrite(EN_PIN, HIGH); // Tắt động cơ ban đầu

  Wire.begin(SDA_PIN, SCL_PIN);
  setupLcd();

  scale.begin(HX_DT, HX_SCK);
  scale.set_scale(CALIB_FACTOR);
  doTare();

  petPresent = ((digitalRead(SENSOR_PIN) == LOW) == SENSOR_ACTIVE_LOW);

  // Kết nối WiFi
  Serial.print("[WiFi] Dang ket noi toi WiFi: ");
  Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int tries = 0;
  while (WiFi.status() != WL_CONNECTED && tries < 20) {
    delay(500);
    Serial.print(".");
    tries++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Ket noi thanh cong!");
    Serial.print("[WiFi] Dia chi IP ESP32: ");
    Serial.println(WiFi.localIP());
    lcdLine(0, "WiFi: OK");
    lcdLine(1, WiFi.localIP().toString());
    delay(1000);
  } else {
    Serial.println("\n[WiFi] Khong the ket noi WiFi. Chay che do Offline.");
    lcdLine(0, "WiFi: Offline");
    delay(1000);
  }

  logMsg("He thong da san sang!");
  Serial.println("Nut: CHO AN | DUNG | TU DONG | TARE");
  Serial.println("---------------------------------------------");
}

void loop() {
  float dist = readDistanceCm();
  int raw = digitalRead(SENSOR_PIN);
  readWeight(1);

  // 1. Xử lý các nút bấm vật lý tại hộp máy
  if (pressed(BTN_AUTO)) {
    autoMode = !autoMode;
    logMsg(autoMode ? "Che do: TU DONG (BAT)" : "Che do: THU CONG (TAT)");
    sendTelemetryToWebsite(); // Báo ngay lên web
  }
  if (pressed(BTN_TARE)) {
    doTare();
    delay(600);
  }
  if (pressed(BTN_FEED)) {
    feed("nut CHO AN tai hop");
  }

  // 2. Cảm biến phát hiện mèo đến gần
  if (updateSensor(raw)) {
    Serial.print("[");
    Serial.print(millis() / 1000.0, 1);
    Serial.print("s] CAM BIEN: ");
    Serial.print(petPresent ? "PHAT HIEN MEO!" : "meo da roi di");
    Serial.println();
    if (petPresent) {
      if (!autoMode) logMsg("Dang o che do THU CONG");
      else if (lastAuto != 0 && millis() - lastAuto <= AUTO_COOLDOWN)
        logMsg("Chua het thoi gian cho (cooldown)");
    }
  }

  // 3. Tự động cho ăn khi có mèo (nếu bật chế độ tự động)
  if (autoMode && petPresent && (lastAuto == 0 || millis() - lastAuto > AUTO_COOLDOWN)) {
    lastAuto = millis();
    feed("tu dong");
  }

  updateLeds(dist);

  // 4. Cập nhật hiển thị màn hình LCD (mỗi 500ms)
  if (millis() - lastLcd > 500) {
    lastLcd = millis();
    showLcd(dist);
  }

  // 5. In log Serial Monitor (mỗi 1000ms)
  if (millis() - lastPrint > 1000) {
    lastPrint = millis();
    Serial.print("Hat: ");
    if (dist < 0) Serial.print("LOI");
    else { Serial.print(dist, 1); Serial.print("cm"); }
    Serial.print(" | Can: "); Serial.print(weight, 1); Serial.print("g");
    Serial.print(" | Moc: "); Serial.print(TARGET_GRAM, 0); Serial.print("g");
    Serial.print(petPresent ? " | [CO MEO]" : " | [Trong]");
    Serial.print(autoMode ? " | TU DONG" : " | THU CONG");
    Serial.print(" | Lan an: "); Serial.println(feedCount);
  }

  // 6. ĐỒNG BỘ 2 CHIỀU VỚI WEBSITE (Mỗi 2 giây một lần)
  if (millis() - lastSyncTime >= SYNC_INTERVAL) {
    lastSyncTime = millis();
    syncWithWebsite();
  }

  delay(20);
}
