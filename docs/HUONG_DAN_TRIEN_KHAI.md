# Hướng dẫn triển khai

## A. Chạy trên máy Windows (demo trong lớp / tại nhà)

1. Cài Java 17, Maven, MariaDB (hoặc dùng Supabase ở mục B).
2. `.env.example` → `.env`, điền:
   - `AURAPET_JWT_SECRET` (≥ 32 ký tự ngẫu nhiên)
   - `DB_*` của MariaDB
   - `MQTT_*` và `MQTT_TOPIC_PREFIX` (giống firmware)
3. Chạy `start_aurapet.bat`. Script nạp `.env`, tự build lại khi code thay đổi, in địa chỉ LAN cho điện thoại.
4. Mở `http://localhost:8080`. Điện thoại cùng Wi-Fi mở `http://<IP-máy-tính>:8080`.
5. Cần truy cập từ 4G/mạng khác: chạy `start_cloudflare_tunnel.bat` để có địa chỉ `https://….trycloudflare.com`.

## B. Cơ sở dữ liệu Supabase

1. Tạo project trên supabase.com (region Singapore).
2. SQL Editor → chạy `supabase/schema.sql`, rồi `supabase/seed.sql` (tài khoản mẫu `admin@aurapet.com / Admin@123`).
3. Project Settings → Database → Connection string (**Transaction pooler**, JDBC).
4. Trong `.env`:
   ```
   SPRING_PROFILES_ACTIVE=supabase
   SUPABASE_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require
   SUPABASE_DB_USER=postgres.<project-ref>
   SUPABASE_DB_PASSWORD=<mật khẩu>
   ```

## C. Backend trên cloud

**Render (miễn phí):** New → Blueprint → chọn repo (dùng `render.yaml`) → điền các biến `SUPABASE_*`, `MQTT_*`, `GEMINI_API_KEY`. Health check: `/actuator/health`.

**VPS có Docker:**
```bash
cp .env.example .env   # điền giá trị thật
docker compose up -d --build aurapet-backend           # dùng Supabase
docker compose --profile local up -d --build           # kèm MariaDB trong Docker (cần DB_ROOT_PASSWORD, DB_USER khác root)
```
Nên đặt Nginx + Let's Encrypt phía trước để có HTTPS.

## D. GitHub

- `setup_git_and_push.bat` khởi tạo và đẩy code. File `.env`, `secrets.h`, file build đều đã nằm trong `.gitignore`.
- GitHub Actions:
  - **Backend CI**: test + build JAR mỗi lần push vào `backend/`.
  - **Docker**: đẩy image lên GHCR.
  - **GitHub Pages**: build Flutter web + copy web dashboard vào `/dashboard/`. Khi mở từ Pages, nhập địa chỉ backend ở mục "Máy chủ" trên màn hình đăng nhập.
  - **APK**: tạo tag `v2.0.0` để build APK và đính kèm vào Release.

## E. App Flutter

```bash
cd mobile
flutter pub get
flutter run                                   # nhập địa chỉ máy chủ ở màn hình đăng nhập
flutter build apk --release --dart-define=BACKEND_URL=https://aurapet-backend.onrender.com
```
App lưu đăng nhập và địa chỉ máy chủ; trên Android có nút tự tìm máy chủ trong cùng Wi-Fi.
