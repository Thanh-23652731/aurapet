# Hướng dẫn phần cứng: ESP32 máy cho ăn và Raspberry Pi camera

## 1. Đấu dây ESP32 (đúng với firmware `esp32_aurapet.ino`)

| Linh kiện | Chân linh kiện | Chân ESP32 | Ghi chú |
|---|---|---|---|
| Driver động cơ bước (TMC2209/A4988) | STEP / DIR / EN | GPIO 13 / 23 / 17 | EN mức thấp = bật; vi bước 1/16 (3200 xung/vòng) |
| Cân HX711 | DT / SCK | GPIO 16 / 4 | Hiệu chuẩn bằng `CALIB_FACTOR` |
| Siêu âm HC-SR04 (đo thùng chứa) | TRIG / ECHO | GPIO 5 / 18 | ECHO 5V nên qua cầu chia áp về 3.3V |
| Cảm biến phát hiện mèo (IR/PIR) | OUT | GPIO 19 | Mặc định mức LOW = có mèo (`SENSOR_ACTIVE_LOW`) |
| Nút Cho ăn / Dừng / Tự động / Trừ bì | 1 chân | GPIO 32 / 33 / 25 / 26 | Chân còn lại nối GND (dùng `INPUT_PULLUP`) |
| LED đỏ / LED xanh | Anode qua trở 220 Ω | GPIO 27 / 14 | Đỏ = sắp hết hạt |
| LCD 16x2 I2C | SDA / SCL | GPIO 21 / 22 | Địa chỉ 0x27 hoặc 0x3F (tự dò) |
| Bơm nước (tuỳ chọn) qua relay/MOSFET | IN | `PUMP_PIN` (gợi ý GPIO 15) | Mặc định `-1` = chưa lắp; web/app sẽ báo "Chưa lắp bơm" |

Động cơ bước và bơm nên dùng nguồn riêng, nối chung GND với ESP32.

## 2. Nạp firmware

1. Arduino IDE 2.x → Boards Manager → cài **esp32** (Espressif).
2. Library Manager → cài: **WiFiManager** (tzapu), **PubSubClient**, **ArduinoJson** (v7), **HX711** (Bogdan Necula), **LiquidCrystal I2C**.
3. Trong `firmware/esp32_aurapet/`: sao chép `secrets.example.h` thành `secrets.h` và điền:
   - `WIFI_SSID`, `WIFI_PASS`
   - `MQTT_HOST`, `MQTT_PORT`, `MQTT_USE_TLS`, `MQTT_USER`, `MQTT_PASS`
   - `TOPIC_PREFIX` — **phải giống hệt** `MQTT_TOPIC_PREFIX` trong file `.env` của backend
4. Nếu đã lắp bơm: sửa `#define PUMP_PIN -1` thành chân thật (ví dụ `15`).
5. Chọn board *ESP32 Dev Module* → Upload → mở Serial Monitor 115200 để xem log.

`secrets.h` đã nằm trong `.gitignore` nên mật khẩu Wi-Fi không bị đưa lên GitHub.

## 3. Đổi Wi-Fi không cần nạp lại code

Wi-Fi trong `secrets.h` chỉ dùng cho lần đầu. Muốn chuyển máy sang mạng khác:

1. **Giữ nút TRỪ BÌ (GPIO 26)** rồi cắm điện (hoặc bấm RESET), giữ khoảng 2 giây cho tới khi LCD hiện `Cai WiFi: ket noi / AuraPet-Setup`.
   Nếu máy không vào được mạng đã lưu (ví dụ mang sang chỗ khác), chế độ này **tự mở** sau khoảng 15 giây, không cần bấm nút.
2. Trên điện thoại, kết nối Wi-Fi **AuraPet-Setup**, mật khẩu **aurapet123** (đổi được bằng `SETUP_AP_PASS` trong `secrets.h`).
3. Trang cài đặt tự bật lên. Nếu không thấy, mở trình duyệt và vào `192.168.4.1`.
4. Bấm **Configure WiFi**, chọn mạng 2.4 GHz, nhập mật khẩu rồi bấm **Save**. Máy tự khởi động lại và vào mạng mới. LCD hiện tên Wi-Fi và IP.

Sau 3 phút không cài đặt, máy thoát chế độ này và chạy offline: nút bấm và tự cho ăn bằng cảm biến vẫn hoạt động. Mạng mới được lưu trong ESP32 nên lần cắm điện sau không cần cài lại.

## 4. Chọn MQTT broker

| Lựa chọn | Cấu hình | Khi nào dùng |
|---|---|---|
| **EMQX Serverless** (khuyên dùng) | Tạo deployment miễn phí trên emqx.com → thêm user/pass → cổng **8883**, TLS bật | Demo thật, dùng lâu dài |
| broker.emqx.io (công cộng) | Cổng 1883, không mật khẩu | Thử nghiệm nhanh. Ai biết topic đều gửi được lệnh, nên `TOPIC_PREFIX` phải là chuỗi ngẫu nhiên |

Backend `.env` và `secrets.h` phải dùng cùng broker và cùng prefix.

## 5. Kiểm tra

1. Chạy backend (`start_aurapet.bat`), log phải có `[MQTT] Đã kết nối broker`.
2. Cấp nguồn ESP32. Trên web, thanh trạng thái chuyển sang **Máy cho ăn đang hoạt động**, LCD hiện dấu `*`.
3. Bỏ hết hạt khỏi khay → bấm **Trừ bì cân về 0 g**.
4. Bấm **Cho ăn 30 g**: nút chuyển "Đang nhả hạt…", hiện nút **Dừng ngay**; khi xong, mục Hoạt động hiện "Đã nhả 29g cho …".
5. Rút nguồn ESP32: sau vài giây web báo offline và nút cho ăn bị khoá.

| Hiện tượng | Cách xử lý |
|---|---|
| Web báo "Máy chủ chưa kết nối MQTT" | Kiểm tra `MQTT_HOST/PORT/TLS/USERNAME/PASSWORD` trong `.env` |
| Web báo máy offline dù ESP32 đã có Wi-Fi | `TOPIC_PREFIX` không khớp `MQTT_TOPIC_PREFIX`, hoặc sai broker |
| Cảnh báo "động cơ chạy nhưng cân không tăng" | Thùng hết hạt, kẹt vít tải, hoặc cân chưa hiệu chuẩn |
| Số gram âm/lệch | Bỏ khay trống → Trừ bì; chỉnh `CALIB_FACTOR` |

## 6. Camera nhận diện mèo (YOLOv8) — Raspberry Pi 4 hoặc laptop

Script `firmware/raspberry_pi/cat_detection_gateway.py` dùng YOLOv8n (lớp `cat` của COCO). Mỗi khi mèo **đến gần** camera — nhận diện đủ `CAT_STABLE_FRAMES` lần liên tiếp, tâm nằm trong `CAT_ROI` và chiếm ít nhất `CAT_NEAR_AREA` khung hình — Pi gửi `{"catPresent": true}` lên `PREFIX/vision`; khi mèo rời đi gửi `false`. Backend tự quyết định bật bơm hoặc cho ăn theo cài đặt (có chống cho ăn lặp 90 giây). Đồng thời hình trực tiếp có khung đánh dấu mèo được gửi lên web/app.

```bash
pip install -r firmware/raspberry_pi/requirements.txt
export MQTT_HOST=... MQTT_PORT=... MQTT_TLS=1 MQTT_USERNAME=... MQTT_PASSWORD=...
export MQTT_TOPIC_PREFIX=...                                     # giống backend
export AURAPET_SERVER=http://192.168.0.25:8080 CAMERA_KEY=...    # xem hình trên web (tuỳ chọn)
export YOLO_IMGSZ=320                                            # Pi 4 chạy nhanh hơn
python3 firmware/raspberry_pi/cat_detection_gateway.py
```

Chạy thử trên laptop Windows với webcam USB (PowerShell):

```powershell
pip install -r firmware\raspberry_pi\requirements.txt opencv-python
$env:MQTT_TOPIC_PREFIX="..."; $env:CAMERA_INDEX="1"; $env:SHOW_PREVIEW="1"
python firmware\raspberry_pi\cat_detection_gateway.py
```

| Biến | Mặc định | Ý nghĩa |
|---|---|---|
| `CAMERA_INDEX` | `0` | Webcam USB cắm ngoài laptop thường là `1` (tự thử `2` nếu `1` không có) |
| `YOLO_MODEL` | `yolov8n.pt` cạnh script | Lần đầu ultralytics tự tải về nếu chưa có |
| `CAT_CONFIDENCE` | `0.6` | Ngưỡng tin cậy |
| `CAT_STABLE_FRAMES` | `3` | Số lần liên tiếp để xác nhận mèo đến / rời đi |
| `CAT_ROI` | cả khung | Vùng quan sát `x1,y1,x2,y2` (pixel trên khung 640x480), vd. quanh khay ăn |
| `CAT_NEAR_AREA` | `0` | Tỉ lệ diện tích tối thiểu, vd. `0.05` = mèo phải chiếm ≥ 5% khung hình (bỏ qua mèo ở xa) |
| `REPORT_INTERVAL` | `10` | Mèo vẫn ở đó thì nhắc lại tối đa mỗi 10 giây |
| `CAMERA_FPS` / `STREAM_FPS` | `60` / `60` | Khung/giây xin từ camera (chế độ MJPG) và gửi lên web. Tốc độ thật phụ thuộc webcam (nhiều loại chỉ 30) và Wi-Fi; log in ra con số thực tế mỗi 15 giây |
| `JPEG_QUALITY` | `70` | Giảm 50–60 nếu hình bị giật vì Wi-Fi yếu |
| `TORCH_THREADS` | nửa số nhân CPU | Số nhân dành cho YOLO, phần còn lại lo hình trực tiếp |
| `SHOW_PREVIEW` | `0` | `1` = mở cửa sổ xem trước (cần `opencv-python`), nhấn `q` để thoát |

Nếu camera lỗi, script chỉ báo lỗi và thử mở lại — **không** giả lập "có mèo". Muốn dùng model tự train, đặt `YOLO_MODEL=đường/dẫn/best.pt` (và sửa `CAT_CLASS_ID` nếu lớp mèo không phải 15).
