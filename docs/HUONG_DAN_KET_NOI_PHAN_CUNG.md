# 🔌 Hướng Dẫn Kết Nối Phần Cứng (ESP32 & Raspberry Pi) Với Website AuraPet

Tài liệu này hướng dẫn chi tiết từng bước cách đấu dây linh kiện điện tử, nạp code cho **ESP32** và kết nối hai chiều với **Website Quản Lý AuraPet**.

---

## 📌 SƠ ĐỒ ĐẤU NỐI DÂY (WIRING DIAGRAM)

### 1. Sơ Đồ Chân Vi Điều Khiển ESP32

```
                       ┌────────────────┐
                       │  ESP32 DevKit  │
                       │                │
(Chân tín hiệu Servo) ─┤ GPIO 18        │
(Chân kích Relay Bơm) ─┤ GPIO 19        │
(Chân còi / LED Xanh) ─┤ GPIO 21        │
(Chân LED Đỏ cảnh báo) ─┤ GPIO 22        │
(Chân Nút bấm vật lý) ─┤ GPIO 23        │
(Cảm biến phát hiện mèo)─┤ GPIO 34 (PIR)  │
                       │                │
             Nguồn 5V ─┤ VIN            │
              Chân Mas ─┤ GND            │
                       └────────────────┘
```

### 2. Chi Tiết Đấu Dây Từng Linh Kiện

| Linh Kiện | Chân Trên Linh Kiện | Nối Tới Chân ESP32 | Ghi Chú |
| :--- | :--- | :--- | :--- |
| **Động Cơ Servo Nhả Hạt** | Dây Cam (Tín hiệu)<br>Dây Đỏ (Nguồn)<br>Dây Nâu (GND) | **GPIO 18**<br>**VIN (5V)**<br>**GND** | Nên dùng nguồn 5V riêng nếu servo lớn (MG996R) để tránh sụt áp ESP32. |
| **Module Rơ-le Máy Bơm Nước** | IN (Tín hiệu)<br>VCC<br>GND | **GPIO 19**<br>**VIN (5V)**<br>**GND** | Đầu ra rơ-le đấu nối tiếp với cực dương nguồn của máy bơm nước mini 5V/12V. |
| **Đèn LED Xanh (Sẵn sàng)** | Cực Dương (+)<br>Cực Âm (-) | **GPIO 21** (qua trở 220Ω)<br>**GND** | Sáng khi hệ thống sẵn sàng nhả thức ăn hoặc tuần hoàn nước. |
| **Đèn LED Đỏ (Cảnh báo)** | Cực Dương (+)<br>Cực Âm (-) | **GPIO 22** (qua trở 220Ω)<br>**GND** | Sáng khi thức ăn/nước bị cạn hoặc kẹt cơ cấu. |
| **Nút Nhấn Thủ Công** | Chân 1<br>Chân 2 | **GPIO 23**<br>**GND** | Sử dụng chế độ điện trở kéo lên nội bộ (`INPUT_PULLUP`). |
| **Cảm Biến Phát Hiện Mèo** | OUT (Tín hiệu)<br>VCC<br>GND | **GPIO 34**<br>**3.3V hoặc 5V**<br>**GND** | Dùng cảm biến hồng ngoại tránh vật cản hoặc cảm biến chuyển động PIR. |

---

## 💻 CÁC BƯỚC NẠP CODE CHO ESP32

Mã nguồn hoàn chỉnh đã được tạo sẵn tại: [firmware/esp32_aurapet/esp32_aurapet.ino](file:///d:/chamsocmeo/firmware/esp32_aurapet/esp32_aurapet.ino).

### Bước 1: Chuẩn bị Arduino IDE
1. Tải và cài đặt phần mềm **Arduino IDE** (phiên bản 2.x) tại [https://www.arduino.cc/en/software](https://www.arduino.cc/en/software).
2. Thêm hỗ trợ bo mạch ESP32:
   - Mở Arduino IDE -> vào **File** -> **Preferences**.
   - Tại dòng **Additional boards manager URLs**, dán đường link:
     ```
     https://raw.githubusercontent.com/espressif/arduino-esp32/gh-pages/package_esp32_index.json
     ```
   - Vào **Tools** -> **Board** -> **Boards Manager** -> tìm kiếm `esp32` và bấm **Install**.

### Bước 2: Cài đặt các thư viện cần thiết
Vào menu **Tools** -> **Manage Libraries...** (hoặc nhấn `Ctrl + Shift + I`), tìm và cài đặt 2 thư viện:
1. `ArduinoJson` (tác giả Benoit Blanchon - khuyên dùng phiên bản 6.x hoặc 7.x).
2. `ESP32Servo` (tác giả Kevin Harrington).

### Bước 3: Cấu hình thông số WiFi & Website trong file code
Mở file [firmware/esp32_aurapet/esp32_aurapet.ino](file:///d:/chamsocmeo/firmware/esp32_aurapet/esp32_aurapet.ino) trong Arduino IDE:
1. Sửa tên và mật khẩu WiFi nhà bạn:
   ```cpp
   const char* WIFI_SSID     = "Ten_WiFi_Nha_Ban";
   const char* WIFI_PASSWORD = "Mat_Khau_WiFi";
   ```
2. Điền địa chỉ máy chủ Website:
   - **Trường hợp 1 (Chạy cục bộ trong nhà)**: Dán địa chỉ IP máy tính đang chạy website (Ví dụ: `http://10.10.59.64:8080`).
   - **Trường hợp 2 (Dùng Cloudflare Tunnel)**: Dán link tunnel có đuôi `.trycloudflare.com`.
   - **Trường hợp 3 (Đã deploy lên Render)**: Dán link `https://aurapet-backend.onrender.com`.

### Bước 4: Nạp code vào ESP32
1. Cắm bo mạch ESP32 vào cổng USB máy tính.
2. Tại Arduino IDE:
   - Mục **Board**: chọn `DOIT ESP32 DEVKIT V1` (hoặc `ESP32 Dev Module`).
   - Mục **Port**: chọn cổng COM tương ứng của ESP32 (ví dụ: `COM3`, `COM4`).
3. Nhấn nút **Upload** (biểu tượng mũi tên sang phải ➡️).
4. Mở cửa sổ **Serial Monitor** (tốc độ baud: `115200`) để quan sát ESP32 kết nối WiFi và nhận lệnh từ website!

---

## 🎯 KIỂM TRA ĐIỀU KHIỂN GIỮA WEBSITE VÀ PHẦN CỨNG

1. **Mở Web Dashboard**:
   - Nhấp đúp vào file [start_dashboard_web.bat](file:///d:/chamsocmeo/start_dashboard_web.bat) trên máy tính.
   - Hoặc mở trên điện thoại bằng địa chỉ LAN: `http://10.10.59.64:8080/index.html`.
2. **Thử nghiệm điều khiển Thức Ăn (Food)**:
   - Trên Web Dashboard, bấm nút **"Cho ăn ngay"** (Machines).
   - 👉 **Kết quả thực tế**: Động cơ Servo trên ESP32 sẽ lập tức quay mở nắp khay nhả hạt, đèn LED xanh nhấp nháy, sau đó tự đóng nắp lại và gửi thông báo về Web đã hoàn thành!
3. **Thử nghiệm điều khiển Nước Uống (Water)**:
   - Trên Web Dashboard, gạt công tắc **"Máy bơm nước"**.
   - 👉 **Kết quả thực tế**: Rơ-le đóng cạch một tiếng, máy bơm nước bắt đầu hoạt động tuần hoàn lọc nước qua than hoạt tính.
4. **Thử nghiệm nút nhấn tại chỗ**:
   - Nhấn nút vật lý (GPIO 23) trên hộp máy.
   - 👉 **Kết quả thực tế**: Thức ăn lập tức được nhả ra và trên Web Dashboard tự động cập nhật nhật ký cho ăn mới nhất!

---

## 🐱 KẾT NỐI VỚI RASPBERRY PI 4 (CAMERA AI)

Nếu bạn có **Raspberry Pi 4** kèm camera để nhận diện mèo:
1. Mở terminal trên Raspberry Pi và chạy lệnh:
   ```bash
   pip install requests opencv-python
   ```
2. Chạy script nhận diện:
   ```bash
   python3 firmware/raspberry_pi/cat_detection_gateway.py
   ```
3. Khi camera nhìn thấy bé mèo đến gần khay ăn/nước, Raspberry Pi sẽ tự động gọi API kích hoạt chu trình bơm nước tươi và định lượng hạt thông minh theo chuẩn lưu đồ Hình 4 & Hình 6!
