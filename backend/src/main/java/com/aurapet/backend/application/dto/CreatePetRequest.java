package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePetRequest {

    /** Chỉ admin mới được tạo hộ cho người khác; người dùng thường luôn là chủ. */
    private UUID ownerId;

    @NotBlank(message = "Vui lòng nhập tên thú cưng")
    @Size(max = 60, message = "Tên tối đa 60 ký tự")
    private String name;

    @NotBlank(message = "Vui lòng nhập giống")
    @Size(max = 60, message = "Giống tối đa 60 ký tự")
    private String breed;

    @NotNull(message = "Vui lòng nhập tuổi")
    @Min(value = 0, message = "Tuổi không được âm")
    @Max(value = 40, message = "Tuổi không hợp lệ")
    private Integer age;

    @NotNull(message = "Vui lòng chọn giới tính")
    private Gender gender;

    @NotNull(message = "Vui lòng chọn đã triệt sản hay chưa")
    private Boolean neutered;

    @NotNull(message = "Vui lòng nhập cân nặng")
    @DecimalMin(value = "0.1", message = "Cân nặng tối thiểu 0.1 kg")
    @DecimalMax(value = "30", message = "Cân nặng không hợp lệ")
    private Double weight;

    @Size(max = 1024)
    private String imageUrl;
}
