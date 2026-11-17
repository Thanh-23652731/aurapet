package com.aurapet.backend.application.service;

import com.aurapet.backend.domain.model.AlertLevel;
import com.aurapet.backend.domain.model.AlertType;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.util.Set;

/**
 * Bình nước bơm tuần hoàn: ESP32 máy dọn cát gửi PREFIX/water 5 giây/lần.
 * Chỉ theo dõi và báo khi sắp cạn — bơm chạy liên tục, không điều khiển từ xa.
 * Gói này cũng mang trạng thái khay cát (litterState) vì cùng một ESP32.
 */
@Service
public class WaterMonitorService {

    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);
    /** Sóng nước làm số đo nhảy quanh ngưỡng: không nhắc "sắp hết nước" lại trong 30 phút. */
    private static final Duration LOW_WATER_ALERT_GAP = Duration.ofMinutes(30);
    private static final Set<String> LITTER_STATES = Set.of("idle", "cat_inside", "waiting", "cleaning");

    private final DeviceAlerts alerts;
    private final ObjectMapper mapper;

    private volatile JsonNode water;
    private volatile Instant lastSeen;
    private volatile boolean announced;
    private volatile boolean lastLowWater;
    private Instant lastLowWaterAlertAt = Instant.EPOCH;

    /** Số đo hiện tại. percent/cm = null khi cảm biến lỗi; litterState = null khi máy mất kết nối. */
    public record Reading(boolean online, Instant lastSeen, boolean sensorOk, Integer percent, Double cm,
                          boolean low, String litterState) {
    }

    public WaterMonitorService(DeviceAlerts alerts, ObjectMapper mapper) {
        this.alerts = alerts;
        this.mapper = mapper;
    }

    public boolean isOnline() {
        Instant seen = lastSeen;
        return seen != null && Duration.between(seen, Instant.now()).compareTo(ONLINE_TIMEOUT) < 0;
    }

    /** Sự kiện khay cát đến từ cùng ESP32 -> máy vẫn đang online. */
    void markSeen() {
        lastSeen = Instant.now();
    }

    public Reading reading() {
        JsonNode w = water;
        boolean online = isOnline();
        if (w == null) return new Reading(online, lastSeen, false, null, null, false, null);
        boolean sensorOk = w.path("sensorOk").asBoolean(false) && w.path("waterPercent").asInt(-1) >= 0;
        String ls = w.path("litterState").asText("");
        return new Reading(online, lastSeen, sensorOk,
                sensorOk ? w.path("waterPercent").asInt() : null,
                sensorOk ? Math.round(w.path("waterCm").asDouble() * 10.0) / 10.0 : null,
                w.path("lowWater").asBoolean(false),
                online && LITTER_STATES.contains(ls) ? ls : null);
    }

    synchronized void handleWater(String payload) throws Exception {
        JsonNode w = mapper.readTree(payload);
        water = w;
        lastSeen = Instant.now();

        if (!announced) {
            announced = true;
            alerts.add(AlertType.SYSTEM, AlertLevel.INFO, "Cảm biến mực nước đã kết nối (IP " + w.path("ip").asText("?") + ")");
        }

        boolean low = w.path("lowWater").asBoolean(false);
        int pct = w.path("waterPercent").asInt(-1);
        if (low && !lastLowWater
                && Duration.between(lastLowWaterAlertAt, Instant.now()).compareTo(LOW_WATER_ALERT_GAP) > 0) {
            lastLowWaterAlertAt = Instant.now();
            alerts.add(AlertType.HEALTH, AlertLevel.WARNING,
                    "Bình nước sắp cạn" + (pct >= 0 ? " (còn khoảng " + pct + "%)" : "")
                            + ". Hãy đổ thêm nước để bơm không chạy khô và bé luôn có nước uống.");
        } else if (!low && lastLowWater) {
            alerts.add(AlertType.HEALTH, AlertLevel.INFO, "Đã đổ thêm nước" + (pct >= 0 ? " (" + pct + "%)" : ""));
        }
        lastLowWater = low;
    }

    synchronized void checkOffline() {
        if (announced && !isOnline()) {
            announced = false;
            alerts.add(AlertType.SYSTEM, AlertLevel.WARNING,
                    "Cảm biến mực nước (ESP32 máy dọn cát) mất kết nối — kiểm tra nguồn điện và Wi-Fi");
        }
    }
}
