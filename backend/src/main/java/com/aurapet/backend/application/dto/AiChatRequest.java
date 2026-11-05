package com.aurapet.backend.application.dto;

import jakarta.validation.Valid;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.*;

import java.util.List;
import java.util.UUID;

/** Câu hỏi gửi Bác sĩ AI. Dữ liệu của mèo do backend tự lấy từ CSDL theo petId. */
@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class AiChatRequest {

    @NotBlank(message = "Vui lòng nhập câu hỏi")
    @Size(max = 2000, message = "Câu hỏi tối đa 2000 ký tự")
    private String message;

    /** Con mèo đang hỏi (tuỳ chọn). */
    private UUID petId;

    /** Vài lượt hội thoại gần nhất để AI hiểu ngữ cảnh (tối đa 10). */
    @Valid
    @Size(max = 10)
    private List<AiChatTurn> history;
}
