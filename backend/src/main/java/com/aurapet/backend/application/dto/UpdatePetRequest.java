package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.Gender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import lombok.*;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UpdatePetRequest {
    private String name;
    private String breed;
    @Min(value = 0, message = "Age cannot be negative")
    private Integer age;
    private Gender gender;
    private Boolean neutered;
    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
    private Double weight;
    private String imageUrl;
}
