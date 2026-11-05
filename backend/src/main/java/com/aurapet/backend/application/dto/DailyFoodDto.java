package com.aurapet.backend.application.dto;

import lombok.*;

import java.time.LocalDate;

/** Tổng lượng ăn trong 1 ngày (theo giờ Việt Nam). */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DailyFoodDto {
    private LocalDate date;
    private Integer gram;
    private Integer meals;
}
