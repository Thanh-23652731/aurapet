package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.DeviceSettingsRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import com.aurapet.backend.interfaces.web.exception.DeviceUnavailableException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.context.event.EventListener;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Service;

import java.time.Duration;
import java.time.Instant;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.regex.Pattern;

/**
 * Bộ não điều khiển máy cho ăn.
 *
 * Luồng dữ liệu:
 *   Web/App --REST--> DeviceService --MQTT command--> ESP32
 *   ESP32 --telemetry (2s)/log/status--> MqttGateway --event--> DeviceService
 *
 * Trạng thái thời gian thực chỉ giữ trong RAM (không ghi DB mỗi 2 giây).
 * Nhật ký cho ăn CHỈ được ghi khi ESP32 báo đã nhả xong (số gram thật từ cân),
 * nên không còn bị ghi trùng.
 */
@Slf4j
@Service
public class DeviceService {

    public static final int EMPTY_BOWL_GRAM = 10;
    public static final int MIN_GRAM = 5;
    public static final int MAX_GRAM = 150;
    private static final Duration ONLINE_TIMEOUT = Duration.ofSeconds(10);
    private static final Duration SYNC_GRACE = Duration.ofSeconds(6);
    private static final Duration VISION_FEED_COOLDOWN = Duration.ofSeconds(90);
    private static final Pattern TIME_PATTERN = Pattern.compile("^([01]\\d|2[0-3]):[0-5]\\d$");
    private static final DateTimeFormatter HH_MM = DateTimeFormatter.ofPattern("HH:mm");

    private final DeviceSettingsRepository settingsRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;
    private final PetRepository petRepository;
    private final AlertRepository alertRepository;
    private final DeviceCommandPublisher publisher;
    private final ObjectMapper mapper;

    // ---- Trạng thái thời gian thực (RAM) ----
    private volatile JsonNode telemetry;
    private volatile Instant lastSeen;
    private volatile boolean reportedOffline;
    private volatile boolean onlineAnnounced;
    private volatile boolean lastLowFood;
    /** Cảm biến siêu âm có thể chập chờn quanh ngưỡng: không nhắc "sắp hết hạt" lại trong 30 phút. */
    private static final Duration LOW_FOOD_ALERT_GAP = Duration.ofMinutes(30);
    private Instant lastLowFoodAlertAt = Instant.EPOCH;
    private volatile Instant pendingSyncUntil = Instant.EPOCH;
    private volatile Instant cameraLastSeen;
    private volatile Instant lastVisionFeedAt = Instant.EPOCH;
    private volatile String lastScheduleKey;

    // ---- Bình nước (ESP32 máy dọn cát gửi PREFIX/water 5 giây/lần) ----
    private static final Duration WATER_ONLINE_TIMEOUT = Duration.ofSeconds(30);
    /** Sóng nước làm số đo nhảy quanh ngưỡng: không nhắc "sắp hết nước" lại trong 30 phút. */
    private static final Duration LOW_WATER_ALERT_GAP = Duration.ofMinutes(30);
    private volatile JsonNode water;
    private volatile Instant waterLastSeen;
    private volatile boolean waterAnnounced;
    private volatile boolean lastLowWater;
    private Instant lastLowWaterAlertAt = Instant.EPOCH;

    // ---- Khay vệ sinh (ESP32 máy dọn cát gửi sự kiện PREFIX/litter) ----
    /** ESP32 gửi bù sự kiện lúc mất mạng: bỏ qua sự kiện cũ hơn mức này (đồng hồ ESP32 có thể đã khởi động lại). */
    private static final long MAX_EVENT_AGE_SEC = 7 * 24 * 3600;
    private static final Set<String> LITTER_STATES = Set.of("idle", "cat_inside", "waiting", "cleaning");
    private volatile Instant lastLitterVisitAt;
    private volatile Integer lastLitterVisitSec;
    private volatile String lastLitterVisitPetName;
    private volatile boolean lastLitterLoaded;

    private volatile Instant lastFeedAt;
    private volatile Integer lastFeedGram;
    private volatile String lastFeedSource;
    private volatile boolean lastFeedLoaded;

    private volatile DeviceSettings cachedSettings;

    public DeviceService(DeviceSettingsRepository settingsRepository,
                         FeedingLogRepository feedingLogRepository,
                         LitterLogRepository litterLogRepository,
                         PetRepository petRepository,
                         AlertRepository alertRepository,
                         DeviceCommandPublisher publisher,
                         ObjectMapper mapper) {
        this.settingsRepository = settingsRepository;
        this.feedingLogRepository = feedingLogRepository;
        this.litterLogRepository = litterLogRepository;
        this.petRepository = petRepository;
        this.alertRepository = alertRepository;
        this.publisher = publisher;
        this.mapper = mapper;
    }

    // =====================================================================
    // 1. ĐỌC TRẠNG THÁI
    // =====================================================================

    public boolean isOnline() {
        Instant seen = lastSeen;
        return !reportedOffline && seen != null
                && Duration.between(seen, Instant.now()).compareTo(ONLINE_TIMEOUT) < 0;
    }

    public DeviceStateDto getState() {
        DeviceSettings s = getSettings();
        JsonNode t = telemetry;
        boolean online = isOnline();
        loadLastFeedIfNeeded();

        DeviceStateDto.DeviceStateDtoBuilder b = DeviceStateDto.builder()
                .brokerConnected(publisher.isConnected())
                .online(online)
                .lastSeen(lastSeen)
                .lastFeedAt(lastFeedAt)
                .lastFeedGram(lastFeedGram)
                .lastFeedSource(lastFeedSource)
                .cameraLastSeen(cameraLastSeen)
                .settings(toSettingsDto(s));

        if (t != null) {
            b.bowlWeightGram(round1(t.path("weight").asDouble(0)))
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

        JsonNode w = water;
        boolean wOnline = isWaterOnline();
        b.waterOnline(wOnline).waterLastSeen(waterLastSeen);
        if (w != null) {
            boolean sensorOk = w.path("sensorOk").asBoolean(false) && w.path("waterPercent").asInt(-1) >= 0;
            b.waterSensorOk(sensorOk)
                    .waterPercent(sensorOk ? w.path("waterPercent").asInt() : null)
                    .waterCm(sensorOk ? round1(w.path("waterCm").asDouble()) : null)
                    .lowWater(w.path("lowWater").asBoolean(false));
            String ls = w.path("litterState").asText("");
            if (wOnline && LITTER_STATES.contains(ls)) b.litterState(ls);
        }

        loadLastLitterVisitIfNeeded();
        b.lastLitterVisitAt(lastLitterVisitAt)
                .lastLitterVisitSec(lastLitterVisitSec)
                .lastLitterVisitPetName(lastLitterVisitPetName)
                .lastLitterCleanAt(s.getLastLitterCleanAt());
        return b.build();
    }

    public boolean isWaterOnline() {
        Instant seen = waterLastSeen;
        return seen != null && Duration.between(seen, Instant.now()).compareTo(WATER_ONLINE_TIMEOUT) < 0;
    }

    // =====================================================================
    // 2. LỆNH ĐIỀU KHIỂN (gọi từ Web/App)
    // =====================================================================

    public DeviceStateDto feed(Integer gram, String source) {
        requireOnline();
        if (telemetry != null && telemetry.path("isFeeding").asBoolean(false)) {
            throw new BadRequestException("Máy đang nhả hạt, vui lòng đợi lượt hiện tại xong");
        }
        int g = clampGram(gram != null ? gram : getSettings().getTargetGram());
        sendCommand(cmd("FEED", "gram", g, "source", cleanSource(source)));
        log.info("[Device] Lệnh cho ăn {}g từ {}", g, cleanSource(source));
        return getState();
    }

    public DeviceStateDto stop() {
        requireOnline();
        sendCommand(cmd("STOP"));
        return getState();
    }

    public DeviceStateDto tare() {
        requireOnline();
        if (telemetry != null && telemetry.path("isFeeding").asBoolean(false)) {
            throw new BadRequestException("Không thể trừ bì khi máy đang nhả hạt");
        }
        sendCommand(cmd("TARE"));
        return getState();
    }

    public synchronized DeviceStateDto updateSettings(UpdateDeviceSettingsRequest r) {
        DeviceSettings s = getSettings();
        boolean pushTarget = false, pushAuto = false;

        if (r.getTargetGram() != null) {
            s.setTargetGram(clampGram(r.getTargetGram()));
            pushTarget = true;
        }
        if (r.getAutoMode() != null) {
            s.setAutoMode(r.getAutoMode());
            pushAuto = true;
        }
        if (r.getScheduleEnabled() != null) {
            s.setScheduleEnabled(r.getScheduleEnabled());
        }
        if (r.getScheduleTimes() != null) {
            s.setScheduleTimes(String.join(",", normalizeTimes(r.getScheduleTimes())));
        }
        if (r.getFeederPetId() != null) {
            if (!petRepository.existsById(r.getFeederPetId())) {
                throw new BadRequestException("Không tìm thấy thú cưng được chọn cho máy cho ăn");
            }
            s.setFeederPetId(r.getFeederPetId());
        }

        saveSettings(s);

        if (isOnline()) {
            if (pushTarget) sendCommand(cmd("SET_TARGET", "gram", s.getTargetGram()));
            if (pushAuto) sendCommand(cmd("AUTO", "enabled", s.getAutoMode()));
            pendingSyncUntil = Instant.now().plus(SYNC_GRACE);
        }
        return getState();
    }

    // =====================================================================
    // 3. XỬ LÝ GÓI TIN TỪ THIẾT BỊ
    // =====================================================================

    @EventListener
    public void onDeviceMessage(DeviceMessageEvent event) {
        try {
            switch (event.channel()) {
                case "telemetry" -> handleTelemetry(event.payload());
                case "log" -> handleFeedingLog(event.payload());
                case "status" -> handleStatus(event.payload());
                case "vision" -> handleVision(event.payload());
                case "water" -> handleWater(event.payload());
                case "litter" -> handleLitter(event.payload());
                default -> log.debug("[Device] Bỏ qua channel lạ: {}", event.channel());
            }
        } catch (Exception e) {
            log.warn("[Device] Lỗi xử lý gói tin {}: {}", event.channel(), e.getMessage());
        }
    }

    synchronized void handleTelemetry(String payload) throws Exception {
        JsonNode t = mapper.readTree(payload);
        this.telemetry = t;
        this.lastSeen = Instant.now();
        this.reportedOffline = false;

        if (!onlineAnnounced) {
            onlineAnnounced = true;
            addAlert(AlertType.SYSTEM, AlertLevel.INFO, "Máy cho ăn đã kết nối (IP " + t.path("ip").asText("?") + ")");
            pushSettingsToDevice();
        }

        boolean low = t.path("lowFood").asBoolean(false);
        if (low && !lastLowFood
                && Duration.between(lastLowFoodAlertAt, Instant.now()).compareTo(LOW_FOOD_ALERT_GAP) > 0) {
            lastLowFoodAlertAt = Instant.now();
            addAlert(AlertType.FEEDING, AlertLevel.WARNING,
                    "Thùng chứa sắp hết hạt (còn khoảng " + t.path("foodLevelPercent").asInt(0) + "%). Hãy châm thêm hạt.");
        }
        lastLowFood = low;

        // Người dùng bấm nút AUTO vật lý trên máy -> đồng bộ ngược về cài đặt
        if (Instant.now().isAfter(pendingSyncUntil) && t.has("autoMode")) {
            DeviceSettings s = getSettings();
            boolean deviceAuto = t.path("autoMode").asBoolean();
            if (!Objects.equals(s.getAutoMode(), deviceAuto)) {
                s.setAutoMode(deviceAuto);
                saveSettings(s);
                log.info("[Device] Đồng bộ chế độ tự động từ nút vật lý: {}", deviceAuto);
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
                addAlert(AlertType.FEEDING, AlertLevel.WARNING,
                        "Động cơ đã chạy nhưng cân không tăng — kiểm tra thùng chứa hết hạt hoặc bị kẹt.");
            }
            return;
        }

        int gram = (int) Math.round(added);
        Pet pet = resolveFeederPet();
        Instant now = Instant.now();
        lastFeedAt = now;
        lastFeedGram = gram;
        lastFeedSource = source;
        lastFeedLoaded = true;

        if (pet == null) {
            addAlert(AlertType.FEEDING, AlertLevel.INFO, "Đã nhả " + gram + "g (" + source + ") — chưa có thú cưng để ghi nhật ký");
            return;
        }
        feedingLogRepository.save(FeedingLog.builder()
                .petId(pet.getId())
                .foodGram(gram)
                .source(source)
                .timestamp(now)
                .build());
        String note = "STOPPED".equalsIgnoreCase(result) ? " (đã dừng giữa chừng)" : "";
        addAlert(AlertType.FEEDING, AlertLevel.INFO, "Đã nhả " + gram + "g cho " + pet.getName() + " — " + source + note);
    }

    synchronized void handleStatus(String payload) {
        String p = payload == null ? "" : payload.trim().toLowerCase(Locale.ROOT);
        if (p.contains("offline")) {
            reportedOffline = true;
            announceOfflineIfNeeded();
        }
    }

    /** Camera vừa gửi khung hình lên (xem CameraService) -> web biết camera đang hoạt động. */
    public void markCameraSeen() {
        cameraLastSeen = Instant.now();
    }

    synchronized void handleVision(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        cameraLastSeen = Instant.now();
        if (!n.path("catPresent").asBoolean(false) || !isOnline() || telemetry == null) {
            return;
        }
        DeviceSettings s = getSettings();

        // Mèo đến gần: chỉ cho ăn khi bật Tự động và khay gần hết (bơm nước tuần hoàn chạy liên tục, không điều khiển)
        boolean bowlEmpty = telemetry.path("weight").asDouble(0) < EMPTY_BOWL_GRAM;
        boolean cooled = Duration.between(lastVisionFeedAt, Instant.now()).compareTo(VISION_FEED_COOLDOWN) > 0;
        if (Boolean.TRUE.equals(s.getAutoMode()) && bowlEmpty && cooled
                && !telemetry.path("isFeeding").asBoolean(false)) {
            lastVisionFeedAt = Instant.now();
            sendCommand(cmd("FEED", "gram", s.getTargetGram(), "source", "Camera AI"));
        }
    }

    /** Mực nước từ ESP32 máy dọn cát: báo web/app khi sắp hết để người dùng đổ thêm. */
    synchronized void handleWater(String payload) throws Exception {
        JsonNode w = mapper.readTree(payload);
        this.water = w;
        this.waterLastSeen = Instant.now();

        if (!waterAnnounced) {
            waterAnnounced = true;
            addAlert(AlertType.SYSTEM, AlertLevel.INFO, "Cảm biến mực nước đã kết nối (IP " + w.path("ip").asText("?") + ")");
        }

        boolean low = w.path("lowWater").asBoolean(false);
        int pct = w.path("waterPercent").asInt(-1);
        if (low && !lastLowWater
                && Duration.between(lastLowWaterAlertAt, Instant.now()).compareTo(LOW_WATER_ALERT_GAP) > 0) {
            lastLowWaterAlertAt = Instant.now();
            addAlert(AlertType.HEALTH, AlertLevel.WARNING,
                    "Bình nước sắp cạn" + (pct >= 0 ? " (còn khoảng " + pct + "%)" : "")
                            + ". Hãy đổ thêm nước để bơm không chạy khô và bé luôn có nước uống.");
        } else if (!low && lastLowWater) {
            addAlert(AlertType.HEALTH, AlertLevel.INFO, "Đã đổ thêm nước" + (pct >= 0 ? " (" + pct + "%)" : ""));
        }
        lastLowWater = low;
    }

    /**
     * Sự kiện khay vệ sinh từ ESP32 máy dọn cát:
     *   visit          mèo đi vệ sinh xong 1 lượt (durationSec = thời gian ở trong khay) -> ghi litter_logs
     *   cleaned        máy cào cát xong
     *   clean_stopped  dừng cào giữa chừng vì mèo vào khay
     * agoSec: số giây từ lúc xảy ra tới lúc gửi (ESP32 gửi bù sau khi mất mạng).
     */
    synchronized void handleLitter(String payload) throws Exception {
        JsonNode n = mapper.readTree(payload);
        String event = n.path("event").asText("");
        long ago = Math.max(0, n.path("agoSec").asLong(0));
        if (ago > MAX_EVENT_AGE_SEC) {
            log.info("[Device] Bỏ qua sự kiện khay quá cũ ({} giây): {}", ago, event);
            return;
        }
        Instant at = Instant.now().minusSeconds(ago);
        waterLastSeen = Instant.now(); // cùng ESP32 với cảm biến nước -> máy đang online

        switch (event) {
            case "visit" -> {
                int sec = Math.max(0, n.path("durationSec").asInt(0));
                Pet pet = resolveFeederPet();
                loadLastLitterVisitIfNeeded();
                if (lastLitterVisitAt == null || !at.isBefore(lastLitterVisitAt)) {
                    lastLitterVisitAt = at;
                    lastLitterVisitSec = sec;
                    lastLitterVisitPetName = pet != null ? pet.getName() : null;
                }
                if (pet == null) {
                    addAlert(AlertType.LITTER, AlertLevel.INFO,
                            "Mèo vừa đi vệ sinh (" + formatDuration(sec) + ") — chưa có thú cưng để ghi nhật ký");
                    return;
                }
                litterLogRepository.save(LitterLog.builder()
                        .petId(pet.getId())
                        .result(LitterResult.NORMAL)
                        .durationSec(sec)
                        .timestamp(at)
                        .build());
                addAlert(AlertType.LITTER, AlertLevel.INFO,
                        pet.getName() + " vừa đi vệ sinh (" + formatDuration(sec) + " trong khay)");
            }
            case "cleaned" -> {
                DeviceSettings s = getSettings();
                if (s.getLastLitterCleanAt() == null || at.isAfter(s.getLastLitterCleanAt())) {
                    s.setLastLitterCleanAt(at);
                    saveSettings(s);
                }
                addAlert(AlertType.LITTER, AlertLevel.INFO, "Máy đã dọn cát xong");
            }
            case "clean_stopped" -> addAlert(AlertType.LITTER, AlertLevel.INFO,
                    "Máy dừng cào cát giữa chừng vì mèo vào khay, sẽ dọn lại sau khi bé ra");
            default -> log.debug("[Device] Bỏ qua sự kiện khay lạ: {}", event);
        }
    }

    private void loadLastLitterVisitIfNeeded() {
        if (lastLitterLoaded) return;
        lastLitterLoaded = true;
        litterLogRepository.findFirstByOrderByTimestampDesc().ifPresent(l -> {
            lastLitterVisitAt = l.getTimestamp();
            lastLitterVisitSec = l.getDurationSec();
            lastLitterVisitPetName = petRepository.findById(l.getPetId()).map(Pet::getName).orElse(null);
        });
    }

    static String formatDuration(int sec) {
        if (sec < 60) return sec + " giây";
        int m = sec / 60, s = sec % 60;
        return s == 0 ? m + " phút" : m + " phút " + s + " giây";
    }

    // =====================================================================
    // 4. LỊCH HẸN GIỜ + THEO DÕI MẤT KẾT NỐI (chạy mỗi 15 giây)
    // =====================================================================

    @Scheduled(fixedDelay = 15000, initialDelay = 15000)
    public void tick() {
        try {
            announceOfflineIfNeeded();
            runSchedule(LocalDateTime.now(AppTime.ZONE));
        } catch (Exception e) {
            log.warn("[Device] Lỗi tick lịch hẹn: {}", e.getMessage());
        }
    }

    synchronized void runSchedule(LocalDateTime now) {
        DeviceSettings s = getSettings();
        if (!Boolean.TRUE.equals(s.getScheduleEnabled())) return;

        String hhmm = now.format(HH_MM);
        if (!splitTimes(s.getScheduleTimes()).contains(hhmm)) return;

        String key = now.toLocalDate() + " " + hhmm;
        if (key.equals(lastScheduleKey)) return;
        lastScheduleKey = key;

        if (!isOnline() || telemetry == null) {
            addAlert(AlertType.FEEDING, AlertLevel.WARNING, "Lỡ bữa " + hhmm + " vì máy cho ăn đang offline");
            return;
        }
        double bowl = telemetry.path("weight").asDouble(0);
        if (bowl >= EMPTY_BOWL_GRAM) {
            addAlert(AlertType.FEEDING, AlertLevel.INFO,
                    "Bỏ qua bữa " + hhmm + " vì khay còn " + Math.round(bowl) + "g");
            return;
        }
        sendCommand(cmd("FEED", "gram", s.getTargetGram(), "source", "Lịch " + hhmm));
    }

    private synchronized void announceOfflineIfNeeded() {
        if (onlineAnnounced && !isOnline()) {
            onlineAnnounced = false;
            addAlert(AlertType.SYSTEM, AlertLevel.WARNING, "Máy cho ăn mất kết nối — kiểm tra nguồn điện và Wi-Fi");
        }
        if (waterAnnounced && !isWaterOnline()) {
            waterAnnounced = false;
            addAlert(AlertType.SYSTEM, AlertLevel.WARNING,
                    "Cảm biến mực nước (ESP32 máy dọn cát) mất kết nối — kiểm tra nguồn điện và Wi-Fi");
        }
    }

    // =====================================================================
    // 5. CÀI ĐẶT
    // =====================================================================

    public DeviceSettings getSettings() {
        DeviceSettings s = cachedSettings;
        if (s == null) {
            synchronized (this) {
                if (cachedSettings == null) {
                    cachedSettings = settingsRepository.findById(DeviceSettings.SINGLETON_ID)
                            .orElseGet(() -> settingsRepository.save(defaultSettings()));
                }
                s = cachedSettings;
            }
        }
        return s;
    }

    private void saveSettings(DeviceSettings s) {
        cachedSettings = settingsRepository.save(s);
    }

    static DeviceSettings defaultSettings() {
        return DeviceSettings.builder()
                .id(DeviceSettings.SINGLETON_ID)
                .targetGram(30)
                .autoMode(false)
                .scheduleEnabled(false)
                .scheduleTimes("07:00,12:00,18:00")
                .build();
    }

    private void pushSettingsToDevice() {
        DeviceSettings s = getSettings();
        sendCommand(cmd("SET_TARGET", "gram", s.getTargetGram()));
        sendCommand(cmd("AUTO", "enabled", s.getAutoMode()));
        pendingSyncUntil = Instant.now().plus(SYNC_GRACE);
    }

    private DeviceSettingsDto toSettingsDto(DeviceSettings s) {
        Pet pet = resolveFeederPet();
        return DeviceSettingsDto.builder()
                .targetGram(s.getTargetGram())
                .autoMode(s.getAutoMode())
                .scheduleEnabled(s.getScheduleEnabled())
                .scheduleTimes(splitTimes(s.getScheduleTimes()))
                .feederPetId(pet != null ? pet.getId() : null)
                .feederPetName(pet != null ? pet.getName() : null)
                .build();
    }

    /** Mèo được gán cho máy; nếu chưa gán (hoặc đã bị xoá) thì lấy con được tạo sớm nhất. */
    public Pet resolveFeederPet() {
        UUID id = getSettings().getFeederPetId();
        if (id != null) {
            Optional<Pet> p = petRepository.findById(id);
            if (p.isPresent()) return p.get();
        }
        List<Pet> all = petRepository.findAllByOrderByCreatedAtAsc();
        return all.isEmpty() ? null : all.get(0);
    }

    // =====================================================================
    // 6. TIỆN ÍCH
    // =====================================================================

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
            log.warn("[Device] Không gửi được lệnh {} (broker chưa kết nối)", command.get("action"));
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

    private void addAlert(AlertType type, AlertLevel level, String message) {
        try {
            alertRepository.save(Alert.builder()
                    .type(type)
                    .level(level)
                    .message(message)
                    .createdAt(Instant.now())
                    .build());
        } catch (Exception e) {
            log.warn("[Device] Không lưu được cảnh báo: {}", e.getMessage());
        }
    }

    private void loadLastFeedIfNeeded() {
        if (lastFeedLoaded) return;
        lastFeedLoaded = true;
        feedingLogRepository.findFirstByOrderByTimestampDesc().ifPresent(f -> {
            lastFeedAt = f.getTimestamp();
            lastFeedGram = f.getFoodGram();
            lastFeedSource = f.getSource();
        });
    }

    static int clampGram(int g) {
        return Math.max(MIN_GRAM, Math.min(MAX_GRAM, g));
    }

    static List<String> normalizeTimes(List<String> times) {
        TreeSet<String> out = new TreeSet<>();
        for (String raw : times) {
            if (raw == null) continue;
            String t = raw.trim();
            if (t.length() == 4 && t.charAt(1) == ':') t = "0" + t; // 7:00 -> 07:00
            if (!TIME_PATTERN.matcher(t).matches()) {
                throw new BadRequestException("Giờ không hợp lệ: \"" + raw + "\" (định dạng HH:mm, ví dụ 07:30)");
            }
            out.add(t);
        }
        return new ArrayList<>(out);
    }

    static List<String> splitTimes(String csv) {
        if (csv == null || csv.isBlank()) return new ArrayList<>();
        List<String> out = new ArrayList<>();
        for (String p : csv.split(",")) {
            String t = p.trim();
            if (!t.isEmpty()) out.add(t);
        }
        return out;
    }

    static String cleanSource(String source) {
        if (source == null) return "API";
        String s = source.trim();
        if (s.equalsIgnoreCase("web")) return "Web";
        if (s.equalsIgnoreCase("app")) return "App";
        return "API";
    }

    private static String textOrNull(JsonNode n, String field) {
        JsonNode v = n.get(field);
        return v == null || v.isNull() ? null : v.asText();
    }

    private static double round1(double v) {
        return Math.round(v * 10.0) / 10.0;
    }

    /** Dùng cho DashboardService/AI: thời điểm hiện tại theo HH:mm. */
    public static String nowHhMm() {
        return LocalDateTime.now(AppTime.ZONE).format(HH_MM);
    }
}
