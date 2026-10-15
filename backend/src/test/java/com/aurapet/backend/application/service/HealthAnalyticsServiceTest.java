package com.aurapet.backend.application.service;

import com.aurapet.backend.application.dto.HealthSummaryDto;
import com.aurapet.backend.domain.model.Gender;
import com.aurapet.backend.domain.model.Pet;
import com.aurapet.backend.domain.repository.FeedingLogRepository;
import com.aurapet.backend.domain.repository.LitterLogRepository;
import com.aurapet.backend.domain.repository.PetRepository;
import com.aurapet.backend.domain.repository.WeightRecordRepository;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.util.Collections;
import java.util.Optional;
import java.util.UUID;

import static org.assertj.core.api.Assertions.assertThat;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.Mockito.when;

@ExtendWith(MockitoExtension.class)
class HealthAnalyticsServiceTest {

    @Mock
    private PetRepository petRepository;

    @Mock
    private WeightRecordRepository weightRecordRepository;

    @Mock
    private FeedingLogRepository feedingLogRepository;

    @Mock
    private LitterLogRepository litterLogRepository;

    @InjectMocks
    private HealthAnalyticsService healthAnalyticsService;

    @Test
    @DisplayName("Should generate Edge AI health summary")
    void testGetHealthSummary() {
        UUID petId = UUID.randomUUID();
        Pet pet = Pet.builder()
                .id(petId)
                .name("Luna")
                .breed("British Shorthair")
                .age(2)
                .gender(Gender.FEMALE)
                .neutered(true)
                .weight(4.25)
                .build();

        when(petRepository.findById(petId)).thenReturn(Optional.of(pet));
        when(feedingLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any())).thenReturn(Collections.emptyList());
        when(litterLogRepository.findByPetIdAndTimestampBetweenOrderByTimestampAsc(any(), any(), any())).thenReturn(Collections.emptyList());
        when(weightRecordRepository.findByPetIdOrderByRecordedAtAsc(petId)).thenReturn(Collections.emptyList());

        HealthSummaryDto summary = healthAnalyticsService.getHealthSummary(petId);

        assertThat(summary).isNotNull();
        assertThat(summary.getPetName()).isEqualTo("Luna");
        assertThat(summary.getHealthScore()).isBetween(70, 100);
        assertThat(summary.getAiRecommendation()).contains("Luna");
    }
}
