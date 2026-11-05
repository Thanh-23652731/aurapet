// =============================================================================
// AuraPet - FILE MẪU CẤU HÌNH (máy cho ăn esp32_feeder_emqx)
// 1. Sao chép file này thành "secrets.h" trong cùng thư mục
// 2. Điền thông tin thật. secrets.h nằm trong .gitignore, KHÔNG bị đẩy lên GitHub.
// =============================================================================
#pragma once

// ---- Wi-Fi nơi đặt máy cho ăn (ESP32 chỉ dùng được Wi-Fi 2.4 GHz) ----
#define WIFI_SSID "TEN_WIFI"
#define WIFI_PASS "MAT_KHAU_WIFI"

// ---- MQTT broker: phải giống MQTT_HOST / MQTT_PORT / MQTT_USERNAME / MQTT_PASSWORD trong .env ----
#define MQTT_HOST "broker.emqx.io"
#define MQTT_PORT 1883
#define MQTT_USER ""
#define MQTT_PASS ""

// ---- Chuỗi bí mật của topic: PHẢI giống hệt MQTT_TOPIC_PREFIX trong .env ----
#define TOPIC_PREFIX "aurapet/doi-chuoi-ngau-nhien-o-day"
