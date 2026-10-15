package com.aurapet.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "system_status")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemStatus {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private Boolean feeder;

    @Column(nullable = false)
    private Boolean waterPump;

    @Column(nullable = false)
    private Boolean litterCleaner;

    @Column(nullable = false)
    private String esp32; // ONLINE, OFFLINE, BUSY

    @Column(nullable = false)
    private String raspberryPi; // ONLINE, OFFLINE, BUSY

    private Integer foodLevelPercent;
    private Integer waterLevelPercent;
    private Integer litterWastePercent;

    @UpdateTimestamp
    private Instant lastUpdated;
}
