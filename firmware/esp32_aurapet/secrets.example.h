// =============================================================================
// AuraPet - FILE MẪU CẤU HÌNH BÍ MẬT
// 1. Sao chép file này thành "secrets.h" (cùng thư mục)
// 2. Điền thông tin thật. File secrets.h đã nằm trong .gitignore, KHÔNG bị đẩy lên GitHub.
// =============================================================================
#pragma once

// ---- Wi-Fi nơi đặt máy cho ăn ----
#define WIFI_SSID "TEN_WIFI"
#define WIFI_PASS "MAT_KHAU_WIFI"

// Chỉ dùng cho lần khởi động đầu tiên. Sau đó đổi Wi-Fi bằng điện thoại (xem đầu file .ino),
// không cần sửa file này và nạp lại code.

// ---- Chế độ cài Wi-Fi qua điện thoại ----
#define SETUP_AP_NAME "AuraPet-Setup"
#define SETUP_AP_PASS "aurapet123"   // >= 8 ký tự, nên đổi

// ---- MQTT broker ----
// Khuyên dùng EMQX Serverless (miễn phí, có tài khoản + TLS): cổng 8883, MQTT_USE_TLS 1.
// Broker công cộng broker.emqx.io (cổng 1883, không mật khẩu) chỉ nên dùng để thử nghiệm.
#define MQTT_HOST "broker.emqx.io"
#define MQTT_PORT 1883
#define MQTT_USE_TLS 0
#define MQTT_USER ""
#define MQTT_PASS ""

// ---- Chuỗi bí mật của topic ----
// PHẢI giống hệt MQTT_TOPIC_PREFIX trong file .env của backend.
// Dùng chuỗi ngẫu nhiên để người lạ không đoán được topic điều khiển máy.
#define TOPIC_PREFIX "aurapet/doi-chuoi-ngau-nhien-o-day"
