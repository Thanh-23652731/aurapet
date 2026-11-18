package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import com.aurapet.backend.interfaces.web.exception.DeviceUnavailableException;

import java.time.Instant;
import java.util.Map;
import java.util.UUID;

/**
 * Khay vệ sinh: sự kiện PREFIX/litter từ ESP32 máy dọn cát.
 *   visit          mèo đi vệ sinh xong 1 lượt (durationSec = thời gian ở trong khay) -> ghi litter_logs
 *   cleaned        máy cào cát xong
 *   clean_stopped  dừng cào giữa chừng vì mèo vào khay
 *   clean_cancelled dừng cào theo lệnh STOP từ web/app
 * agoSec: số giây từ lúc xảy ra tới lúc gửi (ESP32 gửi bù sau khi mất mạng).
 */
@Slf4j
@Service
public class LitterService {

    /** ESP32 gửi bù sự kiện lúc mất mạng: bỏ qua sự kiện cũ hơn mức này (đồng hồ ESP32 có thể đã khởi động lại). */
    private static final long MAX_EVENT_AGE_SEC = 7 * 24 * 3600;

    private final DeviceSettingsService settings;
    private final LitterLogRepository litterLogRepository;
    private final PetRepository petRepository;
    private final WaterMonitorService waterMonitor;
    private final DeviceAlerts alerts;
    private final DeviceCommandPublisher publisher;
    private final ObjectMapper mapper;

    private Instant lastVisitAt;
    private Integer lastVisitSec;
    private String lastVisitPetName;
    private UUID lastVisitPetId;
    private boolean lastVisitLoaded;

    /** Lượt đi vệ sinh gần nhất (từ RAM, hoặc từ CSDL sau khi khởi động lại). */
    public record LastVisit(Instant at, Integer durationSec, String petName, UUID petId) {
    }

    public LitterService(DeviceSettingsService settings,
                         LitterLogRepository litterLogRepository,
                         PetRepository petRepository,
                         WaterMonitorService waterMonitor,
                         DeviceAlerts alerts,
                         DeviceCommandPublisher publisher,
                         ObjectMapper mapper) {
        this.settings = settings;
        this.litterLogRepository = litterLogRepository;
        this.petRepository = petRepository;
        this.waterMonitor = waterMonitor;
        this.alerts = alerts;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    // =====================================================================
    // DỌN CÁT THỦ CÔNG TỪ WEB/APP (lệnh qua PREFIX/litter/command)
    // =====================================================================

    /** Cào cát ngay. Máy từ chối nếu mèo đang trong khay hoặc đang cào. */
    public void clean() {
        String state = requireOnline();
        if ("cat_inside".equals(state)) {
            throw new BadRequestException("Mèo đang ở trong khay — đợi bé ra rồi dọn cát");
        }
        if ("cleaning".equals(state)) {
            throw new BadRequestException("Máy đang dọn cát");
        }
        send(Map.of("action", "CLEAN"));
    }

    /** Dừng cào cát ngay (cào dừng tại chỗ, lần dọn sau sẽ tự về vị trí đầu). */
    public void stopClean() {
        requireOnline();
        send(Map.of("action", "STOP"));
    }

    private String requireOnline() {
        if (!publisher.isConnected()) {
            throw new DeviceUnavailableException("Máy chủ chưa kết nối được MQTT broker. Kiểm tra cấu hình MQTT_* trong file .env");
        }
        WaterMonitorService.Reading r = waterMonitor.reading();
        if (!r.litterOnline()) {
            throw new DeviceUnavailableException("Máy dọn cát đang offline — kiểm tra nguồn điện và Wi-Fi của ESP32");
        }
        return r.litterState();
    }

    private void send(Map<String, Object> command) {
        if (!publisher.publish(DeviceCommandPublisher.LITTER_CHANNEL, command)) {
            throw new DeviceUnavailableException("Không gửi được lệnh tới máy dọn cát, thử lại sau");
        }
    }

    public synchronized LastVisit lastVisit() {
        loadLastVisitIfNeeded();
        return new LastVisit(lastVisitAt, lastVisitSec, lastVisitPetName, lastVisitPetId);
    }

    public Instant lastCleanAt() {
        return settings.get().getLastLitterCleanAt();
    }

    synchronized void handleLitter(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        String event = n.path("event").asText("");
        long ago = Math.max(0, n.path("agoSec").asLong(0));
        if (ago > MAX_EVENT_AGE_SEC) {
            log.info("[Litter] Bỏ qua sự kiện khay quá cũ ({} giây): {}", ago, event);
            return;
        }
        Instant at = Instant.now().minusSeconds(ago);
        waterMonitor.markSeen(); // cùng ESP32 với cảm biến nước -> máy đang online

        switch (event) {
            case "visit" -> recordVisit(at, Math.max(0, n.path("durationSec").asInt(0)));
            case "cleaned" -> {
                settings.update(s -> {
                    if (s.getLastLitterCleanAt() == null || at.isAfter(s.getLastLitterCleanAt())) {
                        s.setLastLitterCleanAt(at);
                    }
                });
                alerts.add(AlertType.LITTER, AlertLevel.INFO, "Máy đã dọn cát xong");
            }
            case "clean_stopped" -> alerts.add(AlertType.LITTER, AlertLevel.INFO,
                    "Máy dừng cào cát giữa chừng vì mèo vào khay, sẽ dọn lại sau khi bé ra");
            case "clean_cancelled" -> alerts.add(AlertType.LITTER, AlertLevel.INFO,
                    "Đã dừng dọn cát theo lệnh từ web/app");
            default -> log.debug("[Litter] Bỏ qua sự kiện khay lạ: {}", event);
        }
    }

    private void recordVisit(Instant at, int sec) {
        Pet pet = settings.resolveFeederPet();
        loadLastVisitIfNeeded();
        if (lastVisitAt == null || !at.isBefore(lastVisitAt)) {
            lastVisitAt = at;
            lastVisitSec = sec;
            lastVisitPetName = pet != null ? pet.getName() : null;
            lastVisitPetId = pet != null ? pet.getId() : null;
        }
        if (pet == null) {
            alerts.add(AlertType.LITTER, AlertLevel.INFO,
                    "Mèo vừa đi vệ sinh (" + formatDuration(sec) + ") — chưa có thú cưng để ghi nhật ký");
            return;
        }
        litterLogRepository.save(LitterLog.builder()
                .petId(pet.getId())
                .result(LitterResult.NORMAL)
                .durationSec(sec)
                .timestamp(at)
                .build());
        alerts.add(AlertType.LITTER, AlertLevel.INFO,
                pet.getName() + " vừa đi vệ sinh (" + formatDuration(sec) + " trong khay)", pet.getId());
    }

    private void loadLastVisitIfNeeded() {
        if (lastVisitLoaded) return;
        lastVisitLoaded = true;
        litterLogRepository.findFirstByOrderByTimestampDesc().ifPresent(l -> {
            lastVisitAt = l.getTimestamp();
            lastVisitSec = l.getDurationSec();
            lastVisitPetId = l.getPetId();
            lastVisitPetName = petRepository.findById(l.getPetId()).map(Pet::getName).orElse(null);
        });
    }

    static String formatDuration(int sec) {
        if (sec < 60) return sec + " giây";
        int m = sec / 60, s = sec % 60;
        return s == 0 ? m + " phút" : m + " phút " + s + " giây";
    }
}
