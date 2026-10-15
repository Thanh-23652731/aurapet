# 🚀 HƯỚNG DẪN TRIỂN KHAI BACKEND & DATABASE LÊN CLOUD VPS (24/7)

Hệ thống **Smart Pet Care** (AuraPet) hỗ trợ triển khai 24/7 trên môi trường đám mây giúp:
- **Tắt máy tính cá nhân ở nhà**, ứng dụng điện thoại và web vẫn kết nối, điều khiển và nhận dữ liệu thời gian thực mọi lúc mọi nơi.
- Chia sẻ cho tất cả thành viên trong gia đình cùng theo dõi bé mèo qua 4G/5G hoặc bất kỳ mạng Wi-Fi nào.

---

## 🌟 PHẦN 1: SO SÁNH CÁC PHƯƠNG ÁN TRIỂN KHAI

| Tiêu chí | Lựa chọn A: Nền tảng Miễn phí (Render/Railway/Aiven) | Lựa chọn B: Cloud VPS riêng (Khuyên dùng) |
| :--- | :--- | :--- |
| **Chi phí** | **0 VNĐ / tháng** | ~70.000đ - 120.000đ / tháng (hoặc 0đ với Oracle Cloud Free Tier) |
| **Độ trễ (Ping)** | 80ms - 200ms (Server quốc tế) | 5ms - 20ms (VPS Việt Nam: Vietnix, BKNS, Viettel IDC,...) |
| **Thời gian khởi động** | Có thể bị sleep (ngủ) sau 15p không có request (gói Free) | **Chạy 24/7/365 liên tục**, không bao giờ bị gián đoạn |
| **Độ bảo mật** | Dùng chung tài nguyên | Toàn quyền root, IP tĩnh riêng biệt |

---

## 🛠️ PHẦN 2: TRIỂN KHAI LÊN CLOUD VPS RIÊNG VỚI DOCKER (KHUYÊN DÙNG)

Dự án đã được cấu hình sẵn **`Dockerfile`** đa tầng tối ưu hóa và tệp **`docker-compose.yml`** tự động hóa 100%.

### Bước 1: Chuẩn bị máy chủ VPS
1. Thuê một VPS (hệ điều hành **Ubuntu 22.04 LTS** hoặc **Ubuntu 24.04 LTS**).
   - *Gợi ý nhà cung cấp uy tín giá rẻ*: Vietnix, TinoHost, BKNS (tại VN) hoặc Hetzner, DigitalOcean, Oracle Cloud Free Tier.
2. Kết nối SSH vào VPS qua Terminal hoặc PuTTY:
   ```bash
   ssh root@<IP_VPS_CUA_BAN>
   ```

### Bước 2: Cài đặt Docker & Docker Compose trên VPS
Chạy lệnh tự động cài đặt Docker chính thức:
```bash
curl -fsSL https://get.docker.com -o get-docker.sh
sh get-docker.sh
apt-get install -y docker-compose-plugin git
```

### Bước 3: Đưa mã nguồn lên VPS & Khởi chạy hệ thống
1. Tải thư mục dự án lên VPS (hoặc dùng `git clone`):
   ```bash
   mkdir -p /opt/smartpetcare
   cd /opt/smartpetcare
   ```
2. Tạo tệp `.env` để bảo vệ mật khẩu (sao chép từ `.env.example`):
   ```bash
   nano .env
   ```
   *Nội dung mẫu:*
   ```ini
   DB_ROOT_PASSWORD=MatKhauRootSieuManh2026!
   DB_NAME=aurapetdb
   DB_USER=aurapet
   DB_PASSWORD=MatKhauDatabase123!
   JWT_SECRET=404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970
   PORT=8080
   ```
3. **Khởi chạy toàn bộ hệ thống với 1 lệnh duy nhất**:
   ```bash
   docker compose up -d --build
   ```

4. Kiểm tra trạng thái:
   ```bash
   docker ps
   ```
   Bạn sẽ thấy 2 container đang chạy song song:
   - `aurapet-db` (MariaDB 11.4)
   - `aurapet-backend` (Spring Boot Java 17)

### Bước 4: Mở cổng Firewall trên VPS
Đảm bảo VPS đã mở cổng `8080` (hoặc `80`/`443` nếu dùng Domain):
```bash
sudo ufw allow 8080/tcp
sudo ufw allow 80/tcp
sudo ufw allow 443/tcp
sudo ufw reload
```

👉 **Kiểm tra hoạt động**: Mở trình duyệt truy cập: `http://<IP_VPS>:8080/swagger-ui.html`

---

## 🌐 PHẦN 3: CẤU HÌNH DOMAIN & HTTPS (SSL MIỄN PHÍ) VỚI NGINX & CERTBOT (NÂNG CAO)

Để ứng dụng Android / iOS gọi API qua kết nối mã hóa an toàn `https://api.yourdomain.com`:

1. Cài Nginx & Certbot trên VPS:
   ```bash
   sudo apt install -y nginx certbot python3-certbot-nginx
   ```
2. Cấu hình Reverse Proxy Nginx (`/etc/nginx/sites-available/smartpetcare`):
   ```nginx
   server {
       server_name api.yourdomain.com;

       location / {
           proxy_pass http://localhost:8080;
           proxy_set_header Host $host;
           proxy_set_header X-Real-IP $remote_addr;
           proxy_set_header X-Forwarded-For $proxy_add_x_forwarded_for;
           proxy_set_header X-Forwarded-Proto $scheme;
       }
   }
   ```
3. Kích hoạt và cấp chứng chỉ SSL tự động gia hạn:
   ```bash
   sudo ln -s /etc/nginx/sites-available/smartpetcare /etc/nginx/sites-enabled/
   sudo nginx -t && sudo systemctl reload nginx
   sudo certbot --nginx -d api.yourdomain.com
   ```

---

## ☁️ PHẦN 4: TRIỂN KHAI MIỄN PHÍ 0Đ (RENDER + AIVEN / RAILWAY)

Nếu bạn chưa muốn thuê VPS trả phí và muốn thử nghiệm 0đ:
1. **Database MariaDB / MySQL**:
   - Đăng ký tài khoản miễn phí tại **Aiven.io** hoặc **Clever Cloud**.
   - Tạo 1 Database MySQL/MariaDB miễn phí $\rightarrow$ Lấy `Host`, `Port`, `User`, `Password`, `Database Name`.
2. **Spring Boot Backend**:
   - Đẩy mã nguồn thư mục `backend/` lên một repository GitHub riêng.
   - Truy cập **Render.com** $\rightarrow$ Chọn **New Web Service** $\rightarrow$ Kết nối repo GitHub của bạn.
   - Environment: **Docker** (Render sẽ tự động đọc `backend/Dockerfile`).
   - Thêm các biến môi trường (Environment Variables) trong Render Dashboard:
     - `SPRING_DATASOURCE_URL`: `jdbc:mariadb://<HOST_AIVEN>:<PORT>/<DB_NAME>?sslmode=require`
     - `SPRING_DATASOURCE_USERNAME`: `<USER_AIVEN>`
     - `SPRING_DATASOURCE_PASSWORD`: `<PASSWORD_AIVEN>`
     - `AURAPET_JWT_SECRET`: `404E635266556A586E3272357538782F413F4428472B4B6250645367566B5970`
   - Bấm **Deploy**. Sau 2-3 phút, bạn sẽ nhận được đường dẫn dạng: `https://smartpetcare-backend.onrender.com`.

---

## 📱 PHẦN 5: KẾT NỐI APP MOBILE & WEB VÀO CLOUD BACKEND

Sau khi có địa chỉ Cloud URL (ví dụ: `http://103.xxx.xxx.xxx:8080` hoặc `https://api.yourdomain.com`):

### Cách 1: Thay đổi trực tiếp trong mã nguồn Mobile (Đơn giản nhất)
Mở file `mobile/lib/core/constants/api_endpoints.dart`:
```dart
class ApiEndpoints {
  // Điền địa chỉ Cloud VPS hoặc Render vào đây:
  static const String cloudBackendUrl = 'https://api.yourdomain.com';
  // Hoặc: static const String cloudBackendUrl = 'http://103.xxx.xxx.xxx:8080';
```

### Cách 2: Build APK với cờ tham số mà không cần sửa code
```bash
cd mobile
flutter build apk --release --dart-define=BACKEND_URL=https://api.yourdomain.com
```

File APK xuất ra sẽ tự động kết nối vào Cloud VPS của bạn 24/7 bất kể điện thoại đang ở đâu!
