package com.aurapet.backend.application.dto;

import lombok.*;

import java.util.List;
import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSettingsDto {
    private Integer targetGram;
    private Boolean autoMode;
    private Boolean scheduleEnabled;
    private List<String> scheduleTimes;
    private UUID feederPetId;
    private String feederPetName;
    /** Máy đang ghi nhật ký cho bé của tài khoản khác (id/tên bị ẩn với người xem). */
    private boolean feederPetOfOtherUser;
}
