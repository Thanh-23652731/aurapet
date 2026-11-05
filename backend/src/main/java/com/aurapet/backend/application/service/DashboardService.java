package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.FeedingLog;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.AlertRepository;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.*;

@Service
@RequiredArgsConstructor
public class DashboardService {

    private final PetService petService;
    private final HealthAnalyticsService healthAnalyticsService;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;
    private final AlertRepository alertRepository;
    private final DeviceService deviceService;

    @Transactional(readOnly = true)
    public DashboardStatsDto getDashboardStats(Actor actor) {
        List<Pet> pets = petService.accessiblePets(actor);
        List<UUID> petIds = pets.stream().map(Pet::getId).toList();
        Instant startOfToday = AppTime.startOfToday();

        int todayFood = 0;
        int sessions = 0;
        long litterCount = 0;
        if (!petIds.isEmpty()) {
            List<FeedingLog> today = feedingLogRepository.findByPetIdInAndTimestampAfterOrderByTimestampAsc(petIds, startOfToday);
            todayFood = today.stream().mapToInt(FeedingLog::getFoodGram).sum();
            sessions = today.size();
            litterCount = litterLogRepository.countByPetIdInAndTimestampAfter(petIds, startOfToday);
        }

        List<AlertDto> alerts = alertRepository.findTop15ByOrderByCreatedAtDesc().stream()
                .map(a -> AlertDto.builder()
                        .id(a.getId())
                        .type(a.getType())
                        .level(a.getLevel())
                        .message(a.getMessage())
                        .createdAt(a.getCreatedAt())
                        .build())
                .toList();

        return DashboardStatsDto.builder()
                .totalPets(pets.size())
                .todayFoodGram(todayFood)
                .feedingSessionsCount(sessions)
                .litterUsageCount((int) litterCount)
                .last7Days(healthAnalyticsService.dailyTotals(petIds, 7))
                .latestAlerts(alerts)
                .device(deviceService.getState())
                .build();
    }

    /** Lịch sử cho ăn gần đây của các mèo người dùng được xem (tối đa 100 dòng). */
    @Transactional(readOnly = true)
    public List<FeedingLogDto> recentFeedings(Actor actor, int limit) {
        List<Pet> pets = petService.accessiblePets(actor);
        if (pets.isEmpty()) return List.of();
        Map<UUID, String> names = new HashMap<>();
        pets.forEach(p -> names.put(p.getId(), p.getName()));
        return feedingLogRepository.findTop100ByPetIdInOrderByTimestampDesc(names.keySet()).stream()
                .limit(Math.max(1, Math.min(limit, 100)))
                .map(f -> FeedingLogDto.builder()
                        .id(f.getId())
                        .petId(f.getPetId())
                        .petName(names.getOrDefault(f.getPetId(), "?"))
                        .foodGram(f.getFoodGram())
                        .source(f.getSource())
                        .timestamp(f.getTimestamp())
                        .build())
                .toList();
    }
}
