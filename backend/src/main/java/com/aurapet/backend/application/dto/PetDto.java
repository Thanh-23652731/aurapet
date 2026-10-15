package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.Gender;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetDto {
    private UUID id;
    private UUID ownerId;
    private String name;
    private String breed;
    private Integer age;
    private Gender gender;
    private Boolean neutered;
    private Double weight;
    private String imageUrl;
}
