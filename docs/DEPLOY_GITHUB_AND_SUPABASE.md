# 🌐 Hướng Dẫn Chi Tiết Deploy AuraPet Lên GitHub & Supabase

Tài liệu này hướng dẫn trọn gói từng bước triển khai hệ sinh thái **AuraPet — AIoT Smart Pet Care** lên **GitHub** (quản lý mã nguồn, CI/CD, GitHub Pages, Docker Registry) và **Supabase** (Cơ sở dữ liệu PostgreSQL Cloud tốc độ cao).

---

## 📌 Kiến Trúc Triển Khai (Cloud Architecture)

```
┌─────────────────────────────────────────────────────────────┐
│                      GITHUB REPOSITORY                      │
│                                                             │
│  ├── .github/workflows/backend-ci.yml     (Test & Build JAR)│
│  ├── .github/workflows/deploy-ghpages.yml (Deploy Web App)  │
│  ├── .github/workflows/docker-publish.yml (Publish to GHCR) │
│  └── .github/workflows/mobile-release.yml (Build APK App)   │
└───────────────────────┬─────────────────────────────────────┘
                        │
       ┌────────────────┴────────────────┐
       ▼                                 ▼
┌───────────────────────┐       ┌────────────────────────┐
│  GITHUB PAGES (HOST)  │       │  CLOUD BACKEND SERVICE │
│  (Web Dashboard &     │       │  (Render / Railway /   │
│   Flutter Web App)    │──────▶│   Docker VPS Container)│
│                       │       │  Spring Boot 3.2.x     │
└───────────────────────┘       └───────────┬────────────┘
                                            │
                                            ▼
                                ┌────────────────────────┐
                                │   SUPABASE DATABASE    │
                                │   Managed PostgreSQL   │
                                │   Pooler: Port 6543    │
                                └────────────────────────┘
```

---

## BƯỚC 1: KHỞI TẠO CƠ SỞ DỮ LIỆU TRÊN SUPABASE

### 1.1. Tạo Project Supabase mới
1. Truy cập [https://supabase.com](https://supabase.com) và đăng nhập (khuyên dùng tài khoản GitHub).
2. Nhấn nút **New Project**.
3. Điền thông tin:
   - **Name**: `aurapet-db`
   - **Database Password**: Đặt mật khẩu an toàn (ví dụ: `AuraPet@Password2026`) và **lưu lại**.
   - **Region**: Chọn `Singapore (ap-southeast-1)` (khu vực có ping thấp nhất về Việt Nam).
4. Nhấn **Create new project** và đợi khoảng 1 phút.

### 1.2. Tạo bảng & Nạp dữ liệu mẫu
1. Trên thanh công cụ bên trái của Supabase, mở **SQL Editor** (biểu tượng `>_`).
2. Mở file [schema.sql](file:///d:/chamsocmeo/supabase/schema.sql) trong project này:
   - Sao chép toàn bộ nội dung và dán vào SQL Editor.
   - Bấm nút **RUN** để tạo 9 bảng (`users`, `pets`, `weight_records`, `feeding_logs`, `litter_logs`, `alerts`, `system_status`, `system_food`, `system_water`).
3. Mở tiếp file [seed.sql](file:///d:/chamsocmeo/supabase/seed.sql):
   - Sao chép và dán vào SQL Editor, bấm **RUN** để nạp tài khoản admin, user và thông tin thú cưng ban đầu.
4. Chuyển sang mục **Table Editor** để xác nhận dữ liệu đã hiển thị đầy đủ.

### 1.3. Lấy chuỗi kết nối JDBC
1. Vào **Project Settings** (biểu tượng bánh răng ⚙️ ở góc dưới trái) -> chọn **Database**.
2. Cuộn xuống mục **Connection string**:
   - Chọn tab **URI** hoặc **JDBC**.
   - Chọn chế độ **Connection Pooling (Transaction mode - Port 6543)** (chế độ này hỗ trợ kết nối mạng IPv4 và chịu tải nhiều connection):
     ```
     jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require
     ```
   - **User**: `postgres.[PROJECT_REF]` (ví dụ: `postgres.abcdefghijklmnop`)
   - **Password**: Mật khẩu database bạn đã tạo.

---

## BƯỚC 2: ĐẨY TOÀN BỘ PROJECT LÊN GITHUB

### Cách 1: Sử dụng file chạy tự động (Nhanh nhất)
1. Chạy file [setup_git_and_push.bat](file:///d:/chamsocmeo/setup_git_and_push.bat).
2. File sẽ tự kiểm tra Git, thêm các file theo chuẩn `.gitignore`, tạo commit và hỏi đường link GitHub repo của bạn để push.

### Cách 2: Chạy lệnh Git thủ công trong PowerShell
1. Mở trang [https://github.com/new](https://github.com/new) và tạo một repository mới (ví dụ: `aurapet`).
2. Mở PowerShell tại thư mục `d:\chamsocmeo`:
   ```powershell
   # 1. Khởi tạo repository
   git init -b main

   # 2. Thêm tất cả file (đã lọc các file rác và bí mật qua .gitignore)
   git add .

   # 3. Tạo commit
   git commit -m "feat: complete AuraPet fullstack AIoT ecosystem"

   # 4. Gắn remote repository GitHub của bạn
   git remote add origin https://github.com/<USERNAME>/<REPO_NAME>.git

   # 5. Đẩy code lên GitHub
   git push -u origin main
   ```

---

## BƯỚC 3: KÍCH HOẠT GITHUB PAGES & GITHUB ACTIONS

Dự án đã được tích hợp sẵn 4 workflow tự động trong thư mục `.github/workflows/`:

| Tên Workflow | File | Mục đích |
| :--- | :--- | :--- |
| **Backend CI** | `backend-ci.yml` | Tự động biên dịch Maven và chạy 16 bài kiểm thử mỗi khi push |
| **Deploy Web** | `deploy-ghpages.yml` | Tự động build và xuất bản Web Dashboard lên GitHub Pages |
| **Docker GHCR** | `docker-publish.yml` | Đóng gói Docker Container đẩy lên GitHub Container Registry |
| **Mobile Release** | `mobile-release.yml` | Tự động build file Android APK mỗi khi gắn thẻ phiên bản (tag `v*`) |

### Kích hoạt GitHub Pages để xem giao diện Web:
1. Mở repository của bạn trên GitHub.
2. Vào **Settings** -> mục **Pages** (ở menu bên trái).
3. Trong mục **Build and deployment** -> **Source**: chọn **GitHub Actions**.
4. Workflow `Deploy Web to GitHub Pages` sẽ tự động kích hoạt.
5. Sau khi chạy xong (khoảng 2 phút), web của bạn sẽ hoạt động trực tiếp tại địa chỉ:
   ```
   https://<USERNAME>.github.io/<REPO_NAME>/
   ```
   *(Trang quản lý IoT Dashboard chuyên sâu tại: `https://<USERNAME>.github.io/<REPO_NAME>/dashboard/`)*

---

## BƯỚC 4: DEPLOY BACKEND LÊN CLOUD KẾT NỐI SUPABASE

Vì GitHub Pages chỉ lưu trữ giao diện tĩnh (Frontend/HTML), bạn có thể đưa Backend Spring Boot lên một dịch vụ Cloud miễn phí để chạy 24/7 kết nối với Supabase:

### Cách 1: Deploy lên Render (Miễn phí, 1-Click với Blueprint)
1. Đăng ký tài khoản tại [https://render.com](https://render.com) (đăng nhập bằng GitHub).
2. Nhấn nút **New +** -> chọn **Blueprint**.
3. Chọn repository `aurapet` trên GitHub của bạn. Render sẽ tự động đọc file [render.yaml](file:///d:/chamsocmeo/render.yaml).
4. Điền 3 biến môi trường Supabase:
   - `SUPABASE_DB_URL`: Chuỗi JDBC pooler từ Bước 1 (Port 6543, `?sslmode=require`).
   - `SUPABASE_DB_USER`: `postgres.[PROJECT_REF]`
   - `SUPABASE_DB_PASSWORD`: Mật khẩu Supabase của bạn.
5. Nhấn **Apply**. Backend sẽ tự động build từ Dockerfile và cấp phát URL dạng:
   `https://aurapet-backend.onrender.com`.

### Cách 2: Chạy Docker Container trên VPS / Server riêng
1. Tạo file `.env` trên VPS dựa trên file [.env.supabase.example](file:///d:/chamsocmeo/.env.supabase.example):
   ```bash
   SUPABASE_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require
   SUPABASE_DB_USER=postgres.your-ref
   SUPABASE_DB_PASSWORD=your-password
   AURAPET_JWT_SECRET=d774dfc564fce6b2d9d56de4985e8152f0e3bb944f53c1f87bb15bc2d4a11ff2
   ```
2. Khởi chạy bằng Docker Compose:
   ```bash
   docker compose -f docker-compose.supabase.yml up -d
   ```

---

## BƯỚC 5: KẾT NỐI FRONTEND & MOBILE APP VỚI HỆ THỐNG MỚI

### 1. Trên Web Dashboard (khi mở qua GitHub Pages)
- Khi truy cập Web Dashboard trên GitHub Pages, ở màn hình Đăng nhập có sẵn nút:
  **"⚙️ Cấu hình máy chủ API (Deploy / Tunnel)"**.
- Nhấn vào nút này và dán URL backend đã deploy (Ví dụ: `https://aurapet-backend.onrender.com` hoặc link Cloudflare Tunnel).
- Trình duyệt sẽ lưu cấu hình vào bộ nhớ máy và kết nối thông suốt với Supabase.

### 2. Trên Ứng dụng Di động Flutter
- Trong file `mobile/lib/core/constants/api_endpoints.dart`:
  ```dart
  static const String cloudBackendUrl = 'https://aurapet-backend.onrender.com';
  ```
- Hoặc người dùng có thể chạm vào nút **[Máy chủ]** ở góc màn hình đăng nhập của App di động để nhập URL server bất kỳ lúc nào mà không cần build lại APK.

---

## 🎯 Kiểm Tra Hoàn Tất

- [x] Cơ sở dữ liệu Supabase đang hoạt động, có đủ 9 bảng và dữ liệu mẫu.
- [x] Mã nguồn đã được push lên GitHub với `.gitignore` bảo vệ tuyệt đối thông tin bảo mật.
- [x] GitHub Actions tự động kiểm thử code mỗi khi commit.
- [x] Web Dashboard có thể xem trực tuyến trên GitHub Pages.
- [x] Backend kết nối trực tiếp đến Supabase PostgreSQL qua Transaction Pooler.
