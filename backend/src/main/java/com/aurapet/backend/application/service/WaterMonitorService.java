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
 * Bình nước. Hai nguồn số đo:
 *   - ESP32 máy dọn cát gửi PREFIX/water 5 giây/lần (cảm biến siêu âm, kèm trạng thái khay cát litterState).
 *   - ESP32 máy cho ăn có cảm biến mực nước analog gửi waterPercent/lowWater trong PREFIX/telemetry.
 * Có máy dọn cát thì ưu tiên số đo của nó; không có thì lấy từ máy cho ăn.
 * Chỉ theo dõi và báo khi sắp cạn — bơm chạy liên tục, không điều khiển từ xa.
 */
@Service
public class WaterMonitorService {

    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(30);
    /** Sóng nước làm số đo nhảy quanh ngưỡng: không nhắc "sắp hết nước" lại trong 30 phút. */
    private static final Duration LOW_WATER_ALERT_GAP = Duration.ofMinutes(30);
    private static final Set<String> LITTER_STATES = Set.of("idle", "cat_inside", "waiting", "cleaning");

    public static final String SOURCE_LITTER = "litter";
    public static final String SOURCE_FEEDER = "feeder";

    private final DeviceAlerts alerts;
    private final ObjectMapper mapper;

    /** Gói PREFIX/water của ESP32 máy dọn cát. */
    private volatile JsonNode water;
    private volatile Instant lastSeen;
    /** Mực nước trong telemetry của ESP32 máy cho ăn. */
    private volatile JsonNode feederWater;
    private volatile Instant feederSeen;

    private volatile boolean announced;
    private volatile String announcedSource;
    private volatile boolean lastLowWater;
    /** Đã gửi cảnh báo "sắp cạn" và chưa báo "đã đổ thêm". */
    private volatile boolean lowAlerted;
    private Instant lastLowWaterAlertAt = Instant.EPOCH;

    /**
     * Số đo hiện tại. percent/cm = null khi cảm biến lỗi hoặc không có; litterState = null khi máy dọn cát
     * mất kết nối. source = litter | feeder | null (chưa có). litterOnline/litterLastSeen chỉ của máy dọn cát.
     */
    public record Reading(boolean online, Instant lastSeen, boolean sensorOk, Integer percent, Double cm,
                          boolean low, String litterState, String source,
                          boolean litterOnline, Instant litterLastSeen) {
    }

    public WaterMonitorService(DeviceAlerts alerts, ObjectMapper mapper) {
        this.alerts = alerts;
        this.mapper = mapper;
    }

    private static boolean fresh(Instant seen) {
        return seen != null && Duration.between(seen, Instant.now()).compareTo(ONLINE_TIMEOUT) < 0;
    }

    /** Máy dọn cát (nguồn PREFIX/water) còn online. */
    public boolean isOnline() {
        return fresh(lastSeen);
    }

    private boolean feederWaterOnline() {
        return feederWater != null && fresh(feederSeen);
    }

    /** Sự kiện khay cát đến từ cùng ESP32 -> máy vẫn đang online. */
    void markSeen() {
        lastSeen = Instant.now();
    }

    public Reading reading() {
        boolean litterOnline = isOnline();
        JsonNode w = water;
        String ls = w == null ? "" : w.path("litterState").asText("");
        String litterState = litterOnline && LITTER_STATES.contains(ls) ? ls : null;

        // Ưu tiên máy dọn cát khi nó có cảm biến nước (gói có waterPercent) và đang gửi số đo.
        // Bản firmware khay cát không còn cảm biến nước chỉ gửi litterState -> lấy nước từ máy cho ăn.
        if (w != null && w.has("waterPercent") && (litterOnline || !feederWaterOnline())) {
            boolean sensorOk = w.path("sensorOk").asBoolean(false) && w.path("waterPercent").asInt(-1) >= 0;
            return new Reading(litterOnline, lastSeen, sensorOk,
                    sensorOk ? w.path("waterPercent").asInt() : null,
                    sensorOk ? Math.round(w.path("waterCm").asDouble() * 10.0) / 10.0 : null,
                    w.path("lowWater").asBoolean(false), litterState, SOURCE_LITTER, litterOnline, lastSeen);
        }
        JsonNode f = feederWater;
        if (f != null) {
            boolean online = feederWaterOnline();
            int pct = f.path("waterPercent").asInt(-1);
            boolean sensorOk = pct >= 0;
            return new Reading(online, feederSeen, sensorOk, sensorOk ? Math.min(100, pct) : null, null,
                    f.path("lowWater").asBoolean(false), litterState, SOURCE_FEEDER, litterOnline, lastSeen);
        }
        // Chưa có nguồn đo nước nào (khay cát online không có nghĩa là có cảm biến nước)
        return new Reading(false, null, false, null, null, false, litterState, null, litterOnline, lastSeen);
    }

    synchronized void handleWater(String payload) throws Exception {
        JsonNode w = mapper.readTree(payload);
        water = w;
        lastSeen = Instant.now();
        if (!w.has("waterPercent")) return; // máy dọn cát không lắp cảm biến nước: chỉ là trạng thái khay
        announce(SOURCE_LITTER, w);
        checkLow(w);
    }

    /** Telemetry của máy cho ăn: chỉ dùng khi có trường waterPercent (máy có lắp cảm biến nước). */
    synchronized void handleFeederTelemetry(JsonNode t) {
        if (t == null || !t.has("waterPercent")) return;
        feederWater = t;
        feederSeen = Instant.now();
        if (isOnline() && water != null && water.has("waterPercent")) return; // máy dọn cát đang lo phần nước
        announce(SOURCE_FEEDER, t);
        checkLow(t);
    }

    private void announce(String source, JsonNode n) {
        if (announced) return;
        announced = true;
        announcedSource = source;
        alerts.add(AlertType.SYSTEM, AlertLevel.INFO, "Cảm biến mực nước đã kết nối ("
                + (SOURCE_FEEDER.equals(source) ? "trên máy cho ăn" : "máy dọn cát, IP " + n.path("ip").asText("?")) + ")");
    }

    private void checkLow(JsonNode w) {
        int pct = w.path("waterPercent").asInt(-1);
        // Cảm biến lỗi / chưa đo được: không biết mực nước -> không báo sắp cạn cũng không báo đã đổ thêm
        if (pct < 0 || !w.path("waterSensorOk").asBoolean(true)) return;
        boolean low = w.path("lowWater").asBoolean(false);
        if (low && !lastLowWater
                && Duration.between(lastLowWaterAlertAt, Instant.now()).compareTo(LOW_WATER_ALERT_GAP) > 0) {
            lastLowWaterAlertAt = Instant.now();
            lowAlerted = true;
            alerts.add(AlertType.HEALTH, AlertLevel.WARNING,
                    "Bình nước sắp cạn" + " (còn khoảng " + pct + "%)"
                            + ". Hãy đổ thêm nước để bơm không chạy khô và bé luôn có nước uống.");
        } else if (!low && lastLowWater && lowAlerted) {
            // Chỉ báo "đã đổ thêm" một lần sau mỗi lần đã cảnh báo sắp cạn
            // (mặt nước gợn quanh ngưỡng từng làm thông báo này lặp lại vài chục giây một lần)
            lowAlerted = false;
            alerts.add(AlertType.HEALTH, AlertLevel.INFO, "Đã đổ thêm nước (" + pct + "%)");
        }
        lastLowWater = low;
    }

    synchronized void checkOffline() {
        if (announced && !isOnline() && !feederWaterOnline()) {
            announced = false;
            alerts.add(AlertType.SYSTEM, AlertLevel.WARNING, SOURCE_FEEDER.equals(announcedSource)
                    ? "Cảm biến mực nước (trên máy cho ăn) mất kết nối — kiểm tra nguồn điện và Wi-Fi của ESP32"
                    : "Cảm biến mực nước (ESP32 máy dọn cát) mất kết nối — kiểm tra nguồn điện và Wi-Fi");
        }
    }
}
