package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

/** Cập nhật từng phần: trường nào null thì giữ nguyên. */
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdateDeviceSettingsRequest {

    @Min(value = 5, message = "Khẩu phần tối thiểu 5g")
    @Max(value = 150, message = "Khẩu phần tối đa 150g")
    private Integer targetGram;

    private Boolean autoMode;

    private Boolean scheduleEnabled;

    @Size(max = 8, message = "Tối đa 8 bữa mỗi ngày")
    private List<String> scheduleTimes;

    private UUID feederPetId;
}
