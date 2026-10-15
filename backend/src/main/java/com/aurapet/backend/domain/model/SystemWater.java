package com.aurapet.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;

@Entity
@Table(name = "system_water")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class SystemWater {

    @Id
    @Column(length = 36)
    private String id;

    @Column(name = "device_id", nullable = false, unique = true, length = 50)
    private String deviceId;

    @Column(name = "auto_mode", nullable = false)
    private Boolean autoMode;

    @Column(name = "btn_led", nullable = false)
    private Boolean btnLed;

    @Column(name = "led_green", nullable = false)
    private Boolean ledGreen;

    @Column(name = "led_red", nullable = false)
    private Boolean ledRed;

    @Column(nullable = false)
    private Boolean machines;

    @Column(nullable = false)
    private Integer percel;

    @Column(name = "water_level_percent", nullable = false)
    private Integer waterLevelPercent;

    @Column(name = "cat_detected")
    private Boolean catDetected;

    @Column(name = "last_pumped")
    private Instant lastPumped;

    @UpdateTimestamp
    @Column(name = "updated_at")
    private Instant updatedAt;
}
