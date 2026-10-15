package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.FeedingLog;
import com.aurapet.backend.domain.model.LitterLog;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.model.WeightRecord;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.domain.repository.WeightRecordRepository;
import com.aurapet.backend.interfaces.web.exception.ResourceNotFoundException;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.temporal.ChronoUnit;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

@Service
@RequiredArgsConstructor
public class HealthAnalyticsService {

    private final PetRepository petRepository;
    private final WeightRecordRepository weightRecordRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;

    @Transactional(readOnly = true)
    public HealthSummaryDto getHealthSummary(UUID petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with ID: " + petId));

        Instant sevenDaysAgo = Instant.now().minus(7, ChronoUnit.DAYS);

        // Daily average food intake over last 7 days
        List<FeedingLog> recentFeedings = feedingLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(
                petId, sevenDaysAgo, Instant.now());
        int totalFood7Days = recentFeedings.stream().mapToInt(FeedingLog::getFoodGram).sum();
        double dailyAvgFood = recentFeedings.isEmpty() ? 0.0 : Math.round((totalFood7Days / 7.0) * 10.0) / 10.0;

        // Weekly litter visits
        List<LitterLog> recentLitter = litterLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(
                petId, sevenDaysAgo, Instant.now());
        int weeklyLitterVisits = recentLitter.size();

        // Weight delta
        List<WeightRecord> weightHistory = weightRecordRepository.findByPetIdOrderByRecordedAtAsc(petId);
        double weightChange = 0.0;
        if (weightHistory.size() >= 2) {
            double latest = weightHistory.get(weightHistory.size() - 1).getWeight();
            double previous = weightHistory.get(weightHistory.size() - 2).getWeight();
            weightChange = Math.round((latest - previous) * 100.0) / 100.0;
        }

        // Edge AI Health Scoring Algorithm
        int baseScore = 95;
        if (weeklyLitterVisits < 7 || weeklyLitterVisits > 28) {
            baseScore -= 10;
        }
        if (Math.abs(weightChange) > 0.4) {
            baseScore -= 8;
        }
        int healthScore = Math.max(70, Math.min(100, baseScore));

        String status = healthScore >= 90 ? "Optimal" : healthScore >= 80 ? "Good" : "Attention Needed";
        String recommendation = String.format(
                "%s displays strong physiological stability. Dietary intake averages %.1fg/day with consistent digestion. Continue current portioning schedule.",
                pet.getName(), dailyAvgFood
        );

        return HealthSummaryDto.builder()
                .petId(pet.getId())
                .petName(pet.getName())
                .healthScore(healthScore)
                .status(status)
                .dailyAvgFoodGram(dailyAvgFood)
                .weeklyLitterVisits(weeklyLitterVisits)
                .weightChangeKg(weightChange)
                .aiRecommendation(recommendation)
                .build();
    }

    @Transactional(readOnly = true)
    public List<WeightRecordDto> getWeightHistory(UUID petId) {
        if (!petRepository.existsById(petId)) {
            throw new ResourceNotFoundException("Pet not found with ID: " + petId);
        }
        return weightRecordRepository.findByPetIdOrderByRecordedAtAsc(petId).stream()
                .map(w -> WeightRecordDto.builder()
                        .id(w.getId())
                        .petId(w.getPetId())
                        .weight(w.getWeight())
                        .recordedAt(w.getRecordedAt())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<FeedingLogDto> getFoodHistory(UUID petId) {
        Pet pet = petRepository.findById(petId)
                .orElseThrow(() -> new ResourceNotFoundException("Pet not found with ID: " + petId));
        return feedingLogRepository.findByPetIdOrderByTimestampDesc(petId).stream()
                .map(f -> FeedingLogDto.builder()
                        .id(f.getId())
                        .petId(f.getPetId())
                        .petName(pet.getName())
                        .foodGram(f.getFoodGram())
                        .timestamp(f.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }

    @Transactional(readOnly = true)
    public List<LitterLogDto> getLitterHistory(UUID petId) {
        if (!petRepository.existsById(petId)) {
            throw new ResourceNotFoundException("Pet not found with ID: " + petId);
        }
        return litterLogRepository.findByPetIdOrderByTimestampDesc(petId).stream()
                .map(l -> LitterLogDto.builder()
                        .id(l.getId())
                        .petId(l.getPetId())
                        .result(l.getResult())
                        .imageUrl(l.getImageUrl())
                        .timestamp(l.getTimestamp())
                        .build())
                .collect(Collectors.toList());
    }
}
