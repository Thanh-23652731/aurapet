package com.aurapet.backend.application.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class HealthSummaryDto {
    private UUID petId;
    private String petName;
    private Integer healthScore;
    private String status;
    private Double dailyAvgFoodGram;
    private Integer expectedDailyFoodGram;
    private Integer weeklyLitterVisits;
    private Double weightChangeKg;
    /** Có nhật ký cho ăn trong 7 ngày. false = điểm sức khoẻ chưa đáng tin, giao diện hiện "–". */
    private boolean hasFeedingData;
    /** Có ít nhất 2 lần cân để so sánh; false = weightChangeKg không có ý nghĩa. */
    private boolean weightCompared;
    private String aiRecommendation;
    private List<String> notes;
}
