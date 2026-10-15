# AuraPet AIoT REST API Specification

The AuraPet REST API implements a contract-first architecture frozen in `contracts/openapi.yaml`. When the backend runs, interactive documentation and execution are available at:
**Swagger UI**: `http://localhost:8080/swagger-ui.html`
**OpenAPI JSON**: `http://localhost:8080/api-docs`

---

## 1. Unified Response Envelope (`ApiResponse<T>`)

Every API endpoint responds with a standardized JSON wrapper:

```json
{
  "success": true,
  "message": "Operation completed successfully",
  "timestamp": "2026-09-14T18:05:35Z",
  "data": { ... },
  "errors": []
}
```

In error scenarios (e.g. HTTP 400, 401, 404, 500):
```json
{
  "success": false,
  "message": "Validation failed",
  "timestamp": "2026-09-14T18:05:35Z",
  "data": null,
  "errors": [
    "name: Pet name is required",
    "weight: Weight must be at least 0.1 kg"
  ]
}
```

---

## 2. Seed Accounts for Instant Testing

| Role | Email | Password | Permissions |
|---|---|---|---|
| **ADMIN** | `admin@aurapet.com` | `Admin@123` | Full CRUD pets, system device controls, stats |
| **USER** | `user@aurapet.com` | `User@123` | View pets, pet details, trigger feeder, stats |

---

## 3. Endpoints Matrix

### Authentication
- `POST /api/v1/auth/login`: Authenticate with email & password, returns JWT token.
- `POST /api/v1/auth/register`: Create new user account.
- `GET /api/v1/auth/me`: Retrieve profile of currently authenticated user.

### Pet Management
- `GET /api/v1/pets`: Paginated search & filtering (`?page=0&size=10&search=Luna&breed=British Shorthair&gender=FEMALE`).
- `GET /api/v1/pets/{id}`: Detailed pet profile by UUID.
- `POST /api/v1/pets`: Create new pet with validation.
- `PUT /api/v1/pets/{id}`: Update existing pet.
- `DELETE /api/v1/pets/{id}`: Delete pet.
- `POST /api/v1/pets/{id}/image`: Update pet avatar photo URL.

### Dashboard & Telemetry
- `GET /api/v1/dashboard/stats`: Aggregated summary:
  - Total registered pets
  - Today's consumed kibble in grams
  - Number of feeding sessions
  - Litter box visits
  - Latest system alerts
  - Hardware connection status

### Health Analytics & Edge AI
- `GET /api/v1/health/{petId}/summary`: Edge AI scoring algorithm assessing physiological stability (0-100 score, health state, daily average intake, personalized veterinary recommendation).
- `GET /api/v1/health/{petId}/weight`: Historical weight tracking points for FL Chart rendering.
- `GET /api/v1/health/{petId}/food`: Daily food consumption records.
- `GET /api/v1/health/{petId}/litter`: Stool and litter health logs (`NORMAL`, `ABNORMAL`, `DIARRHEA`, `CONSTIPATION`).

### IoT Device Actuators
- `POST /api/v1/system/feeder`: Request food portion dispense (5g - 150g). Logs event to FeedingLog and triggers alert notification.
- `POST /api/v1/system/water`: Toggle circulating water pump on/off.
- `POST /api/v1/system/litter`: Command auto-cleaning cycle (`CLEAN`, `RESET`, `PAUSE`). Empties waste drawer and updates status.
- `GET /api/v1/system/status`: Real-time telemetry:
  - Feeder readiness
  - Water pump status
  - Litter cleaner cycle
  - ESP32 status (`ONLINE`)
  - Raspberry Pi status (`ONLINE`)
  - Food hopper capacity (`%`)
  - Water reservoir level (`%`)
  - Waste drawer capacity (`%`)
