package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.Gender;
import jakarta.validation.constraints.DecimalMin;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class CreatePetRequest {

    private UUID ownerId;

    @NotBlank(message = "Pet name is required")
    private String name;

    @NotBlank(message = "Pet breed is required")
    private String breed;

    @NotNull(message = "Age is required")
    @Min(value = 0, message = "Age cannot be negative")
    private Integer age;

    @NotNull(message = "Gender is required")
    private Gender gender;

    @NotNull(message = "Neutered status is required")
    private Boolean neutered;

    @NotNull(message = "Weight is required")
    @DecimalMin(value = "0.1", message = "Weight must be at least 0.1 kg")
    private Double weight;

    private String imageUrl;
}
