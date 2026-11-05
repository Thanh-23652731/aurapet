package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.DailyFoodDto;
import com.aurapet.backend.application.dto.HealthSummaryDto;
import com.aurapet.backend.domain.model.FeedingLog;
import com.aurapet.backend.domain.model.Gender;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.WeightRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.Instant;
import java.util.Collections;
import java.util.List;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthAnalyticsServiceTest {

    @Mock
    private PetService petService;
    @Mock
    private WeightRecordRepository weightRecordRepository;
    @Mock
    private FeedingLogRepository feedingLogRepository;
    @Mock
    private LitterLogRepository litterLogRepository;

    @InjectMocks
    private HealthAnalyticsService healthAnalyticsService;

    private final Actor actor = new Actor(UUID.randomUUID(), true);

    private Pet luna(UUID id) {
        return Pet.builder().id(id).ownerId(actor.id()).name("Luna").breed("Mèo ta")
                .age(2).gender(Gender.FEMALE).neutered(true).weight(4.0).build();
    }

    @Test
    @DisplayName("Không có dữ liệu ăn -> có ghi chú nhắc và điểm giảm")
    void summaryWithoutData() {
        UUID petId = UUID.randomUUID();
        when(petService.requireAccessible(petId, actor)).thenReturn(luna(petId));
        when(feedingLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(eq(petId), any(), any())).thenReturn(Collections.emptyList());
        when(litterLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(eq(petId), any(), any())).thenReturn(Collections.emptyList());
        when(weightRecordRepository.findByPetIdOrderByRecordedAtAsc(petId)).thenReturn(Collections.emptyList());

        HealthSummaryDto s = healthAnalyticsService.getHealthSummary(petId, actor);

        assertThat(s.getPetName()).isEqualTo("Luna");
        assertThat(s.getExpectedDailyFoodGram()).isEqualTo(60);
        assertThat(s.getHealthScore()).isEqualTo(85);
        assertThat(s.getStatus()).isEqualTo("Cần theo dõi");
        assertThat(s.getNotes()).anyMatch(n -> n.contains("Chưa có dữ liệu"));
    }

    @Test
    @DisplayName("Tổng lượng ăn theo ngày luôn đủ 7 cột")
    void dailyTotalsHasAllDays() {
        UUID petId = UUID.randomUUID();
        when(feedingLogRepository.findByPetIdInAndTimestampAfterOrderByTimestampAsc(any(), any()))
                .thenReturn(List.of(FeedingLog.builder().petId(petId).foodGram(30).timestamp(Instant.now()).build()));

        List<DailyFoodDto> days = healthAnalyticsService.dailyTotals(List.of(petId), 7);

        assertThat(days).hasSize(7);
        assertThat(days.get(6).getGram()).isEqualTo(30);
        assertThat(days.get(6).getMeals()).isEqualTo(1);
    }
}
