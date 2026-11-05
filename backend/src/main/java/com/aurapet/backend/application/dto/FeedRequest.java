package com.aurapet.backend.application.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedRequest {
    /** Số gram cần nhả; bỏ trống = dùng khẩu phần mặc định. */
    @Min(value = 5, message = "Tối thiểu 5g")
    @Max(value = 150, message = "Tối đa 150g mỗi lần")
    private Integer gram;

    /** "Web" hoặc "App" — dùng để ghi nhật ký. */
    private String source;
}
