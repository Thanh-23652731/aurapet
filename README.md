# AuraPet — Production-Ready AIoT Smart Pet Care Ecosystem

[![Spring Boot 3.2.x](https://img.shields.io/badge/Spring%20Boot-3.2.3-brightgreen.svg)](https://spring.io/projects/spring-boot)
[![Java 17](https://img.shields.io/badge/Java-17%20LTS-orange.svg)](https://openjdk.org/)
[![Flutter 3.47](https://img.shields.io/badge/Flutter-3.47.2-blue.svg)](https://flutter.dev/)
[![OpenAPI 3.0](https://img.shields.io/badge/OpenAPI-3.0.3-green.svg)](https://swagger.io/)
[![Architecture](https://img.shields.io/badge/Architecture-Clean%20DDD-purple.svg)](#)

AuraPet is an intelligent, fullstack cat care platform combining IoT telemetry, Edge AI diagnostics, and a mobile application designed with an Apple-inspired glassmorphic interface.

---

## 🐾 Core Capabilities

1. **Smart Pet Management**:
   - Comprehensive pet identification, multi-criteria filtering (breed, gender, owner), biometric records, and photo management.
   - Seeded companion profiles ("Luna", "Milo", "Oliver") ready out-of-the-box.
2. **AI-Driven Health Analytics**:
   - Edge AI scoring algorithm analyzing dietary intake patterns, stool quality, and weight trend stability.
   - Interactive FL Chart visualizations for weight trajectories and daily food intake.
3. **Automated Feeding & Litter Box Control**:
   - Remote servo-driven kibble portion dispensing (10g - 100g).
   - Real-time monitoring of food hopper capacity, circulating water fountain pump, and auto litter box waste drawer.
4. **IoT & Edge Nodes Telemetry**:
   - Hardware status tracking for ESP32 microcontrollers and Raspberry Pi Vision nodes.
   - Live event alerts with severity levels (`INFO`, `WARNING`, `CRITICAL`).

---

## 📂 Repository Structure

```
d:\chamsocmeo/
├── .github/workflows/             # GitHub Actions CI/CD (Backend CI, Pages, Docker GHCR, APK)
├── supabase/                      # Supabase PostgreSQL Cloud (schema.sql, seed.sql, docs)
├── contracts/
│   └── openapi.yaml               # Frozen OpenAPI 3.0 contract specification
├── backend/                       # Spring Boot 3.x Clean Architecture DDD
│   ├── src/main/java/com/aurapet/backend/
│   │   ├── domain/                # Entities, Value Objects, Repositories
│   │   ├── application/           # DTOs, Mappers, Use Cases, ApiResponse<T>
│   │   ├── infrastructure/        # JPA, PostgreSQL/MariaDB, JWT Security, Seeder
│   │   └── interfaces/web/        # RestControllers, GlobalExceptionHandler, Swagger
│   └── src/test/java/             # 16 Unit & MockMvc Integration Tests
├── mobile/                        # Flutter + Material 3 Client
│   ├── lib/
│   │   ├── core/                  # Theme (Light/Dark), GlassCard, AnimatedSwitch, DioClient
│   │   ├── data/                  # Strongly typed OpenAPI models & Repositories
│   │   └── presentation/          # Riverpod 3 State, Login, Dashboard, Pets, Detail, Devices
│   └── test/                      # 11 Flutter Widget & Model Tests
├── render.yaml                    # 1-Click Cloud Deployment Blueprint (Render.com)
├── setup_git_and_push.bat         # Script tự động đẩy code lên GitHub an toàn
└── docs/                          # Architectural and Deployment Documentation
    ├── DEPLOY_GITHUB_AND_SUPABASE.md  # Hướng dẫn chi tiết deploy GitHub & Supabase
    ├── ARCHITECTURE.md
    ├── API_SPECIFICATION.md
    └── DEPLOYMENT.md
```

---

## ☁️ Cloud Deployment (Supabase & GitHub)

Hệ thống hỗ trợ 100% triển khai điện toán đám mây:
1. **Cơ sở dữ liệu Supabase (PostgreSQL Cloud)**:
   - DDL Schema đầy đủ 9 bảng: [`supabase/schema.sql`](file:///d:/chamsocmeo/supabase/schema.sql)
   - Dữ liệu mẫu chuẩn: [`supabase/seed.sql`](file:///d:/chamsocmeo/supabase/seed.sql)
   - Profile chuyên dụng: `--spring.profiles.active=supabase`
2. **GitHub Actions & Pages**:
   - Tự động test & build JAR trên đám mây.
   - Xuất bản Web Dashboard trực tiếp lên **GitHub Pages**.
   - Tự động đóng gói Docker Image lên **GitHub Container Registry (GHCR)**.
3. 👉 **Xem hướng dẫn chi tiết từng bước tại:** [`docs/DEPLOY_GITHUB_AND_SUPABASE.md`](file:///d:/chamsocmeo/docs/DEPLOY_GITHUB_AND_SUPABASE.md)

---

## 🚀 Quick Start

### 1. Launch Spring Boot Backend
```powershell
java -jar d:\chamsocmeo\backend\target\aurapet-backend-1.0.0-SNAPSHOT.jar
```
- Interactive Swagger UI: `http://localhost:8080/swagger-ui.html`
- **Database (MariaDB & HeidiSQL)**:
  - Host: `127.0.0.1` | Port: `3306` | User: `root` | Password: `1234`
  - Database: `aurapetdb`
  - Mở HeidiSQL và chọn session sẵn có **`AuraPet DB`** để quản lý trực tiếp dữ liệu.

### 2. Launch Flutter Mobile App
```powershell
cd d:\chamsocmeo\mobile
flutter run -d chrome
```

### 3. Demo Credentials
- **Admin**: `admin@aurapet.com` / `Admin@123`
- **User**: `user@aurapet.com` / `User@123`
*(Quick demo login buttons are also available directly on the mobile login screen for instant one-tap access).*

---

## 🧪 Automated Testing

- **Backend Test Suite (JUnit 5, Mockito, Spring Boot Test)**:
  ```powershell
  & 'C:\Users\Admin\maven\apache-maven-3.9.9\bin\mvn.cmd' test -f d:\chamsocmeo\backend\pom.xml
  ```
  *(16 tests passing, 0 failures)*

- **Frontend Test Suite (Flutter Widget & Model Tests)**:
  ```powershell
  cd d:\chamsocmeo\mobile; flutter test
  ```
  *(11 tests passing, 0 failures)*
