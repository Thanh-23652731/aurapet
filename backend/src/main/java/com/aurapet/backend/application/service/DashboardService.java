package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.AlertDto;
import com.aurapet.backend.application.dto.DashboardStatsDto;
import com.aurapet.backend.application.dto.SystemStatusDto;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PetRepository petRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;
    private final AlertRepository alertRepository;
    private final SystemDeviceService systemDeviceService;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats() {
        long totalPets = petRepository.count();
        Instant startOfToday = Instant.now().truncatedTo(ChronoUnit.DAYS);

        Integer todayFood = feedingLogRepository.sumFoodGramSince(startOfToday);
        Integer feedingSessions = feedingLogRepository.countSessionsSince(startOfToday);
        Integer litterUsage = litterLogRepository.countLogsSince(startOfToday);

        List<AlertDto> latestAlerts = alertRepository.findTop10ByOrderByCreatedAtDesc().stream()
                .map(a -> AlertDto.builder()
                        .id(a.getId())
                        .type(a.getType())
                        .level(a.getLevel())
                        .message(a.getMessage())
                        .createdAt(a.getCreatedAt())
                        .build())
                .collect(Collectors.toList());

        SystemStatusDto statusDto = systemDeviceService.getSystemStatus();

        return DashboardStatsDto.builder()
                .totalPets((int) totalPets)
                .todayFoodGram(todayFood != null ? todayFood : 0)
                .feedingSessionsCount(feedingSessions != null ? feedingSessions : 0)
                .litterUsageCount(litterUsage != null ? litterUsage : 0)
                .latestAlerts(latestAlerts)
                .deviceStatus(statusDto)
                .build();
    }
}
