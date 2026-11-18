# Hướng dẫn phần cứng: ESP32 máy cho ăn, ESP32 máy dọn cát + bình nước, Raspberry Pi camera

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

Động cơ bước nên dùng nguồn riêng, nối chung GND với ESP32.

### Dùng ESP32-S3 (code `choan_web_nuoc.ino` / `khay_cat.ino`)
Code tự chọn bảng chân theo board trong Arduino IDE: chọn **Tools → Board → ESP32S3 Dev Module** là dùng chân S3, chọn ESP32 Dev Module là dùng sơ đồ cũ. ESP32-S3 không có GPIO 22–25, GPIO 26–32 nối flash, 33–37 nối PSRAM, ADC chỉ ở GPIO 1–20, nên phải đi lại các dây sau:

| Máy cho ăn | ESP32 cũ | ESP32-S3 |
|---|---|---|
| Cảm biến mèo (IR/PIR) | 19 | **6** |
| Nút Cho ăn / Dừng / Tự động / Trừ bì | 32 / 33 / 25 / 26 | **38 / 39 / 40 / 41** |
| LED đỏ | 27 | **42** |
| DIR động cơ | 23 | **12** |
| LCD SDA / SCL | 21 / 22 | **8 / 9** |
| Cảm biến nước S (ADC) | 34 | **1** |
| Giữ nguyên | TRIG 5, ECHO 18, LED xanh 14, STEP 13, EN 17, HX711 DT 16 / SCK 4, nước + 15 | như cũ |

| Máy dọn cát | ESP32 cũ | ESP32-S3 |
|---|---|---|
| PIR / STEP / DIR / nút dọn | 32 / 26 / 27 / 33 | **4 / 5 / 6 / 7** |
| LCD SDA / SCL | 21 / 22 | **8 / 9** |
| LED đỏ / xanh | 12 / 14 | như cũ |

Nạp lần đầu trên board S3: cắm cổng ghi **COM/UART** (Serial Monitor hiện log ngay). Nếu cắm cổng ghi **USB**, đặt Tools → **USB CDC On Boot: Enabled** thì mới thấy log. Board mới nên cảm biến nước phải hiệu chỉnh lại (`kho` / `day` trong Serial Monitor).

## 2. Nạp firmware

1. Arduino IDE 2.x → Boards Manager → cài **esp32** (Espressif).
2. Library Manager → cài: **WiFiManager** (tzapu), **PubSubClient**, **ArduinoJson** (v7), **HX711** (Bogdan Necula), **LiquidCrystal I2C**.
3. Trong `firmware/esp32_aurapet/`: sao chép `secrets.example.h` thành `secrets.h` và điền:
   - `WIFI_SSID`, `WIFI_PASS`
   - `MQTT_HOST`, `MQTT_PORT`, `MQTT_USE_TLS`, `MQTT_USER`, `MQTT_PASS`
   - `MQTT_CA_CERT` (khi bật TLS): chứng chỉ CA của broker để ESP32 xác thực đúng broker
   - `TOPIC_PREFIX` — **phải giống hệt** `MQTT_TOPIC_PREFIX` trong file `.env` của backend
4. Chọn board *ESP32 Dev Module* → Upload → mở Serial Monitor 115200 để xem log.

`secrets.h` đã nằm trong `.gitignore` nên mật khẩu Wi-Fi không bị đưa lên GitHub.

`firmware/esp32_aurapet` là **bản firmware duy nhất** cho máy cho ăn (bản `esp32_feeder_emqx` cũ đã được gộp vào). Cách chạy bên trong:

- Lệnh MQTT chỉ được ghi nhận trong callback; `loop()` mới thực hiện. Lệnh `FEED` lặp lại trong 3 giây bị bỏ qua; `gram` trong lệnh `FEED` chỉ dùng cho lần đó (khẩu phần mặc định đổi bằng `SET_TARGET`); `STOP` chỉ có tác dụng khi đang nhả hạt.
- Nhả hạt là state machine `IDLE → RUNNING → SETTLING → IDLE`: mỗi vòng `loop()` quay 1 đoạn 800 xung rồi đọc cân, nên MQTT, nút bấm và LCD vẫn chạy trong lúc nhả. Dừng khi đủ gram (`DONE`), bấm dừng (`STOPPED`) hoặc quá 20 giây (`TIMEOUT`).
- Mức hạt trong thùng có độ trễ (báo hết khi > 15 cm, hết báo khi < 13 cm) và giữ số đo cũ 5 giây khi siêu âm mất tín hiệu thoáng qua.

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

## 6. ESP32 máy dọn cát + mực nước bình bơm tuần hoàn

Firmware `firmware/esp32_litter_water/esp32_litter_water.ino`. Bơm tuần hoàn cắm nguồn riêng và **chạy liên tục**; ESP32 không bật/tắt bơm mà chỉ đo mực nước bằng HC-SR04 thứ hai và báo lên web/app khi sắp cạn để người dùng đổ thêm.

| Linh kiện | Chân linh kiện | Chân ESP32 | Ghi chú |
|---|---|---|---|
| Cảm biến mèo PIR | OUT | GPIO 32 | Mức LOW = có mèo (`PIR_MUC_CO_MEO`) |
| Driver động cơ bước cào cát | STEP / DIR | GPIO 26 / 27 | |
| HC-SR04 mực nước | TRIG / ECHO | GPIO 25 / 34 | ECHO 5V qua cầu chia áp (1k nối tiếp, 2k xuống GND) |
| Nút dọn thủ công | 1 chân | GPIO 33 | Chân còn lại nối GND |
| LED đỏ / xanh | Anode qua trở 220 Ω | GPIO 12 / 14 | |
| LCD 16x2 I2C | SDA / SCL | GPIO 21 / 22 | Dòng trên hiện mực nước |

1. Library Manager → cài **PubSubClient** (Nick O'Leary) và **LiquidCrystal I2C**.
2. Sao chép `secrets.example.h` thành `secrets.h`, điền Wi-Fi và MQTT **giống hệt** máy cho ăn (cùng `TOPIC_PREFIX`).
3. Đo bình thật rồi sửa `NUOC_CAN_CM` (0%) và `NUOC_DAY_CM` (100%); `NUOC_THAP_PT = 20` là ngưỡng báo sắp hết nước (hết báo khi lên lại ≥ 25%).
4. Nạp code, mở Serial Monitor 115200: thấy `[MQTT] Da ket noi` và `Nuoc: 45% (4.2 cm)` mỗi 2 giây.

ESP32 gửi `PREFIX/water` mỗi 5 giây (và ngay khi đổi trạng thái), kèm trạng thái khay (sẵn sàng / mèo trong khay / chờ cát vón / đang dọn). Mỗi lượt mèo đi vệ sinh và mỗi lần dọn cát xong được gửi lên `PREFIX/litter` và ghi vào lịch sử vệ sinh của bé đang gán cho máy cho ăn (đổi bé ở **Cài đặt → Thiết bị**). Lượt ngắn hơn 5 giây (`THOI_GIAN_TOI_THIEU_DI_VS`) coi như mèo chỉ ghé qua và không được ghi. Web/app hiện thẻ **Bình nước** với % nước; khi sắp cạn sẽ hiện "Sắp hết nước", thông báo trên đầu trang và mục Hoạt động. Mất Wi-Fi thì máy vẫn dọn cát và đo nước bình thường, chỉ không gửi lên web.

## 7. Camera nhận diện mèo (YOLOv8) — Raspberry Pi 4 hoặc laptop

Script `firmware/raspberry_pi/cat_detection_gateway.py` dùng YOLOv8n (lớp `cat` của COCO). Mỗi khi mèo **đến gần** camera — nhận diện đủ `CAT_STABLE_FRAMES` lần liên tiếp, tâm nằm trong `CAT_ROI` và chiếm ít nhất `CAT_NEAR_AREA` khung hình — Pi gửi `{"catPresent": true}` lên `PREFIX/vision`; khi mèo rời đi gửi `false`. Backend cho ăn nếu đang bật Tự động và khay < 10 g (có chống cho ăn lặp 90 giây). Đồng thời hình trực tiếp có khung đánh dấu mèo được gửi lên web/app.

### Kiểm tra camera trước (khuyên làm trên Pi lần đầu)

`test_camera.py` kiểm tra lần lượt: mở camera và đo khung/giây, phát hiện ảnh đen, nạp YOLOv8 và tìm mèo, đọc tên từng bé (nếu có `models/cat_classifier.pt`), kết nối backend và MQTT. Mỗi bước báo `[OK]` / `[LỖI]` kèm cách sửa, và **không gửi sự kiện nào lên hệ thống** (máy không nhả hạt).

**Pi chạy mô hình bằng ONNX Runtime, không cần PyTorch.** PyTorch bản mới bị lỗi `Illegal instruction` trên Raspberry Pi 4 (CPU Cortex-A72), còn ONNX Runtime chạy ổn định và nhẹ hơn nhiều. Mô hình được xuất sang `.onnx` trên máy tính rồi chép sang Pi:

```powershell
# Trên máy tính Windows (sau mỗi lần train lại mô hình nhận diện từng bé)
cd firmware\raspberry_pi
.\.venv\Scripts\python.exe -m pip install onnx onnxslim     # lần đầu
.\.venv\Scripts\python.exe export_onnx.py                   # -> yolov8n.onnx (640) + models\cat_classifier.onnx
#   Pi chạy chậm: export_onnx.py --imgsz 416 (nhẹ hơn ~2 lần, tầm nhìn gần hơn)
cd ..\..
# Chép đúng các file Pi cần (~20 MB, không chép .venv/.cache/dataset)
#   mặc định nhom4@192.168.99.160; Pi đổi IP thì thêm -PiHost <IP mới> (xem trên Pi: hostname -I)
powershell -ExecutionPolicy Bypass -File scripts\copy_to_pi.ps1
```

```bash
# Trên Pi (Raspberry Pi OS 64-bit)
cd ~/aurapet && ./setup_pi.sh                    # cài thư viện, liệt kê camera đang cắm
source ~/aurapet-venv/bin/activate
python3 test_camera.py                           # camera + AI
python3 test_camera.py --live 30                 # đưa mèo/ảnh mèo trên điện thoại trước camera
AURAPET_SERVER=http://192.168.1.10:8080 MQTT_HOST=broker.emqx.io python3 test_camera.py   # thêm kiểm tra kết nối
```

Ảnh chụp thử được lưu ở `test_snapshot.jpg` và `test_detect.jpg` (có khung nhận diện). Camera module CSI: `sudo apt install -y python3-picamera2` rồi thêm `--picam`. Không có camera: `--image meo.jpg` để thử riêng phần AI.

Chạy thật: `./run_camera.sh` (đọc cấu hình từ `aurapet-camera.env`: MQTT, `CAMERA_KEY`, `AURAPET_SERVER` = IP máy chạy backend). Tự chạy mỗi khi Pi bật nguồn: `./setup_pi.sh --service`, xem log bằng `journalctl -u aurapet-camera -f`.

### Chạy gateway

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
| `CAT_CLASSIFIER_PATH` | `firmware/raspberry_pi/models/cat_classifier.pt` | Đường dẫn mô hình phân loại từng bé mèo |
| `DEMO_PET_NAME` | rỗng | Tên bé mèo giả lập để test nhanh tính năng khi chưa kịp train (vd. `Mimi` hay `Tom`) |

### Nhận ra từng bé bằng Feature Embedding (DINOv2 Re-ID) — cách khuyên dùng
Không cần train: mô hình DINOv2 biến ảnh mèo thành một vector đặc trưng; ảnh cùng một bé cho vector gần nhau.
Mỗi bé chỉ cần **đăng ký 30–50 ảnh**, thêm bé mới không phải train lại. Camera so ảnh mèo đang đứng trước máy với kho ảnh đã đăng ký (cosine similarity), bỏ phiếu qua 5 lần nhìn liên tiếp rồi gửi tên bé → ESP32 nhả đúng khẩu phần của bé đó.

**Đăng ký ngay trên web (khuyên dùng):** mục Thú cưng → Thêm/Sửa bé → khung "Camera nhận diện bé":
- **Đưa bé ra trước camera**: camera trên Pi tự chụp 10 ảnh, mỗi tư thế một ảnh (cắt sát con mèo, không có khung vẽ). Trong lúc chụp máy **không** nhả hạt.
- **Thêm 5–10 ảnh của bé**: chọn ảnh trong máy, web thu nhỏ còn 640 px rồi gửi lên. Pi tự tìm con mèo trong ảnh.
Pi hỏi ảnh mới mỗi 20 giây, tính vector đặc trưng rồi báo lại. Ảnh không thấy mèo bị gắn nhãn đỏ "không thấy mèo", nên xoá. Huy hiệu chuyển thành "Camera đã nhận ra bé" khi đủ 5 ảnh đã học. Pi chỉ cần có `models/cat_embedder.onnx` (bước 1 dưới đây), không cần chạy `enroll_cats.py`.

1. **Trên máy tính, làm một lần** (cần Internet để tải trọng số DINOv2 ~85 MB):
   ```powershell
   cd firmware\raspberry_pi
   .\.venv\Scripts\python.exe export_embedder.py        # -> models\cat_embedder.onnx (ViT-S/14, ảnh 224)
   ```
   Pi chạy chậm thì xuất `--imgsz 168` (nhanh hơn ~1,8 lần).
2. **Đăng ký ảnh từng bé**: mỗi bé một thư mục `gallery/<Tên bé>/`, tên thư mục **trùng tên trên web**. Thêm thư mục `gallery/Khac/` chứa ảnh mèo lạ nếu muốn máy không cho mèo hàng xóm ăn.
   ```powershell
   .\.venv\Scripts\python.exe enroll_cats.py --src gallery     # không có gallery\ thì dùng dataset\train
   ```
   Script tự cắt con mèo bằng YOLOv8, lưu `models\cat_gallery.npz`. Nếu có `dataset\val`, script đo độ chính xác và tự chọn ngưỡng.
   Có thể chụp thẳng bằng camera trên Pi (cho kết quả tốt nhất vì đúng góc và ánh sáng thật):
   ```bash
   sudo systemctl stop aurapet-camera
   python3 enroll_cats.py --camera --name Mimi --count 40
   sudo systemctl start aurapet-camera
   ```
3. Chép sang Pi bằng `scripts\copy_to_pi.ps1`, rồi khởi động lại dịch vụ camera. Log sẽ hiện `[AI] Re-ID (Feature Embedding dinov2_vits14 ...)`.

| Biến | Mặc định | Ý nghĩa |
|------|----------|---------|
| `RECOGNIZER` | `auto` | `auto`: có `cat_embedder.onnx` + `cat_gallery.npz` thì dùng Re-ID, không thì dùng classifier cũ. `reid` / `classifier`: ép một cách |
| `REID_THRESHOLD` | ngưỡng lưu trong gallery (~0.6) | Điểm cosine tối thiểu để nhận là một bé. Hay nhận nhầm thì tăng, hay báo "Meo" thì giảm |
| `REID_MARGIN` | `0.03` | Bé đứng đầu phải hơn bé thứ hai ít nhất chừng này |
| `REID_VOTES` | `5` | Số lần nhìn gần nhất được bỏ phiếu |
| `REID_UNKNOWN` | `meo` | Mèo không khớp bé nào: `meo` = nhả khẩu phần chung; `khac` = coi là mèo lạ, không nhả |

### Huấn luyện nhận diện từng bé mèo (Classifier cũ, dùng khi chưa có Re-ID)
1. Chuẩn bị ảnh bằng `prepare_sample_dataset.py` (tự chia train/val, bỏ ảnh hỏng/nhoè), chọn 1 trong 2 cách:
   - **Chạy thử khi chưa có ảnh bé nhà mình** — lấy ảnh mẫu từ bộ [Oxford-IIIT Pet](https://www.robots.ox.ac.uk/~vgg/data/pets/) (CC BY-SA 4.0, tự tải ~792 MB một lần vào `firmware/raspberry_pi/.cache/`). Mỗi "bé" là một giống mèo khác nhau:
     `python firmware/raspberry_pi/prepare_sample_dataset.py oxford --pets "Mimi=Siamese,Tom=Bengal"`
     (tránh giống Sphynx: mèo không lông bị YOLOv8 nhìn nhầm thành chó, camera hầu như không phát hiện được)
   - **Bé thật** — quay vài video ngắn (nhiều góc, nhiều ánh sáng, nên quay bằng chính camera của Pi) rồi cắt ảnh:
     `python firmware/raspberry_pi/prepare_sample_dataset.py video --name Mimi --video mimi1.mp4 mimi2.mp4`
     Thêm lớp mèo lạ để mô hình biết khi nào không phải bé nhà mình: `... oxford --pets "" --with-other`
   Tên bé phải **trùng tên trong app AuraPet** để nhật ký ăn được ghi đúng bé. Mỗi bé đặt **khẩu phần riêng** trong mục Thú cưng (ô "Khẩu phần khi camera nhận ra bé"): camera nhận ra bé nào thì máy nhả đúng khẩu phần của bé đó, nhận ra mèo lạ (lớp `Khac`) thì không nhả. Cách thủ công vẫn dùng được: `train_cat_classifier.py --setup --names "Mimi,Tom"` rồi tự chép 30–50 ảnh/bé vào `dataset/train/<Tên_Bé>`.
2. Chạy huấn luyện: `python firmware/raspberry_pi/train_cat_classifier.py --train --epochs 40`
Mô hình tự động xuất sang PyTorch (`.pt`) và ONNX (`.onnx`) lưu trong `firmware/raspberry_pi/models/`. Gateway sẽ tự động phát hiện và nhận diện chính xác từng bé để phân bổ khẩu phần và ghi nhật ký ăn riêng.

Nếu camera lỗi, script chỉ báo lỗi và thử mở lại — **không** giả lập "có mèo". Muốn dùng model tự train, đặt `YOLO_MODEL=đường/dẫn/best.pt` (và sửa `CAT_CLASS_ID` nếu lớp mèo không phải 15).
