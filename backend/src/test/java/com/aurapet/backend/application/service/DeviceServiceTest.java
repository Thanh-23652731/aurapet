package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.UpdateDeviceSettingsRequest;
import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.DeviceSettingsRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.interfaces.web.exception.BadRequestException;
import com.aurapet.backend.interfaces.web.exception.DeviceUnavailableException;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;
import java.util.*;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

class DeviceServiceTest {

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
    private FakePublisher publisher;
    private DeviceService service;
    private Pet luna;

    @BeforeEach
    void setUp() {
        settingsRepo = mock(DeviceSettingsRepository.class);
        feedingRepo = mock(FeedingLogRepository.class);
        petRepo = mock(PetRepository.class);
        alertRepo = mock(AlertRepository.class);
        publisher = new FakePublisher();

        when(settingsRepo.findById(DeviceSettings.SINGLETON_ID)).thenReturn(Optional.of(DeviceService.defaultSettings()));
        when(settingsRepo.save(any(DeviceSettings.class))).thenAnswer(inv -> inv.getArgument(0));
        luna = Pet.builder().id(UUID.randomUUID()).ownerId(UUID.randomUUID()).name("Luna").breed("Mèo ta")
                .age(2).gender(Gender.FEMALE).neutered(true).weight(4.0).build();
        when(petRepo.findAllByOrderByCreatedAtAsc()).thenReturn(List.of(luna));
        when(feedingRepo.findFirstByOrderByTimestampDesc()).thenReturn(Optional.empty());

        service = new DeviceService(settingsRepo, feedingRepo, petRepo, alertRepo, publisher, new ObjectMapper());
    }

    private void telemetry(double weight, boolean feeding) throws Exception {
        service.handleTelemetry("{\"weight\":" + weight + ",\"foodLevelPercent\":70,\"lowFood\":false,"
                + "\"petPresent\":false,\"autoMode\":false,\"isFeeding\":" + feeding + ",\"ip\":\"192.168.1.5\","
                + "\"pumpSupported\":false}");
    }

    @Test
    @DisplayName("Máy offline -> cho ăn bị từ chối với thông báo rõ ràng")
    void feedWhenOfflineFails() {
        assertThatThrownBy(() -> service.feed(30, "Web")).isInstanceOf(DeviceUnavailableException.class);
        assertThat(publisher.sent).isEmpty();
    }

    @Test
    @DisplayName("Khi máy kết nối, backend đồng bộ cài đặt xuống ESP32")
    void syncSettingsWhenOnline() throws Exception {
        telemetry(3, false);
        assertThat(service.isOnline()).isTrue();
        assertThat(publisher.actions()).containsExactly("SET_TARGET", "AUTO", "PUMP_AUTO");
    }

    @Test
    @DisplayName("Cho ăn gửi lệnh FEED nhưng KHÔNG tự ghi log (chỉ ghi khi ESP32 báo)")
    void feedSendsCommandOnly() throws Exception {
        telemetry(3, false);
        publisher.sent.clear();

        service.feed(25, "web");

        assertThat(publisher.sent).hasSize(1);
        assertThat(publisher.sent.get(0)).containsEntry("action", "FEED").containsEntry("gram", 25).containsEntry("source", "Web");
        verify(feedingRepo, never()).save(any());
    }

    @Test
    @DisplayName("Đang nhả hạt thì từ chối lệnh cho ăn mới")
    void rejectWhileFeeding() throws Exception {
        telemetry(3, true);
        assertThatThrownBy(() -> service.feed(25, "App")).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("Log từ ESP32 được lưu đúng 1 lần với số gram thật")
    void feedingLogSavedOnce() throws Exception {
        service.handleFeedingLog("{\"source\":\"Web\",\"addedGram\":27.6,\"result\":\"DONE\"}");

        verify(feedingRepo, times(1)).save(argThat((FeedingLog f) -> f.getFoodGram() == 28 && f.getPetId().equals(luna.getId())
                && "Web".equals(f.getSource())));
    }

    @Test
    @DisplayName("Động cơ chạy mà cân không tăng -> cảnh báo, không ghi log")
    void emptyHopperWarns() throws Exception {
        service.handleFeedingLog("{\"source\":\"Web\",\"addedGram\":0.2,\"result\":\"DONE\"}");

        verify(feedingRepo, never()).save(any());
        verify(alertRepo).save(argThat((Alert a) -> a.getLevel() == AlertLevel.WARNING));
    }

    @Test
    @DisplayName("Lịch hẹn giờ: khay còn nhiều hạt thì bỏ qua, khay trống thì cho ăn")
    void scheduleRespectsBowlWeight() throws Exception {
        service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .scheduleEnabled(true).scheduleTimes(List.of("7:00")).build());
        LocalDateTime seven = LocalDateTime.of(2026, 10, 27, 7, 0);

        telemetry(25, false);
        publisher.sent.clear();
        service.runSchedule(seven);
        assertThat(publisher.actions()).doesNotContain("FEED");

        telemetry(4, false);
        service.runSchedule(seven.plusDays(1));
        assertThat(publisher.actions()).contains("FEED");
    }

    @Test
    @DisplayName("Giờ sai định dạng bị từ chối")
    void invalidTimeRejected() {
        assertThatThrownBy(() -> service.updateSettings(UpdateDeviceSettingsRequest.builder()
                .scheduleTimes(List.of("25:00")).build())).isInstanceOf(BadRequestException.class);
    }

    @Test
    @DisplayName("ESP32 báo offline qua Last Will -> trạng thái offline + cảnh báo")
    void lastWillMarksOffline() throws Exception {
        telemetry(3, false);
        service.handleStatus("offline");
        assertThat(service.isOnline()).isFalse();
        verify(alertRepo, atLeastOnce()).save(argThat((Alert a) -> a.getMessage().contains("mất kết nối")));
    }
}
