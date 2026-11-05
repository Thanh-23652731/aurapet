# AuraPet — Hệ thống chăm sóc mèo thông minh

Máy cho ăn ESP32 + backend Spring Boot + web dashboard + app Flutter. Mọi nút bấm trên **web** hay **app** đều điều khiển **phần cứng thật** qua MQTT.

```
 Web dashboard ─┐                         ┌─> ESP32 máy cho ăn (cân HX711, động cơ bước,
 App Flutter  ──┼─ REST + JWT ─> Backend ─┤      siêu âm, cảm biến mèo, bơm nước tuỳ chọn)
                │   (Spring Boot)  │  MQTT │
                │                  │       └─< Raspberry Pi: camera phát hiện mèo
                └───────────── MariaDB / Supabase PostgreSQL
```

- Trình duyệt và app **không** kết nối broker trực tiếp → mật khẩu broker chỉ nằm trên server.
- Topic MQTT có tiền tố bí mật (`MQTT_TOPIC_PREFIX`) để người lạ không đoán được.
- Nhật ký cho ăn chỉ được ghi khi ESP32 báo số gram **đo thật** bằng cân.

## Chức năng

| Màn hình | Làm được gì |
|---|---|
| Điều khiển | Cho ăn ngay theo khẩu phần, dừng khẩn cấp, xem khay (gram) và thùng chứa (%), trừ bì cân, tự cho ăn khi mèo đến (chỉ khi khay < 10 g), lịch hẹn giờ, bơm nước |
| Thú cưng | Thêm/sửa/xoá, điểm sức khoẻ, lượng ăn 7 ngày, biểu đồ cân nặng, gán bé cho máy cho ăn |
| Lịch sử | Lượng ăn 7 ngày, danh sách các lần cho ăn và nguồn lệnh |
| Bác sĩ AI | Hỏi đáp dựa trên dữ liệu thật của bé (Gemini, key giữ trên server) |
| Tài khoản | Đổi tên, đổi mật khẩu, chọn bé của máy cho ăn |

## Chạy nhanh trên máy tính (Windows)

1. Sao chép `.env.example` → `.env`, điền `AURAPET_JWT_SECRET`, thông tin DB và `MQTT_*`.
2. Sao chép `firmware/esp32_aurapet/secrets.example.h` → `secrets.h`, điền Wi-Fi và **cùng** `TOPIC_PREFIX` với `MQTT_TOPIC_PREFIX`. Nạp firmware cho ESP32 (xem [docs/HUONG_DAN_PHAN_CUNG.md](docs/HUONG_DAN_PHAN_CUNG.md)).
3. Chạy `start_aurapet.bat` (tự build lại khi mã nguồn thay đổi) → mở `http://localhost:8080`.
4. App: `cd mobile && flutter pub get && flutter run`. Trên màn hình đăng nhập nhập địa chỉ máy chủ (hoặc bấm nút tìm trong Wi-Fi).

Tài khoản mẫu: `admin@aurapet.com / Admin@123`, `user@aurapet.com / User@123`.

Triển khai cloud (Supabase, Render, Docker, GitHub Pages): [docs/HUONG_DAN_TRIEN_KHAI.md](docs/HUONG_DAN_TRIEN_KHAI.md).

## Cấu trúc

```
backend/   Spring Boot 3 (Java 17): domain, application (service), infrastructure (MQTT, JWT), interfaces (REST)
  src/main/resources/static/   Web dashboard (index.html, app.css, app.js — không cần build)
mobile/    Flutter: lib/core (API, theme), lib/data (model), lib/state (Riverpod), lib/ui (màn hình)
firmware/  esp32_aurapet (Arduino) và raspberry_pi (camera)
supabase/  schema.sql, seed.sql
docs/      Kiến trúc, phần cứng, triển khai, thiết kế mục 3.5
```

## API chính

Tài liệu đầy đủ tự sinh tại `/swagger-ui.html`.

| Phương thức | Đường dẫn | Mô tả |
|---|---|---|
| GET | `/api/v1/device/state` | Trạng thái máy (gọi 2 giây/lần) |
| POST | `/api/v1/device/feed` | `{gram, source}` cho ăn ngay |
| POST | `/api/v1/device/stop` · `/tare` | Dừng động cơ · trừ bì cân |
| POST | `/api/v1/device/pump` | `{on}` bật/tắt bơm |
| PUT | `/api/v1/device/settings` | Khẩu phần, tự động, lịch hẹn giờ, bé của máy, bơm tự động |
| GET | `/api/v1/device/feedings` | Lịch sử cho ăn |

Máy offline → HTTP 409 kèm thông báo tiếng Việt để giao diện hiển thị.

## Kiểm thử

```
mvn -f backend/pom.xml test     # backend: JUnit + MockMvc (gồm test phân quyền và điều khiển thiết bị)
cd mobile && flutter test       # app: model + màn hình đăng nhập
```

Sau khi nâng cấp từ bản cũ, chạy `cleanup_redundant.bat` một lần để xoá file thừa.
