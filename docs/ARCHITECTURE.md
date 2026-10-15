# AuraPet AIoT Smart Pet Care System — Architectural Overview

## 1. System Philosophy & Purpose
**AuraPet** is a production-grade AIoT (Artificial Intelligence of Things) ecosystem designed for automated, intelligent feline healthcare and nutrition management. The platform unites:
- **Edge Microcontrollers & Nodes**: ESP32 for high-precision PWM/servo dosing and pump cycling; Raspberry Pi 5 for computer-vision litter analysis.
- **Backend Core**: Spring Boot 3.x with Clean Architecture and Domain-Driven Design (DDD).
- **Mobile Client**: Flutter Material 3 application featuring Apple-inspired glassmorphism aesthetics, interactive FL Charts, and robust Riverpod state management.
- **Contract-First Communication**: Standardized against a frozen OpenAPI 3.0 contract.

---

## 2. Architecture Diagram

```
┌─────────────────────────────────────────────────────────────────┐
│                      AuraPet Flutter App                        │
│          (Riverpod 3, Dio Interceptors, Material 3 Glass)        │
└───────────────┬─────────────────────────────────┬───────────────┘
                │ REST (JWT Bearer)               │ Telemetry
                ▼                                 ▼
┌─────────────────────────────────────────────────────────────────┐
│              Spring Boot 3.x Clean Architecture Core            │
│  ┌───────────────────────────────────────────────────────────┐  │
│  │ Interfaces Layer: RestControllers, GlobalExceptionHandler  │  │
│  └─────────────────────────────┬─────────────────────────────┘  │
│                                │                                │
│  ┌─────────────────────────────▼─────────────────────────────┐  │
│  │ Application Layer: Services (Auth, Pet, Dashboard, etc.)  │  │
│  └─────────────────────────────┬─────────────────────────────┘  │
│                                │                                │
│  ┌─────────────────────────────▼─────────────────────────────┐  │
│  │ Domain Layer: User, Pet, FeedingLog, LitterLog, Alerts    │  │
│  └─────────────────────────────┬─────────────────────────────┘  │
│                                │                                │
│  ┌─────────────────────────────▼─────────────────────────────┐  │
│  │ Infrastructure Layer: JPA Repositories, Security, Firebase │  │
│  └───────────────────────────────────────────────────────────┘  │
└───────────────┬─────────────────────────────────┬───────────────┘
                │                                 │
                ▼                                 ▼
┌───────────────────────────────┐ ┌───────────────────────────────┐
│  PostgreSQL / Embedded H2 DB  │ │     Hardware & Edge Nodes     │
│   (Relational Data Store)     │ │   - ESP32 Actuator Subsystem  │
│                               │ │   - Raspberry Pi Vision Node  │
└───────────────────────────────┘ └───────────────────────────────┘
```

---

## 3. Backend DDD Layering Details (`/backend`)

1. **Domain Layer (`domain/`)**:
   - `model`: Core domain entities (`User`, `Pet`, `FeedingLog`, `LitterLog`, `SystemStatus`, `Alert`, `WeightRecord`) and enums (`Role`, `Gender`, `LitterResult`, `AlertType`, `AlertLevel`).
   - `repository`: Domain repository interfaces (`PetRepository`, `UserRepository`, etc.) isolating persistence details from business semantics.
2. **Application Layer (`application/`)**:
   - `dto`: Request and response contracts adhering strictly to the OpenAPI 3.0 schema.
   - `service`: Use cases including `AuthService`, `PetService` (CRUD, filtering, search), `DashboardService` (metric aggregations), `HealthAnalyticsService` (Edge AI scoring), and `SystemDeviceService` (actuator commands).
3. **Infrastructure Layer (`infrastructure/`)**:
   - `security`: `JwtTokenProvider`, `JwtAuthenticationFilter`, `SecurityConfig` stateless security filter chain.
   - `persistence`: Spring Data JPA implementations and `DataInitializer` pre-seeding realistic companion data.
   - `firebase`: `FirebaseAdminService` notification and cloud bridge stub.
4. **Interfaces Layer (`interfaces/web/`)**:
   - `controller`: `AuthController`, `PetController`, `DashboardController`, `HealthAnalyticsController`, `SystemDeviceController`.
   - `exception`: `GlobalExceptionHandler` intercepting all domain and validation exceptions to return unified `ApiResponse<T>`.
   - `config`: `OpenApiConfig` configuring SpringDoc OpenAPI 3.0 and Swagger UI.

---

## 4. Mobile Architecture & Design Language (`/mobile`)

1. **Aesthetic Philosophy**:
   - Apple-inspired minimalism using subtle glassmorphism (`GlassCard` with `BackdropFilter` blur).
   - Dynamic Light and Dark themes driven by `ThemeModeNotifier`.
   - Palette: Pure royal blue (`#0066FF`), glowing cyan (`#00B4D8`), and crisp white/dark slate.
   - Micro-interactions: `AnimatedToggleSwitch` with physics springs for actuator commands.
   - Seamless Hero animations bridging Pet cards to Pet detail profile.
2. **State & Network Architecture**:
   - Riverpod 3.x `Notifier` and `AsyncNotifier` patterns for declarative, reactive state propagation.
   - Dio client with authentication interceptor and automatic token injection.
   - Strongly typed models parsing UUIDs and ISO-8601 timestamps without discrepancies.
3. **Data Visualization**:
   - **FL Chart**: Smooth bezier curves for weight tracking progression over time.
   - Dynamic bar rods for daily food intake against nutritional targets.
