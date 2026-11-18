package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.AlertLevel;
import com.aurapet.backend.domain.model.AlertType;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.PetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Optional;
import java.util.Set;
import java.util.UUID;

/**
 * Camera Raspberry Pi (YOLOv8): sự kiện PREFIX/vision {catPresent, petName, confidence}.
 *
 * Khi mèo đến máy cho ăn:
 *   - Camera nhận ra bé có trong app (vd. "Mimi") -> nhả KHẨU PHẦN RIÊNG của bé đó
 *     (bé chưa đặt khẩu phần riêng thì dùng khẩu phần chung) và ghi nhật ký cho bé đó.
 *   - Camera nhận ra mèo lạ (lớp "Khac" của mô hình) -> không nhả hạt.
 *   - Camera chỉ biết "có mèo" (chưa có mô hình nhận diện, hoặc chưa chắc) -> khẩu phần chung.
 * Máy cho ăn tự quyết định có được nhả hay không (bật Tự động, khay gần hết, đã qua thời gian chờ).
 */
@Slf4j
@Service
public class VisionService {

    /** Tên lớp "mèo lạ" trong mô hình nhận diện (prepare_sample_dataset.py --with-other). */
    static final Set<String> STRANGER_NAMES = Set.of("khac", "khác", "stranger", "other");
    /** Tên camera gửi khi chỉ biết là có mèo, chưa nhận ra bé nào. */
    static final Set<String> GENERIC_NAMES = Set.of("", "meo", "mèo", "unknown", "cat");
    private static final Duration STRANGER_ALERT_GAP = Duration.ofMinutes(10);

    private final FeederService feeder;
    private final CatIdentityTracker catIdentity;
    private final PetRepository petRepository;
    private final DeviceAlerts alerts;
    private final ObjectMapper mapper;

    private volatile Instant cameraLastSeen;
    private volatile String lastDetectedPetName;
    /** Bé trong CSDL khớp với tên camera nhận ra; null nếu không khớp bé nào. */
    private volatile UUID lastDetectedPetId;
    private volatile Instant lastDetectedPetAt;
    private Instant lastStrangerAlertAt = Instant.EPOCH;

    public VisionService(FeederService feeder, CatIdentityTracker catIdentity,
                         PetRepository petRepository, DeviceAlerts alerts, ObjectMapper mapper) {
        this.feeder = feeder;
        this.catIdentity = catIdentity;
        this.petRepository = petRepository;
        this.alerts = alerts;
        this.mapper = mapper;
    }

    /**
     * Camera vừa gửi khung hình lên (xem CameraService) -> web biết camera đang hoạt động.
     * Chỉ có hình (vd. Pi chạy AI_ENABLED=0) thì KHÔNG tính là AI đang nhận diện,
     * nên ESP32 vẫn tự cho ăn bằng cảm biến.
     */
    public void markCameraSeen() {
        cameraLastSeen = Instant.now();
    }

    public Instant getCameraLastSeen() {
        return cameraLastSeen;
    }

    public String getLastDetectedPetName() {
        return lastDetectedPetName;
    }

    public UUID getLastDetectedPetId() {
        return lastDetectedPetId;
    }

    public Instant getLastDetectedPetAt() {
        return lastDetectedPetAt;
    }

    synchronized void handleVision(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        markCameraSeen();
        feeder.cameraSeen(); // AI nhận diện đang chạy -> backend lo cho ăn theo từng bé thay cho cảm biến
        boolean catPresent = n.path("catPresent").asBoolean(false);
        String petName = n.path("petName").asText("").trim();
        String key = petName.toLowerCase(Locale.ROOT);
        boolean stranger = STRANGER_NAMES.contains(key);

        Optional<Pet> pet = Optional.empty();
        if (!stranger && !GENERIC_NAMES.contains(key)) {
            lastDetectedPetName = petName;
            lastDetectedPetAt = Instant.now();
            pet = petRepository.findFirstByNameIgnoreCase(petName);
            lastDetectedPetId = pet.map(Pet::getId).orElse(null);
            pet.ifPresent(catIdentity::remember); // nhật ký lần nhả kế tiếp ghi cho bé này
        }

        if (!catPresent) return;

        if (stranger) {
            if (Duration.between(lastStrangerAlertAt, Instant.now()).compareTo(STRANGER_ALERT_GAP) > 0) {
                lastStrangerAlertAt = Instant.now();
                alerts.add(AlertType.FEEDING, AlertLevel.INFO, "Camera thấy mèo lạ ở máy cho ăn — không nhả hạt");
            }
            return;
        }

        // Pi chạy DIRECT_CONTROL=1 đã tự gửi lệnh CAT cho ESP32 (ESP32 tự quyết có nhả không):
        // backend chỉ ghi nhận bé nào đến, không gửi thêm FEED để khỏi nhả 2 lần
        // Firmware ESP32 cũ (telemetry không có "cameraActive") không hiểu lệnh CAT -> backend vẫn gửi FEED thay.
        JsonNode t = feeder.getTelemetry();
        boolean espHandlesCat = t != null && t.has("cameraActive");
        if (n.path("direct").asBoolean(false) && espHandlesCat) return;

        if (pet.isPresent()) {
            Pet p = pet.get();
            boolean sent = feeder.requestVisionFeed("Camera: " + p.getName(), p.getPortionGram());
            if (sent) log.info("[Vision] Nhận ra {} -> nhả {}", p.getName(),
                    p.getPortionGram() != null ? p.getPortionGram() + "g (khẩu phần riêng)" : "khẩu phần chung");
        } else {
            // Chưa nhận ra bé nào (hoặc tên không có trong app): khẩu phần chung như trước
            feeder.requestVisionFeed("Camera AI", null);
        }
    }
}
