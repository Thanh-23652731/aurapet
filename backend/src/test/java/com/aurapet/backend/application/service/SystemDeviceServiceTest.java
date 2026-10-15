package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.FeederCommandRequest;
import com.aurapet.backend.application.dto.LitterCommandRequest;
import com.aurapet.backend.application.dto.SystemStatusDto;
import com.aurapet.backend.application.dto.WaterCommandRequest;
import com.aurapet.backend.domain.model.SystemStatus;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.domain.repository.SystemStatusRepository;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.*;

@ExtendWith(MockitoExtension.class)
class SystemDeviceServiceTest {

    @Mock
    private SystemStatusRepository systemStatusRepository;

    @Mock
    private FeedingLogRepository feedingLogRepository;

    @Mock
    private AlertRepository alertRepository;

    @Mock
    private PetRepository petRepository;

    @InjectMocks
    private SystemDeviceService systemDeviceService;

    private SystemStatus initialStatus;

    @BeforeEach
    void setUp() {
        initialStatus = SystemStatus.builder()
                .id(UUID.randomUUID())
                .feeder(true)
                .waterPump(true)
                .litterCleaner(false)
                .esp32("ONLINE")
                .raspberryPi("ONLINE")
                .foodLevelPercent(80)
                .waterLevelPercent(90)
                .litterWastePercent(20)
                .build();
    }

    @Test
    @DisplayName("Should control feeder and log telemetry")
    void testControlFeeder() {
        when(systemStatusRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(initialStatus));
        when(systemStatusRepository.save(any(SystemStatus.class))).thenReturn(initialStatus);

        FeederCommandRequest request = FeederCommandRequest.builder()
                .portionGram(40)
                .build();

        SystemStatusDto status = systemDeviceService.controlFeeder(request);

        assertThat(status).isNotNull();
        assertThat(status.getFeeder()).isTrue();
        verify(systemStatusRepository, times(1)).save(any(SystemStatus.class));
    }

    @Test
    @DisplayName("Should control water pump")
    void testControlWater() {
        when(systemStatusRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(initialStatus));
        when(systemStatusRepository.save(any(SystemStatus.class))).thenReturn(initialStatus);

        WaterCommandRequest request = WaterCommandRequest.builder()
                .enabled(false)
                .build();

        SystemStatusDto status = systemDeviceService.controlWater(request);

        assertThat(status).isNotNull();
        assertThat(status.getWaterPump()).isFalse();
    }

    @Test
    @DisplayName("Should trigger litter cleaning cycle and reset waste percent")
    void testControlLitterClean() {
        when(systemStatusRepository.findFirstByOrderByLastUpdatedDesc()).thenReturn(Optional.of(initialStatus));
        when(systemStatusRepository.save(any(SystemStatus.class))).thenReturn(initialStatus);

        LitterCommandRequest request = LitterCommandRequest.builder()
                .action("CLEAN")
                .build();

        SystemStatusDto status = systemDeviceService.controlLitter(request);

        assertThat(status).isNotNull();
        assertThat(status.getLitterCleaner()).isTrue();
        assertThat(status.getLitterWastePercent()).isEqualTo(0);
    }
}
