package com.aurapet.backend.application.dto;

import com.aurapet.backend.domain.model.Role;
import lombok.*;

import java.util.UUID;

@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class UserDto {
    private UUID id;
    private String name;
    private String email;
    private Role role;
}
