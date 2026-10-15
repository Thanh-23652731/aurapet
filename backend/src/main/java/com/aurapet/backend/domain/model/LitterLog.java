package com.aurapet.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "litter_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class LitterLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID petId;

    @Enumerated(EnumType.STRING)
    @Column(nullable = false)
    private LitterResult result;

    @Column(length = 1024)
    private String imageUrl;

    @Column(nullable = false)
    private Instant timestamp;
}
