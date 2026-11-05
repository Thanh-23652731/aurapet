# Kiến trúc AuraPet 2.0

## Luồng điều khiển

1. Người dùng bấm **Cho ăn 30 g** trên web/app → `POST /api/v1/device/feed`.
2. `DeviceService` kiểm tra máy đang online (telemetry trong 10 giây gần nhất), không đang nhả hạt → gửi `{"action":"FEED","gram":30,"source":"Web"}` lên topic `PREFIX/command` qua `MqttGateway`.
3. ESP32 quay động cơ, đọc cân liên tục, dừng khi đủ gram, rồi gửi `PREFIX/log` với số gram **thực tế**.
4. Backend lưu `feeding_logs` (1 lần duy nhất) + tạo cảnh báo hoạt động. Web/app thấy ngay ở lần cập nhật kế tiếp (2 giây).

## Topic MQTT

| Topic | Chiều | Nội dung |
|---|---|---|
| `PREFIX/telemetry` | ESP32 → server | cân, thùng chứa, mèo, đang nhả, chế độ, bơm (2 s/lần) |
| `PREFIX/log` | ESP32 → server | kết quả mỗi lần nhả hạt (`addedGram`, `result`) |
| `PREFIX/status` | ESP32 → server | `online` / `offline` (retained + Last Will) |
| `PREFIX/vision` | Raspberry Pi → server | `{catPresent, confidence}` từ camera YOLOv8 khi mèo đến gần / rời đi |
| `PREFIX/command` | server → ESP32 | `FEED`, `STOP`, `TARE`, `AUTO`, `SET_TARGET`, `PUMP`, `PUMP_AUTO` |

## Quy tắc tự động (theo lưu đồ Hình 4 và Hình 6)

- **Tự cho ăn**: chế độ tự động bật + có mèo (cảm biến ESP32 hoặc camera Pi) + khay < 10 g + đã qua thời gian chờ.
- **Lịch hẹn giờ**: đến giờ (giờ Việt Nam), backend gửi lệnh nếu khay < 10 g; nếu máy offline thì tạo cảnh báo "lỡ bữa".
- **Bơm nước**: khi có mèo và bật "Tự bơm" thì chạy N giây (chỉ khi đã cấu hình `PUMP_PIN`).

## Nguồn dữ liệu

- `device_settings` (DB): khẩu phần, chế độ, lịch, bé của máy, bơm. Đồng bộ xuống ESP32 mỗi khi máy kết nối lại.
- Trạng thái thời gian thực: chỉ trong RAM backend (không ghi DB mỗi 2 giây).
- Nút AUTO vật lý trên máy được đồng bộ ngược về cài đặt.

## Bảo mật

- JWT bắt buộc cho mọi API trừ đăng nhập/đăng ký/ping; secret lấy từ `.env`, app từ chối khởi động nếu thiếu hoặc yếu.
- Đăng ký luôn tạo `ROLE_USER`; người dùng thường chỉ xem/sửa mèo của mình.
- Khoá Gemini và mật khẩu broker chỉ nằm trên server.
