package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.Gender;
import jakarta.validation.constraints.*;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePetRequest {

    @Size(min = 1, max = 60, message = "Tên từ 1 đến 60 ký tự")
    private String name;

    @Size(min = 1, max = 60, message = "Giống từ 1 đến 60 ký tự")
    private String breed;

    @Min(value = 0, message = "Tuổi không được âm")
    @Max(value = 40, message = "Tuổi không hợp lệ")
    private Integer age;

    private Gender gender;

    private Boolean neutered;

    @DecimalMin(value = "0.1", message = "Cân nặng tối thiểu 0.1 kg")
    @DecimalMax(value = "30", message = "Cân nặng không hợp lệ")
    private Double weight;

    @Size(max = 1024)
    private String imageUrl;
}
