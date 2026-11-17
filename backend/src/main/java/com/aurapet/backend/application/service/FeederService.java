package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.UpdateDeviceSettingsRequest;
import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import com.aurapet.backend.interfaces.web.exception.DeviceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.LinkedHashMap;
import java.util.Locale;
import java.util.Map;
import java.util.Objects;
import java.util.UUID;

/**
 * Máy cho ăn ESP32: trạng thái thời gian thực, lệnh điều khiển, nhật ký nhả hạt và lịch hẹn giờ.
 *
 *   Web/App --REST--> DeviceService --> FeederService --MQTT command--> ESP32
 *   ESP32 --telemetry (2s) / log / status--> MqttGateway --> DeviceService --> FeederService
 *
 * Trạng thái thời gian thực chỉ giữ trong RAM. Nhật ký cho ăn CHỈ được ghi khi ESP32 báo
 * đã nhả xong (số gram thật từ cân), nên không bị ghi trùng.
 */
@Slf4j
@Service
public class FeederService {

    public static final int EMPTY_BOWL_GRAM = 10;
    public static final int MIN_GRAM = 5;
    public static final int MAX_GRAM = 150;
    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(10);
    /** Sau khi gửi cài đặt xuống, bỏ qua autoMode trong telemetry một lúc để ESP32 kịp cập nhật. */
    private static final Duration SYNC_GRACE = Duration.ofSeconds(6);
    private static final Duration VISION_FEED_COOLDOWN = Duration.ofSeconds(90);
    /** Cảm biến siêu âm có thể chập chờn quanh ngưỡng: không nhắc "sắp hết hạt" lại trong 30 phút. */
    private static final Duration LOW_FOOD_ALERT_GAP = Duration.ofMinutes(30);
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DeviceSettingsService settings;
    private final FeedingLogRepository feedingLogRepository;
    private final CatIdentityTracker catIdentity;
    private final DeviceAlerts alerts;
    private final DeviceCommandPublisher publisher;
    private final ObjectMapper mapper;

    private volatile JsonNode telemetry;
    private volatile Instant lastSeen;
    private volatile boolean reportedOffline;
    private volatile boolean onlineAnnounced;
    private volatile boolean lastLowFood;
    private Instant lastLowFoodAlertAt = Instant.EPOCH;
    private volatile Instant pendingSyncUntil = Instant.EPOCH;
    private Instant lastVisionFeedAt = Instant.EPOCH;
    private String lastScheduleKey;
    /** Camera coi là đang hoạt động nếu có khung hình/sự kiện trong khoảng này. */
    private static final Duration CAMERA_ACTIVE_TIMEOUT = Duration.ofSeconds(30);
    private volatile Instant cameraSeenAt;
    /** Giá trị "sensor" đã gửi kèm lệnh AUTO lần gần nhất (null = chưa gửi). */
    private Boolean lastSensorFeedSent;

    private volatile LastFeed lastFeed = new LastFeed(null, null, null, null);
    private volatile boolean lastFeedLoaded;

    /** Lần nhả hạt gần nhất (từ RAM, hoặc từ CSDL sau khi khởi động lại). petId = bé được ghi nhật ký. */
    public record LastFeed(Instant at, Integer gram, String source, UUID petId) {
    }

    public FeederService(DeviceSettingsService settings,
                         FeedingLogRepository feedingLogRepository,
                         CatIdentityTracker catIdentity,
                         DeviceAlerts alerts,
                         DeviceCommandPublisher publisher,
                         ObjectMapper mapper) {
        this.settings = settings;
        this.feedingLogRepository = feedingLogRepository;
        this.catIdentity = catIdentity;
        this.alerts = alerts;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    // =====================================================================
    // ĐỌC TRẠNG THÁI
    // =====================================================================

    public boolean isOnline() {
        Instant seen = lastSeen;
        return !reportedOffline && seen != null
                && Duration.between(seen, Instant.now()).compareTo(ONLINE_TIMEOUT) < 0;
    }

    public boolean isBrokerConnected() {
        return publisher.isConnected();
    }

    public Instant getLastSeen() {
        return lastSeen;
    }

    /** Gói telemetry gần nhất, null nếu ESP32 chưa gửi lần nào. */
    public JsonNode getTelemetry() {
        return telemetry;
    }

    public boolean isFeeding() {
        JsonNode t = telemetry;
        return t != null && t.path("isFeeding").asBoolean(false);
    }

    public LastFeed lastFeed() {
        if (!lastFeedLoaded) {
            lastFeedLoaded = true;
            feedingLogRepository.findFirstByOrderByTimestampDesc().ifPresent(f -> {
                if (lastFeed.at() == null) {
                    lastFeed = new LastFeed(f.getTimestamp(), f.getFoodGram(), f.getSource(), f.getPetId());
                }
            });
        }
        return lastFeed;
    }

    // =====================================================================
    // LỆNH ĐIỀU KHIỂN (Web/App)
    // =====================================================================

    public void feed(Integer gram, String source) {
        requireOnline();
        if (isFeeding()) {
            throw new BadRequestException("Máy đang nhả hạt, vui lòng đợi lượt hiện tại xong");
        }
        int g = clampGram(gram != null ? gram : settings.get().getTargetGram());
        String src = cleanSource(source);
        sendCommand(cmd("FEED", "gram", g, "source", src));
        log.info("[Feeder] Lệnh cho ăn {}g từ {}", g, src);
    }

    public void stop() {
        requireOnline();
        sendCommand(cmd("STOP"));
    }

    public void tare() {
        requireOnline();
        if (isFeeding()) {
            throw new BadRequestException("Không thể trừ bì khi máy đang nhả hạt");
        }
        sendCommand(cmd("TARE"));
    }

    /**
     * Lưu cài đặt rồi đồng bộ xuống ESP32. Chạy dưới khoá của máy cho ăn để telemetry
     * đến cùng lúc không đồng bộ ngược autoMode cũ đè lên giá trị vừa lưu.
     */
    public synchronized void updateSettings(UpdateDeviceSettingsRequest r, Actor actor) {
        DeviceSettingsService.Change change = settings.apply(r, actor);
        if (isOnline()) {
            DeviceSettings s = settings.get();
            if (change.targetChanged()) sendCommand(cmd("SET_TARGET", "gram", s.getTargetGram()));
            if (change.autoChanged()) sendAuto(s.getAutoMode());
            pendingSyncUntil = Instant.now().plus(SYNC_GRACE);
        }
    }

    /**
     * Camera thấy mèo: tự cho ăn nếu bật Tự động, khay gần hết và đã qua thời gian chờ.
     * gram = khẩu phần riêng của bé camera nhận ra; null = khẩu phần chung.
     * @return true nếu đã gửi lệnh nhả hạt
     */
    public synchronized boolean requestVisionFeed(String source, Integer gram) {
        JsonNode t = telemetry;
        if (!isOnline() || t == null) return false;
        DeviceSettings s = settings.get();
        boolean bowlEmpty = t.path("weight").asDouble(0) < EMPTY_BOWL_GRAM;
        boolean cooled = Duration.between(lastVisionFeedAt, Instant.now()).compareTo(VISION_FEED_COOLDOWN) > 0;
        if (Boolean.TRUE.equals(s.getAutoMode()) && bowlEmpty && cooled && !isFeeding()) {
            lastVisionFeedAt = Instant.now();
            int g = clampGram(gram != null ? gram : s.getTargetGram());
            sendCommand(cmd("FEED", "gram", g, "source", source));
            return true;
        }
        return false;
    }

    // =====================================================================
    // CAMERA ĐANG CHẠY -> ESP32 KHÔNG TỰ NHẢ THEO CẢM BIẾN
    // Cảm biến hồng ngoại trên ESP32 thấy mèo sớm hơn camera và chỉ biết khẩu phần chung.
    // Khi camera hoạt động, backend tự cho ăn theo khẩu phần của từng bé nên báo ESP32
    // tắt việc tự nhả theo cảm biến ("sensor": false); camera tắt thì bật lại.
    // =====================================================================

    /** Camera vừa gửi khung hình hoặc sự kiện. */
    public void cameraSeen() {
        cameraSeenAt = Instant.now();
    }

    public boolean isCameraActive() {
        Instant seen = cameraSeenAt;
        return seen != null && Duration.between(seen, Instant.now()).compareTo(CAMERA_ACTIVE_TIMEOUT) < 0;
    }

    /** Gọi định kỳ: gửi lại lệnh AUTO khi camera bật/tắt để ESP32 bật/tắt tự nhả theo cảm biến. */
    synchronized void syncSensorFeed() {
        boolean sensor = !isCameraActive();
        if (isOnline() && !Objects.equals(lastSensorFeedSent, sensor)) {
            sendAuto(settings.get().getAutoMode());
            log.info("[Feeder] Camera {} -> ESP32 {} tự nhả theo cảm biến",
                    sensor ? "không hoạt động" : "đang hoạt động", sensor ? "BẬT" : "TẮT");
        }
    }

    private void sendAuto(Boolean enabled) {
        boolean sensor = !isCameraActive();
        sendCommand(cmd("AUTO", "enabled", enabled, "sensor", sensor));
        lastSensorFeedSent = sensor;
    }

    // =====================================================================
    // GÓI TIN TỪ ESP32
    // =====================================================================

    synchronized void handleTelemetry(String payload) throws Exception {
        JsonNode t = mapper.readTree(payload);
        telemetry = t;
        lastSeen = Instant.now();
        reportedOffline = false;

        if (!onlineAnnounced) {
            onlineAnnounced = true;
            alerts.add(AlertType.SYSTEM, AlertLevel.INFO, "Máy cho ăn đã kết nối (IP " + t.path("ip").asText("?") + ")");
            pushSettingsToDevice();
        }

        boolean low = t.path("lowFood").asBoolean(false);
        if (low && !lastLowFood
                && Duration.between(lastLowFoodAlertAt, Instant.now()).compareTo(LOW_FOOD_ALERT_GAP) > 0) {
            lastLowFoodAlertAt = Instant.now();
            alerts.add(AlertType.FEEDING, AlertLevel.WARNING,
                    "Thùng chứa sắp hết hạt (còn khoảng " + t.path("foodLevelPercent").asInt(0) + "%). Hãy châm thêm hạt.");
        }
        lastLowFood = low;

        // Người dùng bấm nút AUTO vật lý trên máy -> đồng bộ ngược về cài đặt
        if (Instant.now().isAfter(pendingSyncUntil) && t.has("autoMode")) {
            boolean deviceAuto = t.path("autoMode").asBoolean();
            if (!Objects.equals(settings.get().getAutoMode(), deviceAuto)) {
                settings.update(s -> s.setAutoMode(deviceAuto));
                log.info("[Feeder] Đồng bộ chế độ tự động từ nút vật lý: {}", deviceAuto);
            }
        }
    }

    synchronized void handleFeedingLog(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        double added = n.path("addedGram").asDouble(0);
        String source = n.path("source").asText("Máy cho ăn");
        String result = n.path("result").asText("");

        if (added < 1.0) {
            if (!"STOPPED".equalsIgnoreCase(result)) {
                alerts.add(AlertType.FEEDING, AlertLevel.WARNING,
                        "Động cơ đã chạy nhưng cân không tăng — kiểm tra thùng chứa hết hạt hoặc bị kẹt.");
            }
            return;
        }

        int gram = (int) Math.round(added);
        // Ưu tiên bé vừa được camera nhận diện, nếu không thì bé được gán cho máy
        Pet pet = catIdentity.consume().orElseGet(settings::resolveFeederPet);
        Instant now = Instant.now();
        lastFeed = new LastFeed(now, gram, source, pet != null ? pet.getId() : null);
        lastFeedLoaded = true;

        if (pet == null) {
            alerts.add(AlertType.FEEDING, AlertLevel.INFO, "Đã nhả " + gram + "g (" + source + ") — chưa có thú cưng để ghi nhật ký");
            return;
        }
        feedingLogRepository.save(FeedingLog.builder()
                .petId(pet.getId())
                .foodGram(gram)
                .source(source)
                .timestamp(now)
                .build());
        String note = "STOPPED".equalsIgnoreCase(result) ? " (đã dừng giữa chừng)" : "";
        alerts.add(AlertType.FEEDING, AlertLevel.INFO, "Đã nhả " + gram + "g cho " + pet.getName() + " — " + source + note,
                pet.getId());
    }

    synchronized void handleStatus(String payload) {
        String p = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        if (p.contains("offline")) {
            reportedOffline = true;
            checkOffline();
        }
    }

    // =====================================================================
    // LỊCH HẸN GIỜ + MẤT KẾT NỐI (DeviceService gọi mỗi 15 giây)
    // =====================================================================

    synchronized void runSchedule(LocalDateTime now) {
        DeviceSettings s = settings.get();
        if (!Boolean.TRUE.equals(s.getScheduleEnabled())) return;

        String hhmm = now.format(HH_MM);
        if (!DeviceSettingsService.splitTimes(s.getScheduleTimes()).contains(hhmm)) return;

        String key = now.toLocalDate() + " " + hhmm;
        if (key.equals(lastScheduleKey)) return;
        lastScheduleKey = key;

        JsonNode t = telemetry;
        if (!isOnline() || t == null) {
            alerts.add(AlertType.FEEDING, AlertLevel.WARNING, "Lỡ bữa " + hhmm + " vì máy cho ăn đang offline");
            return;
        }
        double bowl = t.path("weight").asDouble(0);
        if (bowl >= EMPTY_BOWL_GRAM) {
            alerts.add(AlertType.FEEDING, AlertLevel.INFO, "Bỏ qua bữa " + hhmm + " vì khay còn " + Math.round(bowl) + "g");
            return;
        }
        sendCommand(cmd("FEED", "gram", s.getTargetGram(), "source", "Lịch " + hhmm));
    }

    synchronized void checkOffline() {
        if (onlineAnnounced && !isOnline()) {
            onlineAnnounced = false;
            alerts.add(AlertType.SYSTEM, AlertLevel.WARNING, "Máy cho ăn mất kết nối — kiểm tra nguồn điện và Wi-Fi");
        }
    }

    // =====================================================================
    // TIỆN ÍCH
    // =====================================================================

    private void pushSettingsToDevice() {
        DeviceSettings s = settings.get();
        sendCommand(cmd("SET_TARGET", "gram", s.getTargetGram()));
        sendAuto(s.getAutoMode());
        pendingSyncUntil = Instant.now().plus(SYNC_GRACE);
    }

    private void requireOnline() {
        if (!publisher.isConnected()) {
            throw new DeviceUnavailableException("Máy chủ chưa kết nối được MQTT broker. Kiểm tra cấu hình MQTT_* trong file .env");
        }
        if (!isOnline()) {
            throw new DeviceUnavailableException("Máy cho ăn đang offline — kiểm tra nguồn điện và Wi-Fi của ESP32");
        }
    }

    private void sendCommand(Map<String, Object> command) {
        if (!publisher.publishCommand(command)) {
            log.warn("[Feeder] Không gửi được lệnh {} (broker chưa kết nối)", command.get("action"));
        }
    }

    private static Map<String, Object> cmd(String action, Object... kv) {
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("action", action);
        for (int i = 0; i + 1 < kv.length; i += 2) {
            m.put(String.valueOf(kv[i]), kv[i + 1]);
        }
        return m;
    }

    static int clampGram(int g) {
        return Math.max(MIN_GRAM, Math.min(MAX_GRAM, g));
    }

    static String cleanSource(String source) {
        if (source == null) return "API";
        String s = source.trim();
        if (s.equalsIgnoreCase("web")) return "Web";
        if (s.equalsIgnoreCase("app")) return "App";
        return "API";
    }
}
