package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotNull;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PumpRequest {
    @NotNull(message = "Thiếu trạng thái bật/tắt bơm")
    private Boolean on;

    /** Số giây bơm chạy (bỏ trống = theo cài đặt). */
    @Min(value = 5, message = "Tối thiểu 5 giây")
    @Max(value = 300, message = "Tối đa 300 giây")
    private Integer seconds;
}
