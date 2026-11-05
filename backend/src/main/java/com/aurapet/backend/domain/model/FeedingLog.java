package com.aurapet.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;

import java.time.Instant;
import java.util.UUID;

@Entity
@Table(name = "feeding_logs")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class FeedingLog {

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID petId;

    /** Số gram thực tế máy nhả ra (đo bằng cân HX711). */
    @Column(nullable = false)
    private Integer foodGram;

    /** Nguồn lệnh: Web, App, Nút vật lý, Tự động, Lịch hẹn giờ... */
    @Column(length = 40)
    private String source;

    @Column(nullable = false)
    private Instant timestamp;
}
