package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.*;
import com.aurapet.backend.domain.model.*;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.WeightRecordRepository;
import lombok.RequiredArgsConstructor;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.time.LocalDate;
import java.util.*;

/**
 * Đánh giá sức khoẻ theo luật (rule-based) từ dữ liệu thật:
 * lượng ăn 7 ngày, số lần đi vệ sinh, biến động cân nặng và kết quả soi phân.
 */
@Service
@RequiredArgsConstructor
public class HealthAnalyticsService {

    /** Mèo trưởng thành ăn khoảng 15g hạt khô / 1kg cân nặng / ngày. */
    private static final double GRAM_PER_KG_PER_DAY = 15.0;

    private final PetService petService;
    private final WeightRecordRepository weightRecordRepository;
    private final FeedingLogRepository feedingLogRepository;
    private final LitterLogRepository litterLogRepository;

    @Transactional(readOnly = true)
    public HealthSummaryDto getHealthSummary(UUID petId, Actor actor) {
        Pet pet = petService.requireAccessible(petId, actor);
        Instant now = Instant.now();
        Instant sevenDaysAgo = AppTime.startOfDay(AppTime.today().minusDays(6));

        List<FeedingLog> feedings = feedingLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(petId, sevenDaysAgo, now);
        List<LitterLog> litter = litterLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(petId, sevenDaysAgo, now);
        List<WeightRecord> weights = weightRecordRepository.findByPetIdOrderByRecordedAtAsc(petId);

        int totalFood = feedings.stream().mapToInt(FeedingLog::getFoodGram).sum();
        double dailyAvg = feedings.isEmpty() ? 0.0 : Math.round(totalFood / 7.0 * 10.0) / 10.0;
        double weightKg = pet.getWeight() != null ? pet.getWeight() : 4.0;
        int expected = (int) Math.round(weightKg * GRAM_PER_KG_PER_DAY);

        double weightChange = 0.0;
        if (weights.size() >= 2) {
            double latest = weights.get(weights.size() - 1).getWeight();
            double previous = weights.get(weights.size() - 2).getWeight();
            weightChange = Math.round((latest - previous) * 100.0) / 100.0;
        }
        long abnormal = litter.stream().filter(l -> l.getResult() != LitterResult.NORMAL).count();

        int score = 100;
        List<String> notes = new ArrayList<>();

        if (feedings.isEmpty()) {
            score -= 15;
            notes.add("Chưa có dữ liệu cho ăn trong 7 ngày qua — hãy cho ăn bằng máy để hệ thống theo dõi.");
        } else if (dailyAvg < expected * 0.7) {
            score -= 15;
            notes.add(String.format("Ăn ít hơn nhu cầu: trung bình %.0fg/ngày, khuyến nghị khoảng %dg/ngày.", dailyAvg, expected));
        } else if (dailyAvg > expected * 1.3) {
            score -= 10;
            notes.add(String.format("Ăn nhiều hơn nhu cầu: trung bình %.0fg/ngày, khuyến nghị khoảng %dg/ngày.", dailyAvg, expected));
        } else {
            notes.add(String.format("Lượng ăn ổn định: trung bình %.0fg/ngày (khuyến nghị ~%dg).", dailyAvg, expected));
        }

        if (!litter.isEmpty() && (litter.size() < 7 || litter.size() > 28)) {
            score -= 10;
            notes.add("Số lần đi vệ sinh trong tuần (" + litter.size() + ") nằm ngoài mức bình thường 7–28 lần.");
        }
        if (abnormal > 0) {
            score -= (int) Math.min(25, abnormal * 8);
            notes.add("Có " + abnormal + " lần phân bất thường trong tuần — nên theo dõi và hỏi bác sĩ thú y nếu kéo dài.");
        }
        if (Math.abs(weightChange) > weightKg * 0.05) {
            score -= 12;
            notes.add(String.format("Cân nặng thay đổi %+.2f kg so với lần cân trước (>5%%).", weightChange));
        }

        score = Math.max(40, Math.min(100, score));
        String status = score >= 90 ? "Tốt" : score >= 75 ? "Cần theo dõi" : "Cần chú ý";
        String recommendation = pet.getName() + ": " + String.join(" ", notes);

        return HealthSummaryDto.builder()
                .petId(pet.getId())
                .petName(pet.getName())
                .healthScore(score)
                .status(status)
                .dailyAvgFoodGram(dailyAvg)
                .expectedDailyFoodGram(expected)
                .weeklyLitterVisits(litter.size())
                .weightChangeKg(weightChange)
                .aiRecommendation(recommendation)
                .notes(notes)
                .build();
    }

    @Transactional(readOnly = true)
    public List<WeightRecordDto> getWeightHistory(UUID petId, Actor actor) {
        petService.requireAccessible(petId, actor);
        return weightRecordRepository.findByPetIdOrderByRecordedAtAsc(petId).stream()
                .map(w -> WeightRecordDto.builder()
                        .id(w.getId())
                        .petId(w.getPetId())
                        .weight(w.getWeight())
                        .recordedAt(w.getRecordedAt())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<FeedingLogDto> getFoodHistory(UUID petId, Actor actor) {
        Pet pet = petService.requireAccessible(petId, actor);
        return feedingLogRepository.findByPetIdOrderByTimestampDesc(petId).stream()
                .limit(200)
                .map(f -> FeedingLogDto.builder()
                        .id(f.getId())
                        .petId(f.getPetId())
                        .petName(pet.getName())
                        .foodGram(f.getFoodGram())
                        .source(f.getSource())
                        .timestamp(f.getTimestamp())
                        .build())
                .toList();
    }

    @Transactional(readOnly = true)
    public List<DailyFoodDto> getDailyFood(UUID petId, int days, Actor actor) {
        petService.requireAccessible(petId, actor);
        return dailyTotals(List.of(petId), days);
    }

    @Transactional(readOnly = true)
    public List<LitterLogDto> getLitterHistory(UUID petId, Actor actor) {
        petService.requireAccessible(petId, actor);
        return litterLogRepository.findByPetIdOrderByTimestampDesc(petId).stream()
                .limit(200)
                .map(l -> LitterLogDto.builder()
                        .id(l.getId())
                        .petId(l.getPetId())
                        .result(l.getResult())
                        .imageUrl(l.getImageUrl())
                        .timestamp(l.getTimestamp())
                        .build())
                .toList();
    }

    /** Tổng lượng ăn theo từng ngày (giờ VN), đủ cả những ngày bằng 0. */
    public List<DailyFoodDto> dailyTotals(Collection<UUID> petIds, int days) {
        int n = Math.max(1, Math.min(days, 31));
        LocalDate first = AppTime.today().minusDays(n - 1L);
        Map<LocalDate, int[]> buckets = new LinkedHashMap<>();
        for (int i = 0; i < n; i++) {
            buckets.put(first.plusDays(i), new int[]{0, 0});
        }
        if (!petIds.isEmpty()) {
            for (FeedingLog f : feedingLogRepository.findByPetIdInAndTimestampAfterOrderByTimestampAsc(petIds, AppTime.startOfDay(first))) {
                int[] b = buckets.get(AppTime.dateOf(f.getTimestamp()));
                if (b != null) {
                    b[0] += f.getFoodGram();
                    b[1] += 1;
                }
            }
        }
        List<DailyFoodDto> out = new ArrayList<>();
        buckets.forEach((d, b) -> out.add(DailyFoodDto.builder().date(d).gram(b[0]).meals(b[1]).build()));
        return out;
    }
}
