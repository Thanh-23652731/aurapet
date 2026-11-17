package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.DeviceSettingsDto;
import com.aurapet.backend.application.dto.DeviceStateDto;
import com.aurapet.backend.application.dto.UpdateDeviceSettingsRequest;
import com.aurapet.backend.domain.repository.PetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.HashSet;
import java.util.Set;
import java.util.UUID;

/**
 * Cửa ngõ duy nhất tới phần cứng cho controller, dashboard và Bác sĩ AI.
 * Không tự xử lý nghiệp vụ, chỉ chuyển gói tin MQTT tới đúng service và ghép trạng thái:
 *
 *   telemetry / log / status -> {@link FeederService}        máy cho ăn, lịch hẹn giờ
 *   water                    -> {@link WaterMonitorService}  bình nước
 *   litter                   -> {@link LitterService}        khay vệ sinh
 *   vision                   -> {@link VisionService}        camera nhận diện mèo
 *   cài đặt dùng chung       -> {@link DeviceSettingsService}
 */
@Slf4j
@Service
public class DeviceService {

    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final FeederService feeder;
    private final WaterMonitorService water;
    private final LitterService litter;
    private final VisionService vision;
    private final DeviceSettingsService settings;
    private final PetRepository petRepository;

    public DeviceService(FeederService feeder, WaterMonitorService water, LitterService litter,
                         VisionService vision, DeviceSettingsService settings, PetRepository petRepository) {
        this.feeder = feeder;
        this.water = water;
        this.litter = litter;
        this.vision = vision;
        this.settings = settings;
        this.petRepository = petRepository;
    }

    // =====================================================================
    // TRẠNG THÁI (web/app gọi 2 giây/lần)
    // =====================================================================

    public boolean isOnline() {
        return feeder.isOnline();
    }

    /**
     * Trạng thái cho một người dùng cụ thể. Máy là của chung nhưng thông tin gắn với
     * từng bé (lần ăn cuối, lần đi vệ sinh, bé camera nhận ra, bé của máy) chỉ hiện
     * cho chủ của bé đó và admin.
     */
    public DeviceStateDto getState(Actor actor) {
        DeviceStateDto s = getState();
        if (actor.admin()) return s;
        Set<UUID> own = new HashSet<>();
        petRepository.findByOwnerIdOrderByCreatedAtAsc(actor.id()).forEach(p -> own.add(p.getId()));

        UUID feedPet = feeder.lastFeed().petId();
        if (feedPet != null && !own.contains(feedPet)) {
            s.setLastFeedAt(null);
            s.setLastFeedGram(null);
            s.setLastFeedSource(null);
        }
        UUID visitPet = litter.lastVisit().petId();
        if (visitPet != null && !own.contains(visitPet)) {
            s.setLastLitterVisitAt(null);
            s.setLastLitterVisitSec(null);
            s.setLastLitterVisitPetName(null);
        }
        UUID seenPet = vision.getLastDetectedPetId();
        if (seenPet == null || !own.contains(seenPet)) {
            s.setLastDetectedPetName(null);
            s.setLastDetectedPetAt(null);
        }
        DeviceSettingsDto set = s.getSettings();
        if (set.getFeederPetId() != null && !own.contains(set.getFeederPetId())) {
            set.setFeederPetId(null);
            set.setFeederPetName(null);
            set.setFeederPetOfOtherUser(true);
        }
        return s;
    }

    /** Trạng thái đầy đủ (dùng nội bộ, ví dụ ngữ cảnh cho Bác sĩ AI — không chứa thông tin riêng của bé). */
    public DeviceStateDto getState() {
        boolean online = feeder.isOnline();
        JsonNode t = feeder.getTelemetry();
        FeederService.LastFeed lastFeed = feeder.lastFeed();

        DeviceStateDto.DeviceStateDtoBuilder b = DeviceStateDto.builder()
                .brokerConnected(feeder.isBrokerConnected())
                .online(online)
                .lastSeen(feeder.getLastSeen())
                .lastFeedAt(lastFeed.at())
                .lastFeedGram(lastFeed.gram())
                .lastFeedSource(lastFeed.source())
                .settings(settings.toDto());

        if (t != null) {
            b.bowlWeightGram(Math.round(t.path("weight").asDouble(0) * 10.0) / 10.0)
                    .foodLevelPercent(t.path("foodLevelPercent").asInt(0))
                    .lowFood(t.path("lowFood").asBoolean(false))
                    .catPresent(online && t.path("petPresent").asBoolean(false))
                    .feeding(online && t.path("isFeeding").asBoolean(false))
                    .feedCountSinceBoot(t.path("feedCount").asInt(0))
                    .ip(textOrNull(t, "ip"))
                    .wifiRssi(t.has("rssi") ? t.path("rssi").asInt() : null)
                    .message(textOrNull(t, "msg"));
        } else {
            b.feeding(false).catPresent(false);
        }

        WaterMonitorService.Reading w = water.reading();
        b.waterOnline(w.online())
                .waterLastSeen(w.lastSeen())
                .waterSensorOk(w.sensorOk())
                .waterPercent(w.percent())
                .waterCm(w.cm())
                .lowWater(w.low())
                .litterState(w.litterState());

        LitterService.LastVisit visit = litter.lastVisit();
        b.lastLitterVisitAt(visit.at())
                .lastLitterVisitSec(visit.durationSec())
                .lastLitterVisitPetName(visit.petName())
                .lastLitterCleanAt(litter.lastCleanAt());

        b.cameraLastSeen(vision.getCameraLastSeen())
                .lastDetectedPetName(vision.getLastDetectedPetName())
                .lastDetectedPetAt(vision.getLastDetectedPetAt());
        return b.build();
    }

    // =====================================================================
    // LỆNH ĐIỀU KHIỂN
    // =====================================================================

    public DeviceStateDto feed(Integer gram, String source, Actor actor) {
        feeder.feed(gram, source);
        return getState(actor);
    }

    public DeviceStateDto stop(Actor actor) {
        feeder.stop();
        return getState(actor);
    }

    public DeviceStateDto tare(Actor actor) {
        feeder.tare();
        return getState(actor);
    }

    public DeviceStateDto updateSettings(UpdateDeviceSettingsRequest request, Actor actor) {
        feeder.updateSettings(request, actor);
        return getState(actor);
    }

    /** Camera vừa gửi khung hình lên (xem CameraService). */
    public void markCameraSeen() {
        vision.markCameraSeen();
    }

    // =====================================================================
    // GÓI TIN TỪ THIẾT BỊ + VIỆC ĐỊNH KỲ
    // =====================================================================

    @EventListener
    public void onDeviceMessage(DeviceMessageEvent event) {
        try {
            switch (event.channel()) {
                case "telemetry" -> feeder.handleTelemetry(event.payload());
                case "log" -> feeder.handleFeedingLog(event.payload());
                case "status" -> feeder.handleStatus(event.payload());
                case "vision" -> vision.handleVision(event.payload());
                case "water" -> water.handleWater(event.payload());
                case "litter" -> litter.handleLitter(event.payload());
                default -> log.debug("[Device] Bỏ qua channel lạ: {}", event.channel());
            }
        } catch (Exception e) {
            log.warn("[Device] Lỗi xử lý gói tin {}: {}", event.channel(), e.getMessage());
        }
    }

    /** Mỗi 15 giây: báo thiết bị mất kết nối, đồng bộ chế độ cảm biến và chạy lịch hẹn giờ. */
    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void tick() {
        try {
            feeder.checkOffline();
            water.checkOffline();
            feeder.syncSensorFeed(); // camera bật/tắt -> ESP32 tắt/bật tự nhả theo cảm biến
            feeder.runSchedule(LocalDateTime.now(AppTime.ZONE));
        } catch (Exception e) {
            log.warn("[Device] Lỗi tick lịch hẹn: {}", e.getMessage());
        }
    }

    /** Dùng cho Bác sĩ AI: thời điểm hiện tại theo HH:mm. */
    public static String nowHhMm() {
        return LocalDateTime.now(AppTime.ZONE).format(HH_MM);
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }
}
