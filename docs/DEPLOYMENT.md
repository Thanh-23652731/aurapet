# AuraPet Deployment & Local Execution Guide

This document provides complete instructions for executing, testing, and deploying both the backend service and the Flutter mobile application.

---

## 1. Prerequisites

- **Java Development Kit**: OpenJDK 17 LTS
- **Build Tool**: Apache Maven 3.9+ (or use wrapper/direct maven binary)
- **Flutter SDK**: Flutter 3.47+ / Dart 3.13+
- **Host OS**: Windows, macOS, or Linux

---

## 2. Running the Spring Boot Backend

### Option A: Running from Jar
```powershell
java -jar d:\chamsocmeo\backend\target\aurapet-backend-1.0.0-SNAPSHOT.jar
```

### Option B: Running with Maven
```powershell
& 'C:\Users\Admin\maven\apache-maven-3.9.9\bin\mvn.cmd' spring-boot:run -f d:\chamsocmeo\backend\pom.xml
```

Once running:
- **API Base URL**: `http://localhost:8080`
- **Swagger Documentation**: `http://localhost:8080/swagger-ui.html`
- **OpenAPI Schema**: `http://localhost:8080/api-docs`
- **H2 Web Console**: `http://localhost:8080/h2-console` (`JDBC URL: jdbc:h2:mem:aurapetdb`, User: `sa`, Password: empty)

---

## 3. Running the Flutter Mobile Application

### Option A: Run in Chrome Browser
```powershell
cd d:\chamsocmeo\mobile
flutter run -d chrome
```

### Option B: Run in Windows Desktop
```powershell
cd d:\chamsocmeo\mobile
flutter run -d windows
```

### Option C: Run on Connected Mobile Device / Emulator
```powershell
cd d:\chamsocmeo\mobile
flutter run
```

---

## 4. Running the Automated QA Test Suites

### Backend Unit & Integration Tests (JUnit 5 + Mockito + MockMvc)
```powershell
& 'C:\Users\Admin\maven\apache-maven-3.9.9\bin\mvn.cmd' test -f d:\chamsocmeo\backend\pom.xml
```
*Executes all 16 tests covering repositories, service business logic, actuator commands, and web layer endpoints.*

### Frontend Tests (Widget & Model Serialization)
```powershell
cd d:\chamsocmeo\mobile
flutter test
```
*Executes all 11 tests verifying OpenAPI model deserialization, widget rendering, and animated controls.*

---

## 5. Production Profile Configuration

To run the backend connected to external PostgreSQL:
```powershell
java -jar aurapet-backend-1.0.0-SNAPSHOT.jar --spring.profiles.active=prod --spring.datasource.url=jdbc:postgresql://localhost:5432/aurapet --spring.datasource.username=postgres --spring.datasource.password=secret
```
