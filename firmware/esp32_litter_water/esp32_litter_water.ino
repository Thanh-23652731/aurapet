/*
 * AuraPet - ESP32 MAY DON CAT + CAM BIEN MUC NUOC (binh bom tuan hoan)
 *
 * - Don cat tu dong: PIR phat hien meo, meo ra 15s thi cao cat, co meo vao la dung ngay.
 * - Muc nuoc: HC-SR04 thu 2 do khoang cach toi mat nuoc, hien len LCD va GUI LEN WEB/APP
 *   qua MQTT de bao "sap het nuoc" cho nguoi dung do them. Bom tuan hoan chay doc lap,
 *   ESP32 KHONG bat/tat bom.
 * - Lich su ve sinh: moi luot meo di ve sinh va moi lan don cat duoc gui len web/app.
 *
 * MQTT (PREFIX dat trong secrets.h, PHAI giong MQTT_TOPIC_PREFIX trong .env):
 *   PREFIX/water   ESP32 -> server, 5 giay/lan va ngay khi doi trang thai (muc nuoc + trang thai khay)
 *     {"waterPercent":45,"waterCm":4.2,"lowWater":false,"sensorOk":true,"litterState":"idle","rssi":-60,"ip":"..."}
 *   PREFIX/litter  ESP32 -> server, moi su kien khay ve sinh
 *     {"event":"visit","durationSec":42,"agoSec":0}   meo di ve sinh xong (1 luot)
 *     {"event":"cleaned","durationSec":0,"agoSec":0}  da cao cat xong
 *     {"event":"clean_stopped",...}                   dung cao giua chung vi meo vao
 *
 * Mat Wi-Fi / MQTT thi may van don cat va do nuoc binh thuong. Su kien ve sinh duoc
 * giu lai (toi da 8) va gui bu khi co mang, kem so giay da troi qua de ghi dung gio.
 *
 * Thu vien: PubSubClient (Nick O'Leary), LiquidCrystal I2C.
 * Sao chep secrets.example.h thanh secrets.h va dien Wi-Fi + MQTT.
 */
#include <Wire.h>
#include <LiquidCrystal_I2C.h>
#include <WiFi.h>
#include <PubSubClient.h>
#include "secrets.h"

LiquidCrystal_I2C lcd(0x27, 16, 2);   // SDA=21, SCL=22

// --- CHAN ESP32 ---
const int pirPin        = 32;
const int stepPin       = 26;
const int dirPin        = 27;
const int ledRed        = 12;
const int ledGreen      = 14;
const int btnDonThuCong = 33;   // nut noi GPIO33 va GND, nhan = LOW

// --- CAM BIEN MUC NUOC (HC-SR04 thu 2) ---
const int nuocTrig = 25;        // TRIG -> GPIO25
const int nuocEcho = 34;        // ECHO -> 1k -> GPIO34, tu GPIO34 qua 2k xuong GND

const int PIR_MUC_CO_MEO = LOW;   // LOW = co meo

// --- THONG SO MUC NUOC (CAN DO LAI TREN BINH THAT) ---
// Khoang cach do duoc cang LON thi muc nuoc cang CAO (theo yeu cau):
//   7 cm = 100%, giam dan khoang cach thi % giam dan, 1 cm = 0%.
// Neu thuc te nuoc cao len ma so cm lai GIAM thi doi 2 so nay cho nhau.
const float NUOC_CAN_CM  = 1.0;   // 0%
const float NUOC_DAY_CM  = 7.0;   // 100%
const int   NUOC_THAP_PT = 20;    // duoi muc nay (%) bao sap het nuoc
const int   NUOC_HET_THAP_PT = NUOC_THAP_PT + 5;  // phai len lai muc nay moi het bao (chong nhay do song nuoc)
const unsigned long CHU_KY_DO_NUOC = 2000;

float nuocCm = -1;
int   nuocPhanTram = -1;
bool  nuocThap = false;          // trang thai "sap het nuoc" gui len web/app
unsigned long lanDoNuoc = 0;
String nuocLCDCu = "";

// --- MANG (Wi-Fi + MQTT) ---
WiFiClient wifiClient;
PubSubClient mqtt(wifiClient);
String TOPIC_WATER;
const unsigned long CHU_KY_GUI_NUOC = 5000;   // gui muc nuoc len web 5s/lan
const unsigned long CHU_KY_THU_MQTT = 5000;   // mat ket noi thi 5s thu lai 1 lan
String TOPIC_LITTER;
unsigned long lanGuiNuoc = 0;
unsigned long lanThuMqtt = 0;
bool daBaoWifi = false;

// --- LICH SU VE SINH ---
// 1 luot = tu luc meo vao den luc meo ra ma khong quay lai trong THOI_GIAN_CHO_CAT_VON.
// Meo ra roi vao lai trong luc cho van tinh chung 1 luot.
const unsigned long THOI_GIAN_TOI_THIEU_DI_VS = 5000;  // ngan hon 5s coi nhu meo chi ghe qua, khong ghi
bool dangCoLuot = false;
unsigned long luotBatDau = 0;
unsigned long luotKetThuc = 0;
int trangThaiDaGui = -1;   // trang thai khay da bao len web lan gan nhat

struct SuKien { const char* loai; int giay; unsigned long luc; };
const int MAX_SU_KIEN = 8;
SuKien hangCho[MAX_SU_KIEN];
int soSuKien = 0;

// --- THONG SO CHUNG ---
const unsigned long THOI_GIAN_CHO_CAT_VON   = 15000;  // 15s sau khi meo ra
const unsigned long THOI_GIAN_KHOI_DONG_PIR = 5000;
const unsigned long LOC_MEO_VAO = 150;   // ms tin hieu on dinh moi tinh la co meo
const unsigned long LOC_MEO_RA  = 500;   // ms mat tin hieu on dinh moi tinh la meo ra

// --- THONG SO DONG CO ---
const int SO_BUOC   = 1600;
const int TOC_CHAM  = 1500;
const int TOC_NHANH = 700;
const int SO_BUOC_TANG_TOC = 300;

int trangThai = 0;    // 0: san sang | 1: meo trong | 2: cho cat von | 3: dang cao
int trangThaiCu = -1;
unsigned long thoiGianMeoRa = 0;
int viTri = 0;        // vi tri hien tai cua cao (0 = ve nha)

// Loc nhieu cam bien
bool meoOnDinh = false;
bool meoLanDoc = false;
unsigned long meoThoiDiem = 0;

// Loc nhieu nut
bool nutOnDinh = HIGH;
bool nutLanDoc = HIGH;
unsigned long nutThoiDiem = 0;

bool coMeo() {
  bool r = (digitalRead(pirPin) == PIR_MUC_CO_MEO);
  if (r != meoLanDoc) {
    meoLanDoc = r;
    meoThoiDiem = millis();
  }
  unsigned long cho = r ? LOC_MEO_VAO : LOC_MEO_RA;
  if (r != meoOnDinh && millis() - meoThoiDiem >= cho) {
    meoOnDinh = r;
  }
  return meoOnDinh;
}

bool nutVuaNhan() {
  bool r = digitalRead(btnDonThuCong);
  if (r != nutLanDoc) {
    nutLanDoc = r;
    nutThoiDiem = millis();
  }
  if (millis() - nutThoiDiem > 50 && r != nutOnDinh) {
    nutOnDinh = r;
    if (nutOnDinh == LOW) return true;
  }
  return false;
}

// ============================ MANG ============================

const char* tenTrangThai(int tt) {
  if (tt == 1) return "cat_inside";
  if (tt == 2) return "waiting";
  if (tt == 3) return "cleaning";
  return "idle";
}

// Gui muc nuoc + trang thai khay len PREFIX/water (bo qua neu chua ket noi MQTT)
void guiNuoc() {
  lanGuiNuoc = millis();
  if (!mqtt.connected()) return;
  char buf[240];
  snprintf(buf, sizeof(buf),
           "{\"waterPercent\":%d,\"waterCm\":%.1f,\"lowWater\":%s,\"sensorOk\":%s,\"litterState\":\"%s\",\"rssi\":%d,\"ip\":\"%s\"}",
           nuocPhanTram, nuocCm, nuocThap ? "true" : "false", nuocPhanTram >= 0 ? "true" : "false",
           tenTrangThai(trangThai), (int)WiFi.RSSI(), WiFi.localIP().toString().c_str());
  mqtt.publish(TOPIC_WATER.c_str(), buf);
}

// Gui cac su kien dang cho len PREFIX/litter (giu lai neu chua co mang)
void guiSuKien() {
  while (soSuKien > 0 && mqtt.connected()) {
    char buf[120];
    snprintf(buf, sizeof(buf), "{\"event\":\"%s\",\"durationSec\":%d,\"agoSec\":%lu}",
             hangCho[0].loai, hangCho[0].giay, (millis() - hangCho[0].luc) / 1000UL);
    if (!mqtt.publish(TOPIC_LITTER.c_str(), buf)) return;
    for (int i = 1; i < soSuKien; i++) hangCho[i - 1] = hangCho[i];
    soSuKien--;
  }
}

// Them 1 su kien (luc = thoi diem xay ra theo millis) va gui ngay neu co mang
void themSuKien(const char* loai, int giay, unsigned long luc) {
  if (soSuKien == MAX_SU_KIEN) {          // day hang cho: bo su kien cu nhat
    for (int i = 1; i < MAX_SU_KIEN; i++) hangCho[i - 1] = hangCho[i];
    soSuKien--;
  }
  hangCho[soSuKien].loai = loai;
  hangCho[soSuKien].giay = giay;
  hangCho[soSuKien].luc = luc;
  soSuKien++;
  Serial.print("[Khay] Su kien: ");
  Serial.println(loai);
  guiSuKien();
}

// Meo vao khay: bat dau 1 luot moi (neu dang trong luot thi tinh tiep)
void batDauLuot() {
  if (!dangCoLuot) {
    dangCoLuot = true;
    luotBatDau = millis();
  }
}

// Het thoi gian cho (hoac bam nut don): chot luot ve sinh va bao len web
void ketThucLuot() {
  if (!dangCoLuot) return;
  dangCoLuot = false;
  unsigned long dai = luotKetThuc - luotBatDau;
  if (dai >= THOI_GIAN_TOI_THIEU_DI_VS) {
    themSuKien("visit", (int)(dai / 1000UL), luotKetThuc);
  }
}

// Giu ket noi MQTT, KHONG chan chuong trinh lau (Wi-Fi tu ket noi lai nho setAutoReconnect)
void giuKetNoi() {
  if (WiFi.status() != WL_CONNECTED) {
    daBaoWifi = false;
    return;
  }
  if (!daBaoWifi) {
    daBaoWifi = true;
    Serial.print("[WiFi] Da ket noi, IP ");
    Serial.println(WiFi.localIP());
  }
  if (mqtt.connected()) {
    mqtt.loop();
    guiSuKien();
    return;
  }
  if (millis() - lanThuMqtt < CHU_KY_THU_MQTT) return;
  lanThuMqtt = millis();

  String id = "AuraPet_Litter_" + WiFi.macAddress();
  id.replace(":", "");
  bool coTaiKhoan = strlen(MQTT_USER) > 0;
  if (mqtt.connect(id.c_str(), coTaiKhoan ? MQTT_USER : nullptr, coTaiKhoan ? MQTT_PASS : nullptr)) {
    Serial.println("[MQTT] Da ket noi, gui muc nuoc len " + TOPIC_WATER);
    guiNuoc();
    guiSuKien();   // gui bu cac su kien luc mat mang
  } else {
    Serial.print("[MQTT] Loi ket noi rc=");
    Serial.println(mqtt.state());
  }
}

// ========================== MUC NUOC ==========================

// Do khoang cach toi mat nuoc: 3 mau, lay gia tri giua de loc nhieu song nuoc
float docNuocCm() {
  float v[3];
  int n = 0;
  for (int i = 0; i < 3; i++) {
    digitalWrite(nuocTrig, LOW);
    delayMicroseconds(2);
    digitalWrite(nuocTrig, HIGH);
    delayMicroseconds(10);
    digitalWrite(nuocTrig, LOW);
    long d = pulseIn(nuocEcho, HIGH, 25000);
    // Chi nhan gia tri trong 2..50 cm (116..2915 us); ngoai khoang nay la nhieu / khong co vat
    if (d >= 117 && d <= 2915) v[n++] = d * 0.0343 / 2.0;
    delay(20);
  }
  if (n == 0) return -1;
  for (int i = 0; i < n - 1; i++)
    for (int j = i + 1; j < n; j++)
      if (v[j] < v[i]) { float t = v[i]; v[i] = v[j]; v[j] = t; }
  return v[n / 2];
}

// Do nuoc va hien thi o dong 0 cua LCD (chi viet khi noi dung doi)
void capNhatNuoc() {
  nuocCm = docNuocCm();
  if (nuocCm < 0) {
    nuocPhanTram = -1;
  } else {
    float p = (NUOC_CAN_CM - nuocCm) * 100.0 / (NUOC_CAN_CM - NUOC_DAY_CM);
    nuocPhanTram = (int)constrain(p, 0.0f, 100.0f);
  }

  // Trang thai "sap het nuoc" co do tre: < 20% thi bao, >= 25% moi het bao.
  // Loi cam bien thi giu nguyen trang thai cu (web se hien "loi cam bien").
  bool thapMoi = nuocThap;
  if (nuocPhanTram >= 0) {
    if (nuocPhanTram < NUOC_THAP_PT) thapMoi = true;
    else if (nuocPhanTram >= NUOC_HET_THAP_PT) thapMoi = false;
  }
  if (thapMoi != nuocThap) {
    nuocThap = thapMoi;
    Serial.println(nuocThap ? "[Nuoc] SAP HET NUOC -> bao len web/app" : "[Nuoc] Da du nuoc tro lai");
    guiNuoc();   // bao ngay, khong doi chu ky 5s
  }

  String s;
  if (nuocPhanTram < 0)                  s = "NUOC: LOI CAM BIEN";
  else if (nuocPhanTram < NUOC_THAP_PT)  s = "SAP HET NUOC " + String(nuocPhanTram) + "%";
  else                                   s = "NUOC: " + String(nuocPhanTram) + "%";
  while (s.length() < 16) s += ' ';
  s = s.substring(0, 16);

  if (s != nuocLCDCu) {
    lcd.setCursor(0, 0);
    lcd.print(s);
    nuocLCDCu = s;
  }
}

// ========================== DON CAT ===========================

// Quay dong co. Tra ve false neu bi dung giua chung vi co meo
bool quayDongCo(bool chieu, int soBuoc) {
  digitalWrite(dirPin, chieu ? HIGH : LOW);
  delayMicroseconds(20);

  for (int i = 0; i < soBuoc; i++) {
    if (coMeo()) return false;   // co meo -> dung ngay

    int d = TOC_NHANH;
    if (i < SO_BUOC_TANG_TOC) {
      d = map(i, 0, SO_BUOC_TANG_TOC, TOC_CHAM, TOC_NHANH);
    } else if (i >= soBuoc - SO_BUOC_TANG_TOC) {
      d = map(soBuoc - i, 0, SO_BUOC_TANG_TOC, TOC_CHAM, TOC_NHANH);
    }
    digitalWrite(stepPin, HIGH);
    delayMicroseconds(d);
    digitalWrite(stepPin, LOW);
    delayMicroseconds(d);

    viTri += chieu ? 1 : -1;
  }
  return true;
}

// Tra ve true neu don xong, false neu bi dung vi co meo
bool donDepCat() {
  // Neu lan truoc bi dung giua chung, ve nha truoc
  if (viTri > 0) {
    if (!quayDongCo(false, viTri)) return false;
  }

  Serial.println("Dang don cat...");
  if (!quayDongCo(true, SO_BUOC)) return false;

  for (int i = 0; i < 20; i++) {     // nghi 1s, van kiem tra meo
    if (coMeo()) return false;
    delay(50);
  }

  if (!quayDongCo(false, SO_BUOC)) return false;
  Serial.println("Don cat thanh cong!");
  return true;
}

void capNhatLCD(int tt) {
  if (tt != trangThaiCu) {
    lcd.setCursor(0, 1);
    if (tt == 0)      lcd.print("TT: SAN SANG    ");
    else if (tt == 1) lcd.print("MEO DANG O TRONG");
    else if (tt == 2) lcd.print("MEO RA->CHO VON ");
    else if (tt == 3) lcd.print("DANG CAO CAT... ");
    trangThaiCu = tt;
  }
}

void setup() {
  Serial.begin(115200);

  pinMode(pirPin, INPUT);
  pinMode(stepPin, OUTPUT);
  pinMode(dirPin, OUTPUT);
  pinMode(ledRed, OUTPUT);
  pinMode(ledGreen, OUTPUT);
  pinMode(btnDonThuCong, INPUT_PULLUP);
  pinMode(nuocTrig, OUTPUT);
  pinMode(nuocEcho, INPUT);

  lcd.init();
  lcd.backlight();
  lcd.setCursor(0, 0);
  lcd.print("MAY DON VE SINH ");
  lcd.setCursor(0, 1);
  lcd.print("KHOI DONG PIR...");

  // Wi-Fi + MQTT: bat dau ket noi o nen (khong cho), PIR khoi dong 5s trong luc do
  TOPIC_WATER = String(TOPIC_PREFIX);
  while (TOPIC_WATER.endsWith("/")) TOPIC_WATER.remove(TOPIC_WATER.length() - 1);
  TOPIC_LITTER = TOPIC_WATER + "/litter";
  TOPIC_WATER += "/water";
  WiFi.mode(WIFI_STA);
  WiFi.setAutoReconnect(true);
  WiFi.begin(WIFI_SSID, WIFI_PASS);
  mqtt.setServer(MQTT_HOST, MQTT_PORT);
  mqtt.setKeepAlive(30);       // luc cao cat (~7s) khong goi mqtt.loop van khong bi ngat
  mqtt.setSocketTimeout(5);
  Serial.print("[WiFi] Dang ket noi ");
  Serial.println(WIFI_SSID);

  unsigned long batDau = millis();
  while (millis() - batDau < THOI_GIAN_KHOI_DONG_PIR) {
    digitalWrite(ledRed, (millis() / 300) % 2);
    digitalWrite(ledGreen, LOW);
    delay(50);
  }

  nutOnDinh = digitalRead(btnDonThuCong);
  nutLanDoc = nutOnDinh;

  // Khoi tao trang thai cam bien theo gia tri hien tai
  meoOnDinh = (digitalRead(pirPin) == PIR_MUC_CO_MEO);
  meoLanDoc = meoOnDinh;

  digitalWrite(ledGreen, HIGH);
  digitalWrite(ledRed, LOW);

  capNhatNuoc();               // hien muc nuoc ngay khi khoi dong
  lanDoNuoc = millis();
}

void loop() {
  giuKetNoi();

  if (nutVuaNhan() && trangThai != 1 && trangThai != 3) {
    if (trangThai == 2) ketThucLuot();   // dang cho cat von ma bam don -> chot luot truoc
    trangThai = 3;
    Serial.println("Nut don cat duoc bam!");
  }

  // Trang thai khay doi -> bao len web ngay (truoc khi vao buoc cao cat, vi buoc do chan chuong trinh)
  if (trangThai != trangThaiDaGui) {
    trangThaiDaGui = trangThai;
    guiNuoc();
  }

  // Do muc nuoc moi 2 giay, KHONG do khi dang cao cat (de motor khong bi tre)
  if (trangThai != 3 && millis() - lanDoNuoc >= CHU_KY_DO_NUOC) {
    lanDoNuoc = millis();
    capNhatNuoc();
    Serial.print("Nuoc: ");
    Serial.print(nuocPhanTram);
    Serial.print("% (");
    Serial.print(nuocCm, 1);
    Serial.println(" cm)");
  }

  // Gui muc nuoc len web/app moi 5 giay
  if (trangThai != 3 && millis() - lanGuiNuoc >= CHU_KY_GUI_NUOC) {
    guiNuoc();
  }

  capNhatLCD(trangThai);

  switch (trangThai) {
    case 0:
      digitalWrite(ledGreen, HIGH);
      digitalWrite(ledRed, LOW);
      if (coMeo()) {
        trangThai = 1;
        batDauLuot();
        Serial.println("Meo vao thau cat!");
      }
      break;

    case 1:
      digitalWrite(ledGreen, LOW);
      digitalWrite(ledRed, HIGH);
      if (!coMeo()) {
        trangThai = 2;
        thoiGianMeoRa = millis();
        luotKetThuc = thoiGianMeoRa;
        Serial.println("Meo da ra khoi thau!");
      }
      break;

    case 2:
      if (coMeo()) {
        trangThai = 1;
        Serial.println("Meo vao thau cat!");
      } else if (millis() - thoiGianMeoRa >= THOI_GIAN_CHO_CAT_VON) {
        ketThucLuot();
        trangThai = 3;
      }
      break;

    case 3:
      digitalWrite(ledGreen, LOW);
      digitalWrite(ledRed, HIGH);
      capNhatLCD(trangThai);
      if (donDepCat()) {
        themSuKien("cleaned", 0, millis());
        trangThai = 0;
      } else {
        themSuKien("clean_stopped", 0, millis());
        trangThai = 1;                 // bi dung vi co meo
        batDauLuot();
        Serial.println("Meo vao thau cat!");
      }
      break;
  }
}
