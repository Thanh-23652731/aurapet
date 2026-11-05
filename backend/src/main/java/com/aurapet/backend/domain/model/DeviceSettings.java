package com.aurapet.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.UpdateTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Cấu hình của máy cho ăn (chỉ có 1 dòng, id = 1).
 * Đây là "nguồn sự thật" cho các thiết lập; ESP32 được đồng bộ lại mỗi khi kết nối.
 */
@Entity
@Table(name = "device_settings")
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class DeviceSettings {

    public static final int SINGLETON_ID = 1;

    @Id
    private Integer id;

    /** Khẩu phần mỗi lần cho ăn (gram). */
    @Column(nullable = false)
    private Integer targetGram;

    /** Tự động cho ăn khi phát hiện mèo và khay gần hết. */
    @Column(nullable = false)
    private Boolean autoMode;

    @Column(nullable = false)
    private Boolean scheduleEnabled;

    /** Danh sách giờ cho ăn dạng "07:00,12:00,18:00" (giờ Việt Nam). */
    @Column(length = 255)
    private String scheduleTimes;

    /** Con mèo được gán cho máy cho ăn (log cho ăn sẽ ghi cho con này). */
    private UUID feederPetId;

    /** Lần máy dọn cát cào xong gần nhất (ESP32 báo qua PREFIX/litter). */
    private Instant lastLitterCleanAt;

    @UpdateTimestamp
    private Instant updatedAt;
}
