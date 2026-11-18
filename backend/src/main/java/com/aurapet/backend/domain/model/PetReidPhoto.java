package com.aurapet.backend.domain.model;

import jakarta.persistence.*;
import lombok.*;
import org.hibernate.annotations.CreationTimestamp;

import java.time.Instant;
import java.util.UUID;

/**
 * Ảnh mẫu để camera nhận ra từng bé (Re-ID bằng Feature Embedding DINOv2 trên Raspberry Pi).
 * Ảnh do chủ tải lên (source = upload, ảnh nguyên, Pi tự cắt con mèo) hoặc Pi tự chụp khi bé đứng trước
 * camera (source = camera, đã cắt sẵn con mèo). Pi tải các ảnh này về, tính vector đặc trưng rồi so khớp.
 */
@Entity
@Table(name = "pet_reid_photos", indexes = @Index(name = "idx_reid_pet", columnList = "petId"))
@Getter
@Setter
@NoArgsConstructor
@AllArgsConstructor
@Builder
public class PetReidPhoto {

    public static final String SOURCE_UPLOAD = "upload";
    public static final String SOURCE_CAMERA = "camera";

    @Id
    @GeneratedValue(strategy = GenerationType.UUID)
    private UUID id;

    @Column(nullable = false)
    private UUID petId;

    /** JPEG (web thu nhỏ còn cạnh dài <= 640 px trước khi gửi, ~30-80 KB). */
    @Basic(fetch = FetchType.LAZY)
    @Column(nullable = false, length = PetReidPhotoLimits.MAX_BYTES)
    private byte[] jpeg;

    @Column(nullable = false, length = 16)
    private String source;

    /** Pi báo lại: true = thấy mèo và đã học; false = không thấy mèo trong ảnh; null = Pi chưa xử lý. */
    private Boolean usable;

    @CreationTimestamp
    @Column(nullable = false, updatable = false)
    private Instant createdAt;
}
