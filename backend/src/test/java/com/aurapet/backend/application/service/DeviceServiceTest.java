package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.UpdateDeviceSettingsRequest;
import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.DeviceSettingsRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import com.aurapet.backend.interfaces.web.exception.DeviceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.security.access.AccessDeniedException;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceServiceTest {

    private static final Actor ADMIN = new Actor(UUID.randomUUID(), true);

    /** Bộ gửi lệnh giả: ghi lại các lệnh thay vì gửi MQTT thật. */
    static class FakePublisher implements DeviceCommandPublisher {
        boolean connected = true;
        final List<Map<String, Object>> sent = new ArrayList<>();

        @Override
        public boolean isConnected() {
            return connected;
        }

        @Override
        public boolean publishCommand(Map<String, Object> command) {
            sent.add(command);
            return connected;
        }

        List<String> actions() {
            return sent.stream().map(c -> String.valueOf(c.get("action"))).toList();
        }
    }

    private DeviceSettingsRepository settingsRepo;
    private FeedingLogRepository feedingRepo;
    private PetRepository petRepo;
    private AlertRepository alertRepo;
    private LitterLogRepository litterRepo;
    private FakePublisher publisher;
    private DeviceSettingsService settingsService;
    private FeederService feeder;
    private DeviceService service;
    private Pet luna;

    @BeforeEach
    void setUp() {
        settingsRepo = mock(DeviceSettingsRepository.class);
        feedingRepo = mock(FeedingLogRepository.class);
        petRepo = mock(PetRepository.class);
        alertRepo = mock(AlertRepository.class);
        publisher = new FakePublisher();

        when(settingsRepo.findById(DeviceSettings.SINGLETON_ID)).thenReturn(Optional.of(DeviceSettingsService.defaultSettings()));
        when(settingsRepo.save(any(DeviceSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        luna = Pet.builder().id(UUID.randomUUID()).ownerId(UUID.randomUUID()).name("Luna").breed("Mèo ta")
                .age(2).gender(Gender.FEMALE).neutered(true).weight(4.0).build();
        when(petRepo.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(luna));
        when(feedingRepo.findFirstByOrderByTimestampDesc()).thenReturn(Optional.empty());

        litterRepo = mock(LitterLogRepository.class);
        when(litterRepo.findFirstByOrderByTimestampDesc()).thenReturn(Optional.empty());

        // Lắp các service giống Spring (không cần khởi động context)
        ObjectMapper mapper = new ObjectMapper();
        DeviceAlerts alerts = new DeviceAlerts(alertRepo);
        settingsService = new DeviceSettingsService(settingsRepo, petRepo);
        CatIdentityTracker catIdentity = new CatIdentityTracker(petRepo);
        feeder = new FeederService(settingsService, feedingRepo, catIdentity, alerts, publisher, mapper);
        WaterMonitorService water = new WaterMonitorService(alerts, mapper);
        LitterService litter = new LitterService(settingsService, litterRepo, petRepo, water, alerts, mapper);
        VisionService vision = new VisionService(feeder, catIdentity, petRepo, alerts, mapper);
        service = new DeviceService(feeder, water, litter, vision, settingsService, petRepo);
    }

    /** Giả lập gói tin MQTT đi qua đúng đường như khi chạy thật. */
    private void send(String channel, String payload) {
        service.onDeviceMessage(new DeviceMessageEvent(channel, payload));
    }

    private void telemetry(double weight, boolean feeding) throws Exception {
        send("telemetry","{\"weight\":" + weight + ",\"foodLevelPercent\":70,\"lowFood\":false,"
                + "\"petPresent\":false,\"autoMode\":false,\"isFeeding\":" + feeding + ",\"ip\":\"192.168.1.5\"}");
    }

    private void water(int percent, boolean low) throws Exception {
        send("water","{\"waterPercent\":" + percent + ",\"waterCm\":3.0,\"lowWater\":" + low
                + ",\"sensorOk\":" + (percent >= 0) + ",\"ip\":\"192.168.1.6\"}");
    }

    private List<String> alertMessages() {
        var captor = org.mockito.ArgumentCaptor.forClass(Alert.class);
        verify(alertRepo, atLeast(0)).save(captor.capture());
        return captor.getAllValues().stream().map(Alert::getMessage).toList();
    }

    @Test
    @DisplayName("Máy offline -> cho ăn bị từ chối với thông báo rõ ràng")
    void feedWhenOfflineFails() {
        assertThatThrownBy(() -> service.feed(30, "Web", ADMIN)).isInstanceOf(DeviceUnavailableException.class);
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    @DisplayName("Khi máy kết nối, backend đồng bộ cài đặt xuống ESP32")
    void syncSettingsWhenOnline() throws Exception {
        telemetry(3, false);
        assertThat(service.isOnline()).isTrue();
        assertThat(publisher.actions()).containsExactly("SET_TARGET", "AUTO");
    }

    @Test
    @DisplayName("Mèo đến gần không còn bật bơm; chỉ cho ăn khi bật Tự động và khay gần hết")
    void visionNeverSendsPump() throws Exception {
        telemetry(3, false);
        service.updateSettings(UpdateDeviceSettingsRequest.builder().autoMode(true).build(), ADMIN);
        publisher.sent.clear();

        send("vision","{\"catPresent\":true,\"confidence\":0.9}");

        assertThat(publisher.actions()).containsExactly("FEED");
        assertThat(publisher.sent.get(0)).containsEntry("source", "Camera AI");
    }

    @Test
    @DisplayName("Camera nhận ra bé -> nhả đúng khẩu phần riêng của bé và ghi nhật ký cho bé đó")
    void visionFeedsPetPortion() throws Exception {
        Pet mimi = Pet.builder().id(UUID.randomUUID()).ownerId(UUID.randomUUID()).name("Mimi").weight(3.5).portionGram(45).build();
        when(petRepo.findFirstByNameIgnoreCase("Mimi")).thenReturn(Optional.of(mimi));
        when(petRepo.findById(mimi.getId())).thenReturn(Optional.of(mimi));
        telemetry(3, false);
        service.updateSettings(UpdateDeviceSettingsRequest.builder().autoMode(true).build(), ADMIN);
        publisher.sent.clear();

        send("vision", "{\"catPresent\":true,\"petName\":\"Mimi\",\"confidence\":0.9}");

        assertThat(publisher.sent).hasSize(1);
        assertThat(publisher.sent.get(0)).containsEntry("action", "FEED").containsEntry("gram", 45)
                .containsEntry("source", "Camera: Mimi");

        send("log", "{\"source\":\"Camera: Mimi\",\"addedGram\":44,\"result\":\"DONE\"}");
        verify(feedingRepo).save(argThat((FeedingLog f) -> f.getPetId().equals(mimi.getId()) && f.getFoodGram() == 44));
    }

    @Test
    @DisplayName("Bé chưa đặt khẩu phần riêng -> dùng khẩu phần chung")
    void visionFallsBackToCommonPortion() throws Exception {
        Pet tom = Pet.builder().id(UUID.randomUUID()).ownerId(UUID.randomUUID()).name("Tom").weight(4.0).build();
        when(petRepo.findFirstByNameIgnoreCase("Tom")).thenReturn(Optional.of(tom));
        telemetry(3, false);
        service.updateSettings(UpdateDeviceSettingsRequest.builder().autoMode(true).targetGram(35).build(), ADMIN);
        publisher.sent.clear();

        send("vision", "{\"catPresent\":true,\"petName\":\"Tom\",\"confidence\":0.9}");

        assertThat(publisher.sent.get(0)).containsEntry("gram", 35).containsEntry("source", "Camera: Tom");
    }

    @Test
    @DisplayName("Camera nhận ra mèo lạ -> không nhả hạt, báo 1 lần")
    void strangerCatIsNotFed() throws Exception {
        telemetry(3, false);
        service.updateSettings(UpdateDeviceSettingsRequest.builder().autoMode(true).build(), ADMIN);
        publisher.sent.clear();

        send("vision", "{\"catPresent\":true,\"petName\":\"Khac\",\"confidence\":0.9}");
        send("vision", "{\"catPresent\":true,\"petName\":\"Khac\",\"confidence\":0.9}");

        assertThat(publisher.actions()).doesNotContain("FEED");
        assertThat(alertMessages().stream().filter(m -> m.contains("mèo lạ"))).hasSize(1);
        assertThat(service.getState().getLastDetectedPetName()).isNull();
    }

    @Test
    @DisplayName("AI camera đang chạy -> báo ESP32 không tự nhả theo cảm biến; AI tắt -> bật lại")
    void cameraTogglesSensorFeed() throws Exception {
        telemetry(3, false);
        assertThat(publisher.sent.stream().filter(c -> "AUTO".equals(c.get("action"))).findFirst().orElseThrow())
                .containsEntry("sensor", true);
        publisher.sent.clear();

        // Chỉ nhận hình (AI tắt trên Pi) -> vẫn để ESP32 tự nhả theo cảm biến
        service.markCameraSeen();
        feeder.syncSensorFeed();
        assertThat(publisher.sent).isEmpty();

        // AI gửi sự kiện (heartbeat lúc không có mèo) -> tắt tự nhả theo cảm biến
        send("vision", "{\"catPresent\":false,\"petName\":\"\",\"confidence\":0}");
        feeder.syncSensorFeed();
        assertThat(publisher.sent).hasSize(1);
        assertThat(publisher.sent.get(0)).containsEntry("action", "AUTO").containsEntry("sensor", false);

        feeder.syncSensorFeed(); // không đổi -> không gửi lại
        assertThat(publisher.sent).hasSize(1);

        java.lang.reflect.Field f = FeederService.class.getDeclaredField("cameraSeenAt");
        f.setAccessible(true);
        f.set(feeder, java.time.Instant.now().minusSeconds(120)); // camera im lặng 2 phút
        feeder.syncSensorFeed();
        assertThat(publisher.sent.get(1)).containsEntry("action", "AUTO").containsEntry("sensor", true);
    }

    @Test
    @DisplayName("Mực nước: hiện trên trạng thái, báo 1 lần khi sắp cạn và báo khi đã đổ thêm")
    void lowWaterAlertsOnceAndOnRefill() throws Exception {
        assertThat(service.getState().isWaterOnline()).isFalse();

        water(60, false);
        var st = service.getState();
        assertThat(st.isWaterOnline()).isTrue();
        assertThat(st.getWaterPercent()).isEqualTo(60);
        assertThat(st.isLowWater()).isFalse();

        water(15, true);
        water(14, true);   // vẫn cạn -> không nhắc lại
        assertThat(service.getState().isLowWater()).isTrue();
        assertThat(alertMessages().stream().filter(m -> m.startsWith("Bình nước sắp cạn"))).hasSize(1);

        water(80, false);
        assertThat(alertMessages()).anyMatch(m -> m.startsWith("Đã đổ thêm nước"));
        assertThat(publisher.sent).isEmpty();   // chỉ theo dõi, không gửi lệnh bơm nào
    }

    @Test
    @DisplayName("Mèo đi vệ sinh -> ghi litter log cho bé của máy, đúng thời gian và thời lượng")
    void litterVisitIsLogged() throws Exception {
        send("litter","{\"event\":\"visit\",\"durationSec\":95,\"agoSec\":120}");

        var captor = org.mockito.ArgumentCaptor.forClass(LitterLog.class);
        verify(litterRepo).save(captor.capture());
        LitterLog saved = captor.getValue();
        assertThat(saved.getPetId()).isEqualTo(luna.getId());
        assertThat(saved.getDurationSec()).isEqualTo(95);
        assertThat(saved.getResult()).isEqualTo(LitterResult.NORMAL);
        assertThat(saved.getTimestamp()).isBetween(java.time.Instant.now().minusSeconds(125),
                java.time.Instant.now().minusSeconds(115));
        assertThat(alertMessages()).anyMatch(m -> m.equals("Luna vừa đi vệ sinh (1 phút 35 giây trong khay)"));

        var st = service.getState();
        assertThat(st.getLastLitterVisitSec()).isEqualTo(95);
        assertThat(st.getLastLitterVisitPetName()).isEqualTo("Luna");
        assertThat(st.isWaterOnline()).isTrue();
    }

    @Test
    @DisplayName("Dọn cát xong -> lưu thời điểm dọn; sự kiện quá cũ bị bỏ qua")
    void litterCleanedAndStaleEvents() throws Exception {
        send("litter","{\"event\":\"cleaned\",\"durationSec\":0,\"agoSec\":0}");
        assertThat(service.getState().getLastLitterCleanAt()).isNotNull();

        send("litter","{\"event\":\"visit\",\"durationSec\":30,\"agoSec\":999999999}");
        verify(litterRepo, never()).save(any());
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    @DisplayName("Trạng thái khay lấy từ gói mực nước, chỉ khi máy online")
    void litterStateFromHeartbeat() throws Exception {
        send("water","{\"waterPercent\":50,\"waterCm\":4.0,\"lowWater\":false,\"sensorOk\":true,"
                + "\"litterState\":\"cat_inside\",\"ip\":\"192.168.1.6\"}");
        assertThat(service.getState().getLitterState()).isEqualTo("cat_inside");
        assertThat(LitterService.formatDuration(45)).isEqualTo("45 giây");
        assertThat(LitterService.formatDuration(120)).isEqualTo("2 phút");
    }

    @Test
    @DisplayName("Cảm biến nước lỗi -> không có % nhưng vẫn online")
    void waterSensorError() throws Exception {
        water(-1, false);
        var st = service.getState();
        assertThat(st.isWaterOnline()).isTrue();
        assertThat(st.isWaterSensorOk()).isFalse();
        assertThat(st.getWaterPercent()).isNull();
    }

    @Test
    @DisplayName("Cho ăn gửi lệnh FEED nhưng KHÔNG tự ghi log (chỉ ghi khi ESP32 báo)")
    void feedSendsCommandOnly() throws Exception {
        telemetry(3, false);
        publisher.sent.clear();

        service.feed(25, "web", ADMIN);

        assertThat(publisher.sent).hasSize(1);
        assertThat(publisher.sent.get(0)).containsEntry("action", "FEED").containsEntry("gram", 25).containsEntry("source", "Web");
        verify(feedingRepo, never()).save(any());
    }

    @Test
    @DisplayName("Đang nhả hạt thì từ chối lệnh cho ăn mới")
    void rejectWhileFeeding() throws Exception {
        telemetry(3, true);
        assertThatThrownBy(() -> service.feed(25, "App", ADMIN)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Log từ ESP32 được lưu đúng 1 lần với số gram thật")
    void feedingLogSavedOnce() throws Exception {
        send("log","{\"source\":\"Web\",\"addedGram\":27.6,\"result\":\"DONE\"}");

        verify(feedingRepo, times(1)).save(argThat((FeedingLog f) -> f.getFoodGram() == 28 && f.getPetId().equals(luna.getId())
                && "Web".equals(f.getSource())));
    }

    @Test
    @DisplayName("Camera nhận diện đúng bé mèo -> nhật ký cho ăn gán cho bé đó")
    void feedingLogAssignedToVisionIdentifiedPet() throws Exception {
        UUID mimiId = UUID.randomUUID();
        Pet mimi = Pet.builder().id(mimiId).name("Mimi").weight(3.5).build();
        when(petRepo.findFirstByNameIgnoreCase("Mimi")).thenReturn(Optional.of(mimi));
        when(petRepo.findById(mimiId)).thenReturn(Optional.of(mimi));

        // Camera gửi thông điệp nhận diện bé Mimi
        send("vision","{\"catPresent\":true,\"petName\":\"Mimi\",\"confidence\":0.88}");
        assertThat(service.getState().getLastDetectedPetName()).isEqualTo("Mimi");

        // ESP32 nhả hạt xong gửi log
        send("log","{\"source\":\"Camera: Mimi\",\"addedGram\":30.0,\"result\":\"DONE\"}");

        // Nhật ký phải được lưu với mimiId chứ không phải lunaId mặc định
        verify(feedingRepo, times(1)).save(argThat((FeedingLog f) -> f.getPetId().equals(mimiId)
                && f.getFoodGram() == 30));
    }

    @Test
    @DisplayName("Động cơ chạy mà cân không tăng -> cảnh báo, không ghi log")
    void emptyHopperWarns() throws Exception {
        send("log","{\"source\":\"Web\",\"addedGram\":0.2,\"result\":\"DONE\"}");

        verify(feedingRepo, never()).save(any());
        verify(alertRepo).save(argThat((Alert a) -> a.getLevel() == AlertLevel.WARNING));
    }

    @Test
    @DisplayName("Lịch hẹn giờ: khay còn nhiều hạt thì bỏ qua, khay trống thì cho ăn")
    void scheduleRespectsBowlWeight() throws Exception {
        service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .scheduleEnabled(true).scheduleTimes(List.of("7:00")).build(), ADMIN);
        LocalDateTime seven = LocalDateTime.of(2026, 10, 27, 7, 0);

        telemetry(25, false);
        publisher.sent.clear();
        feeder.runSchedule(seven);
        assertThat(publisher.actions()).doesNotContain("FEED");

        telemetry(4, false);
        feeder.runSchedule(seven.plusDays(1));
        assertThat(publisher.actions()).contains("FEED");
    }

    @Test
    @DisplayName("Giờ sai định dạng bị từ chối")
    void invalidTimeRejected() {
        assertThatThrownBy(() -> service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .scheduleTimes(List.of("25:00")).build(), ADMIN)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Giờ sai định dạng không làm thay đổi các cài đặt khác trong cùng yêu cầu")
    void invalidTimeLeavesSettingsUntouched() {
        int before = settingsService.get().getTargetGram();
        assertThatThrownBy(() -> service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .targetGram(before + 20).scheduleTimes(List.of("25:00")).build(), ADMIN))
                .isInstanceOf(BadRequestException.class);
        assertThat(settingsService.get().getTargetGram()).isEqualTo(before);
    }

    @Test
    @DisplayName("Người dùng thường không được gán mèo của người khác cho máy cho ăn")
    void userCannotAssignStrangersPet() {
        when(petRepo.findById(luna.getId())).thenReturn(Optional.of(luna));
        Actor stranger = new Actor(UUID.randomUUID(), false);

        assertThatThrownBy(() -> service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .feederPetId(luna.getId()).autoMode(true).build(), stranger))
                .isInstanceOf(AccessDeniedException.class);
        assertThat(settingsService.get().getFeederPetId()).isNull();
        assertThat(settingsService.get().getAutoMode()).isFalse();
    }

    @Test
    @DisplayName("Chủ mèo và admin được gán mèo cho máy cho ăn")
    void ownerAndAdminCanAssignPet() {
        when(petRepo.findById(luna.getId())).thenReturn(Optional.of(luna));

        service.updateSettings(UpdateDeviceSettingsRequest.builder().feederPetId(luna.getId()).build(),
                new Actor(luna.getOwnerId(), false));
        assertThat(settingsService.get().getFeederPetId()).isEqualTo(luna.getId());

        service.updateSettings(UpdateDeviceSettingsRequest.builder().feederPetId(luna.getId()).build(), ADMIN);
        assertThat(settingsService.get().getFeederPetId()).isEqualTo(luna.getId());
    }

    @Test
    @DisplayName("Gán mèo không tồn tại -> 400")
    void assignMissingPetRejected() {
        UUID missing = UUID.randomUUID();
        when(petRepo.findById(missing)).thenReturn(Optional.empty());
        assertThatThrownBy(() -> service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .feederPetId(missing).build(), ADMIN)).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Bé do camera nhận diện chỉ dùng cho 1 lần nhả hạt, lần sau về bé mặc định của máy")
    void visionIdentityUsedOnce() throws Exception {
        UUID mimiId = UUID.randomUUID();
        Pet mimi = Pet.builder().id(mimiId).name("Mimi").weight(3.5).build();
        when(petRepo.findFirstByNameIgnoreCase("Mimi")).thenReturn(Optional.of(mimi));
        when(petRepo.findById(mimiId)).thenReturn(Optional.of(mimi));

        send("vision", "{\"catPresent\":false,\"petName\":\"Mimi\",\"confidence\":0.9}");
        send("log", "{\"source\":\"Web\",\"addedGram\":20,\"result\":\"DONE\"}");
        send("log", "{\"source\":\"Web\",\"addedGram\":21,\"result\":\"DONE\"}");

        verify(feedingRepo).save(argThat((FeedingLog f) -> f.getFoodGram() == 20 && f.getPetId().equals(mimiId)));
        verify(feedingRepo).save(argThat((FeedingLog f) -> f.getFoodGram() == 21 && f.getPetId().equals(luna.getId())));
    }

    @Test
    @DisplayName("Nút AUTO vật lý trên máy được đồng bộ ngược về cài đặt")
    void physicalAutoButtonSyncsBack() throws Exception {
        telemetry(3, false);
        // đã qua thời gian chờ đồng bộ -> autoMode trong telemetry là nguồn sự thật
        java.lang.reflect.Field f = FeederService.class.getDeclaredField("pendingSyncUntil");
        f.setAccessible(true);
        f.set(feeder, java.time.Instant.EPOCH);

        send("telemetry", "{\"weight\":3,\"autoMode\":true,\"isFeeding\":false}");
        assertThat(settingsService.get().getAutoMode()).isTrue();
    }

    @Test
    @DisplayName("Người dùng khác không thấy lần ăn, lần đi vệ sinh và bé của máy thuộc tài khoản khác")
    void stateHidesOtherUsersPetData() throws Exception {
        when(petRepo.findById(luna.getId())).thenReturn(Optional.of(luna));
        send("log", "{\"source\":\"Web\",\"addedGram\":30,\"result\":\"DONE\"}");
        send("litter", "{\"event\":\"visit\",\"durationSec\":60,\"agoSec\":0}");

        Actor stranger = new Actor(UUID.randomUUID(), false);
        when(petRepo.findByOwnerIdOrderByCreatedAtAsc(stranger.id())).thenReturn(List.of());
        var forStranger = service.getState(stranger);
        assertThat(forStranger.getLastFeedGram()).isNull();
        assertThat(forStranger.getLastLitterVisitPetName()).isNull();
        assertThat(forStranger.getSettings().getFeederPetName()).isNull();
        assertThat(forStranger.getSettings().isFeederPetOfOtherUser()).isTrue();

        Actor owner = new Actor(luna.getOwnerId(), false);
        when(petRepo.findByOwnerIdOrderByCreatedAtAsc(owner.id())).thenReturn(List.of(luna));
        var forOwner = service.getState(owner);
        assertThat(forOwner.getLastFeedGram()).isEqualTo(30);
        assertThat(forOwner.getLastLitterVisitPetName()).isEqualTo("Luna");
        assertThat(forOwner.getSettings().getFeederPetName()).isEqualTo("Luna");
        assertThat(forOwner.getSettings().isFeederPetOfOtherUser()).isFalse();

        // Cảnh báo về bé được gắn petId để lọc theo người xem
        verify(alertRepo, atLeastOnce()).save(argThat((Alert a) -> luna.getId().equals(a.getPetId())));
    }

    @Test
    @DisplayName("ESP32 báo offline qua Last Will -> trạng thái offline + cảnh báo")
    void lastWillMarksOffline() throws Exception {
        telemetry(3, false);
        send("status","offline");
        assertThat(service.isOnline()).isFalse();
        verify(alertRepo, atLeastOnce()).save(argThat((Alert a) -> a.getMessage().contains("mất kết nối")));
    }
}
