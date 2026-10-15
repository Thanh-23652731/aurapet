# 🚀 AuraPet — Supabase Cloud Setup Guide

Tài liệu hướng dẫn kết nối hệ thống **AuraPet** với cơ sở dữ liệu **Supabase (PostgreSQL Cloud)**.

---

## 1. Tạo Project trên Supabase

1. Truy cập [https://supabase.com](https://supabase.com) và đăng nhập (bằng tài khoản GitHub).
2. Bấm **"New project"**.
3. Điền thông tin:
   - **Name**: `aurapet-db`
   - **Database Password**: Nhập mật khẩu mạnh (VD: `AuraPet@Pass2026`) và **lưu lại mật khẩu này**.
   - **Region**: Chọn khu vực gần bạn nhất (ví dụ: `Singapore (ap-southeast-1)`).
4. Bấm **"Create new project"** và đợi 1-2 phút để Supabase chuẩn bị cơ sở dữ liệu.

---

## 2. Khởi tạo Bảng & Dữ liệu Mẫu

1. Trên menu bên trái của Supabase Dashboard, chọn biểu tượng **SQL Editor** (icon `>_`).
2. Mở file [schema.sql](file:///d:/chamsocmeo/supabase/schema.sql) trong project này:
   - Copy toàn bộ nội dung và dán vào SQL Editor.
   - Bấm nút **RUN** (hoặc `Ctrl + Enter`).
   - Kiểm tra thông báo `Success. No rows returned`.
3. Mở tiếp file [seed.sql](file:///d:/chamsocmeo/supabase/seed.sql):
   - Copy nội dung và dán vào SQL Editor.
   - Bấm **RUN**.
   - Bây giờ dữ liệu mẫu cho Admin, mèo Luna, Milo, Oliver và telemetry phần cứng đã sẵn sàng.
4. Bạn có thể sang mục **Table Editor** để xem 9 bảng vừa được tạo.

---

## 3. Lấy JDBC Connection String

1. Trên Supabase Dashboard, bấm vào biểu tượng **Project Settings** (icon bánh răng ⚙️ ở góc dưới bên trái).
2. Chọn mục **Database**.
3. Cuộn xuống phần **Connection string**:
   - Chọn tab **URI** hoặc tab **JDBC**.
   - Chọn chế độ **Connection Pooling (Transaction mode - Port 6543)** (Khuyên dùng để tối ưu hiệu năng và kết nối được trên cả mạng IPv4):
     ```
     jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require
     ```
   - **User**: `postgres.[YOUR-PROJECT-REF]`
   - **Password**: Mật khẩu database bạn đã đặt ở Bước 1.

---

## 4. Cấu hình Backend kết nối Supabase

### Cách 1: Sử dụng file `.env`
Tạo hoặc sửa file `.env` tại thư mục gốc:
```ini
# Supabase PostgreSQL Configuration
SUPABASE_DB_URL=jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require
SUPABASE_DB_USER=postgres.your-project-ref
SUPABASE_DB_PASSWORD=your-database-password

# JWT & Port
AURAPET_JWT_SECRET=d774dfc564fce6b2d9d56de4985e8152f0e3bb944f53c1f87bb15bc2d4a11ff2
PORT=8080
SPRING_PROFILES_ACTIVE=supabase
```

### Cách 2: Chạy trực tiếp bằng lệnh JAR
```powershell
java -jar backend/target/aurapet-backend-1.0.0-SNAPSHOT.jar `
  --spring.profiles.active=supabase `
  --spring.datasource.url="jdbc:postgresql://aws-0-ap-southeast-1.pooler.supabase.com:6543/postgres?sslmode=require" `
  --spring.datasource.username="postgres.your-project-ref" `
  --spring.datasource.password="your-database-password"
```

### Cách 3: Chạy bằng Docker Compose kết nối Supabase
```powershell
docker compose -f docker-compose.supabase.yml up -d
```
