/*
 * ===================================================================
 * AuraPet AIoT - ESP32 Firmware for Smart Pet Feeder & Water Fountain
 * Phân hệ điều khiển cơ điện tử phần cứng & Đồng bộ Web Dashboard
 * ===================================================================
 * 
 * PHẦN CỨNG:
 * - Bo mạch: ESP32 DevKit V1 (30 chân hoặc 38 chân)
 * - Động cơ nhả hạt: Servo SG90 / MG996R (Chân GPIO 18)
 * - Máy bơm nước: Module Relay 5V kích mức LOW/HIGH (Chân GPIO 19)
 * - Đèn LED Xanh (Báo sẵn sàng): Chân GPIO 21
 * - Đèn LED Đỏ (Báo cạn/cảnh báo): Chân GPIO 22
 * - Nút bấm thủ công (Btn Led / Cho ăn tại chỗ): Chân GPIO 23 (Input Pull-up)
 * - Cảm biến phát hiện mèo (PIR / Cảm biến tiệm cận hồng ngoại): Chân GPIO 34
 * - Cảm biến cân nặng HX711 (Tùy chọn): DOUT = GPIO 4, SCK = GPIO 5
 */

#include <WiFi.h>
#include <HTTPClient.h>
#include <WiFiUdp.h>
#include <ArduinoJson.h> // Cài đặt qua Arduino Library Manager: "ArduinoJson by Benoit Blanchon" phiên bản 6.x hoặc 7.x
#include <ESP32Servo.h>

// ===================================================================
// 1. CẤU HÌNH WIFI & MÁY CHỦ WEB
// ===================================================================
const char* WIFI_SSID     = "YOUR_WIFI_NAME";     // Thay bằng tên WiFi nhà bạn
const char* WIFI_PASSWORD = "YOUR_WIFI_PASSWORD"; // Thay bằng mật khẩu WiFi

// ĐỊA CHỈ BACKEND SERVER:
// - Khi chạy cục bộ trên máy tính cùng mạng WiFi: dùng IP máy tính (VD: "http://10.10.59.64:8080")
// - Khi dùng Cloudflare Tunnel: dùng link HTTPS (VD: "https://your-domain.trycloudflare.com")
// - Khi deploy lên Render: dùng link Render (VD: "https://aurapet-backend.onrender.com")
String backendServerUrl = "http://10.10.59.64:8080"; 

// Bật chế độ tự động tìm IP máy chủ trong mạng LAN qua UDP port 8888
bool enableUdpAutoDiscovery = true;

// ===================================================================
// 2. KHAI BÁO CHÂN GPIO
// ===================================================================
const int PIN_SERVO_FOOD   = 18;
const int PIN_RELAY_PUMP   = 19;
const int PIN_LED_GREEN    = 21;
const int PIN_LED_RED      = 22;
const int PIN_BTN_MANUAL   = 23;
const int PIN_PIR_CAT      = 34;

Servo foodServo;
WiFiUDP udpClient;
const int UDP_PORT = 8888;

unsigned long lastPollTime = 0;
const unsigned long POLL_INTERVAL = 2000; // Cứ mỗi 2 giây đồng bộ trạng thái với Website 1 lần

// ===================================================================
// 3. HÀM TỰ ĐỘNG DÒ TÌM IP SERVER TRONG MẠNG LAN (UDP DISCOVERY)
// ===================================================================
void discoverServerIp() {
  Serial.println("[UDP Discovery] Dang tim kiem AuraPet Backend trong mang LAN...");
  udpClient.begin(UDP_PORT);
  
  IPAddress broadcastIp(255, 255, 255, 255);
  String discoverMsg = "AURAPET_DISCOVER";
  
  // Gửi tối đa 3 lần gói tin broadcast
  for (int retry = 0; retry < 3; retry++) {
    udpClient.beginPacket(broadcastIp, UDP_PORT);
    udpClient.write((const uint8_t*)discoverMsg.c_str(), discoverMsg.length());
    udpClient.endPacket();

    unsigned long startWait = millis();
    while (millis() - startWait < 1500) {
      int packetSize = udpClient.parsePacket();
      if (packetSize) {
        char packetBuffer[256];
        int len = udpClient.read(packetBuffer, 255);
        if (len > 0) packetBuffer[len] = 0;
        String reply = String(packetBuffer);
        if (reply.startsWith("AURAPET_SERVER:")) {
          backendServerUrl = reply.substring(15);
          backendServerUrl.trim();
          Serial.print("[UDP Discovery] Tim thay Server Backend tai: ");
          Serial.println(backendServerUrl);
          udpClient.stop();
          return;
        }
      }
      delay(50);
    }
  }
  Serial.print("[UDP Discovery] Khong thay broadcast, su dung URL mac dinh: ");
  Serial.println(backendServerUrl);
  udpClient.stop();
}

// ===================================================================
// 4. HÀM ĐIỀU KHIỂN ĐỘNG CƠ NHẢ HẠT
// ===================================================================
void dispenseFood(int portionGrams) {
  Serial.printf("[Actuator] Dang nha hat: %d gam...\n", portionGrams);
  digitalWrite(PIN_LED_GREEN, HIGH);
  digitalWrite(PIN_LED_RED, LOW);

  // Xoay Servo nhả thức ăn (quay 90 độ mở cửa và đóng lại)
  int cycles = map(portionGrams, 10, 100, 1, 6);
  if (cycles < 1) cycles = 1;

  for (int i = 0; i < cycles; i++) {
    foodServo.write(110); // Mở cửa khay
    delay(400);
    foodServo.write(10);  // Đóng cửa khay
    delay(400);
  }

  // Sau khi nhả hạt xong, gửi báo cáo về Website rằng đã nhả xong (machines = false)
  sendFoodReport(false, portionGrams);
}

// ===================================================================
// 5. ĐỒNG BỘ VÀ NHẬN LỆNH TỪ PHÂN HỆ THỨC ĂN (SYSTEM_FOOD)
// ===================================================================
void syncFoodSubsystem() {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = backendServerUrl + "/api/v1/system/food";
  http.begin(url);
  int httpCode = http.GET();

  if (httpCode == 200) {
    String payload = http.getString();
    DynamicJsonDocument doc(1024);
    deserializeJson(doc, payload);

    bool success = doc["success"] | false;
    if (success) {
      JsonObject data = doc["data"];
      bool machineRunning = data["machines"] | false;
      bool autoMode       = data["autoMode"] | false;
      bool ledGreen       = data["ledGreen"] | false;
      bool ledRed         = data["ledRed"] | false;
      int percel          = data["percel"] | 45;

      digitalWrite(PIN_LED_GREEN, ledGreen ? HIGH : LOW);
      digitalWrite(PIN_LED_RED, ledRed ? HIGH : LOW);

      // Nếu trên Website người dùng bấm nút "Cho ăn ngay" (machines = true)
      if (machineRunning) {
        dispenseFood(percel);
      }
    }
  }
  http.end();
}

// ===================================================================
// 6. ĐỒNG BỘ VÀ NHẬN LỆNH TỪ PHÂN HỆ NƯỚC UỐNG (SYSTEM_WATER)
// ===================================================================
void syncWaterSubsystem() {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = backendServerUrl + "/api/v1/system/water";
  http.begin(url);
  int httpCode = http.GET();

  if (httpCode == 200) {
    String payload = http.getString();
    DynamicJsonDocument doc(1024);
    deserializeJson(doc, payload);

    bool success = doc["success"] | false;
    if (success) {
      JsonObject data = doc["data"];
      bool pumpState = data["machines"] | false;
      
      // Bật hoặc tắt máy bơm nước tuần hoàn
      digitalWrite(PIN_RELAY_PUMP, pumpState ? HIGH : LOW);
    }
  }
  http.end();
}

// ===================================================================
// 7. GỬI BÁO CÁO CẬP NHẬT LÊN SERVER
// ===================================================================
void sendFoodReport(bool isRunning, int percel) {
  if (WiFi.status() != WL_CONNECTED) return;

  HTTPClient http;
  String url = backendServerUrl + "/api/v1/system/food";
  http.begin(url);
  http.addHeader("Content-Type", "application/json");

  DynamicJsonDocument doc(256);
  doc["machines"] = isRunning;
  doc["percel"] = percel;
  doc["catDetected"] = (digitalRead(PIN_PIR_CAT) == HIGH);

  String jsonString;
  serializeJson(doc, jsonString);

  http.POST(jsonString);
  http.end();
}

// ===================================================================
// 8. SETUP & LOOP CHÍNH
// ===================================================================
void setup() {
  Serial.begin(115200);
  delay(1000);
  Serial.println("\n=======================================================");
  Serial.println("         AuraPet AIoT - ESP32 Hardware Starting        ");
  Serial.println("=======================================================");

  // Cấu hình GPIO
  pinMode(PIN_RELAY_PUMP, OUTPUT);
  pinMode(PIN_LED_GREEN, OUTPUT);
  pinMode(PIN_LED_RED, OUTPUT);
  pinMode(PIN_BTN_MANUAL, INPUT_PULLUP);
  pinMode(PIN_PIR_CAT, INPUT);

  digitalWrite(PIN_RELAY_PUMP, LOW);
  digitalWrite(PIN_LED_GREEN, HIGH);
  digitalWrite(PIN_LED_RED, LOW);

  // Khởi tạo Servo
  foodServo.attach(PIN_SERVO_FOOD);
  foodServo.write(10); // Đóng nắp ban đầu

  // Kết nối WiFi
  Serial.print("[WiFi] Dang ket noi toi: ");
  Serial.println(WIFI_SSID);
  WiFi.mode(WIFI_STA);
  WiFi.begin(WIFI_SSID, WIFI_PASSWORD);

  int wifiTimeout = 0;
  while (WiFi.status() != WL_CONNECTED && wifiTimeout < 30) {
    delay(500);
    Serial.print(".");
    wifiTimeout++;
  }

  if (WiFi.status() == WL_CONNECTED) {
    Serial.println("\n[WiFi] Ket noi thanh cong!");
    Serial.print("[WiFi] IP cua ESP32: ");
    Serial.println(WiFi.localIP());

    // Nếu kích hoạt Auto Discovery và đang dùng IP nội bộ
    if (enableUdpAutoDiscovery && backendServerUrl.startsWith("http://10.") || backendServerUrl.startsWith("http://192.168.")) {
      discoverServerIp();
    }
  } else {
    Serial.println("\n[WiFi] Ket noi that bai! Vui long kiem tra lai SSID va Password.");
  }
}

void loop() {
  // 1. Kiểm tra nút bấm vật lý trên thiết bị (Cho ăn khẩn cấp / Manual feed)
  if (digitalRead(PIN_BTN_MANUAL) == LOW) {
    delay(50); // Chống dội phím (debounce)
    if (digitalRead(PIN_BTN_MANUAL) == LOW) {
      Serial.println("[Manual Button] Nguoi dung bam nut tai hop thiet bi!");
      dispenseFood(45);
      while (digitalRead(PIN_BTN_MANUAL) == LOW) delay(10);
    }
  }

  // 2. Định kỳ đồng bộ lệnh từ Website (Mỗi 2 giây)
  unsigned long now = millis();
  if (now - lastPollTime >= POLL_INTERVAL) {
    lastPollTime = now;
    syncFoodSubsystem();
    syncWaterSubsystem();
  }

  delay(20);
}
