# Kiến trúc AuraPet 2.0

## Luồng điều khiển

1. Người dùng bấm **Cho ăn 30 g** trên web/app → `POST /api/v1/device/feed`.
2. `DeviceService` chuyển cho `FeederService`, nơi kiểm tra máy đang online (telemetry trong 10 giây gần nhất), không đang nhả hạt → gửi `{"action":"FEED","gram":30,"source":"Web"}` lên topic `PREFIX/command` qua `MqttGateway`.
3. ESP32 quay động cơ, đọc cân liên tục, dừng khi đủ gram, rồi gửi `PREFIX/log` với số gram **thực tế**.
4. Backend lưu `feeding_logs` (1 lần duy nhất) + tạo cảnh báo hoạt động. Web/app thấy ngay ở lần cập nhật kế tiếp (2 giây).

## Các service phần cứng trong backend

`DeviceService` chỉ là cửa ngõ: nhận gói MQTT (qua `MqttGateway`), chuyển tới đúng service và ghép trạng thái cho web/app.

| Service | Việc |
|---|---|
| `FeederService` | Máy cho ăn: telemetry, online/offline, lệnh FEED/STOP/TARE, nhật ký nhả hạt, lịch hẹn giờ |
| `WaterMonitorService` | Mực nước bình bơm, cảnh báo sắp cạn |
| `LitterService` | Lượt đi vệ sinh, lần dọn cát |
| `VisionService` | Camera: mèo đến, nhận diện từng bé |
| `DeviceSettingsService` | Cài đặt dùng chung (khẩu phần, tự động, lịch, bé của máy) |
| `CatIdentityTracker` | Nhớ bé camera vừa nhận ra trong 60 giây để ghi nhật ký đúng bé |
| `DeviceAlerts` | Ghi cảnh báo |

## Topic MQTT

| Topic | Chiều | Nội dung |
|---|---|---|
| `PREFIX/telemetry` | ESP32 → server | cân, thùng chứa, mèo, đang nhả, chế độ (2 s/lần) |
| `PREFIX/log` | ESP32 → server | kết quả mỗi lần nhả hạt (`addedGram`, `result`) |
| `PREFIX/status` | ESP32 → server | `online` / `offline` (retained + Last Will) |
| `PREFIX/vision` | Raspberry Pi → server | `{catPresent, petName, confidence}` từ camera YOLOv8 khi mèo đến gần / rời đi (hỗ trợ phân loại từng bé) |
| `PREFIX/water` | ESP32 máy dọn cát → server | `{waterPercent, waterCm, lowWater, sensorOk, litterState}` mực nước bình bơm + trạng thái khay (5 s/lần) |
| `PREFIX/litter` | ESP32 máy dọn cát → server | `{event: visit/cleaned/clean_stopped, durationSec, agoSec}` mỗi lượt đi vệ sinh và lần dọn cát |
| `PREFIX/command` | server → ESP32 | `FEED`, `STOP`, `TARE`, `AUTO` (`enabled`, `sensor`), `SET_TARGET` |

## Quy tắc tự động (theo lưu đồ Hình 4 và Hình 6)

- **Tự cho ăn**: chế độ tự động bật + có mèo + khay < 10 g + đã qua thời gian chờ.
  - **Camera nhận ra bé** (tên lớp của mô hình trùng tên bé trong app) → nhả **khẩu phần riêng của bé** (`pets.portion_gram`, trống = khẩu phần chung) và ghi nhật ký cho bé đó.
  - **Camera nhận ra mèo lạ** (lớp `Khac`) → không nhả, báo 1 lần mỗi 10 phút.
  - **Camera chỉ biết "có mèo"** (chưa có mô hình nhận diện / chưa chắc) → khẩu phần chung, ghi cho bé mặc định.
  - **Khi camera đang chạy** (có hình hoặc heartbeat trong 30 giây), backend gửi `AUTO` kèm `"sensor": false` để ESP32 **không tự nhả theo cảm biến hồng ngoại** (vì cảm biến thấy mèo trước khi camera kịp nhận ra bé). Camera tắt → gửi lại `"sensor": true`, ESP32 tự cho ăn bằng cảm biến như cũ.
- **Bé mặc định** (Cài đặt): chỉ dùng để ghi nhật ký khi không biết bé nào ăn (bấm tay, lịch hẹn giờ, camera chưa nhận ra) và cho khay vệ sinh.
- **Lịch hẹn giờ**: đến giờ (giờ Việt Nam), backend gửi lệnh nếu khay < 10 g; nếu máy offline thì tạo cảnh báo "lỡ bữa".
- **Bình nước**: bơm tuần hoàn chạy liên tục (nguồn riêng, không điều khiển từ xa). Khi mực nước < 20% (hết báo khi ≥ 25%), web/app hiện "Sắp hết nước" và tạo cảnh báo để người dùng đổ thêm; mèo đến **không** bật bơm.
- **Khay vệ sinh**: 1 lượt = từ lúc mèo vào tới lúc ra mà không quay lại trong 15 s (ngắn hơn 5 s thì bỏ qua). Mỗi lượt được ghi vào `litter_logs` của bé đang gán cho máy cho ăn, kèm thời gian ở trong khay. ESP32 giữ tối đa 8 sự kiện khi mất mạng và gửi bù kèm `agoSec` để ghi đúng giờ.

## Nguồn dữ liệu

- `device_settings` (DB): khẩu phần, chế độ, lịch, bé của máy. Đồng bộ xuống ESP32 mỗi khi máy kết nối lại.
- Trạng thái thời gian thực: chỉ trong RAM backend (không ghi DB mỗi 2 giây).
- Nút AUTO vật lý trên máy được đồng bộ ngược về cài đặt.

## Bảo mật

- JWT bắt buộc cho mọi API trừ đăng nhập/đăng ký/ping; secret lấy từ `.env`, app từ chối khởi động nếu thiếu hoặc yếu.
- Đăng ký luôn tạo `ROLE_USER`.
- Người dùng thường chỉ xem/sửa mèo của mình và chỉ gán được mèo của mình cho máy cho ăn.
- Máy là của chung, nhưng thông tin gắn với từng bé (lần ăn cuối, lần đi vệ sinh, bé camera nhận ra, bé của máy, cảnh báo có `pet_id`) chỉ hiện cho chủ của bé và admin (`DeviceService.getState(actor)`, `DashboardService`). Cảnh báo chung của thiết bị (`pet_id` rỗng) ai cũng thấy.
- Dữ liệu mẫu (tài khoản có mật khẩu công khai) mặc định tắt; chỉ bật bằng `AURAPET_DEMO_DATA=true` khi chạy thử.
- Khoá Gemini và mật khẩu broker chỉ nằm trên server.
