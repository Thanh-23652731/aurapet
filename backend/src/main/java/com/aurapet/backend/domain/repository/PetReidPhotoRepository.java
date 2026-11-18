package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.PetReidPhoto;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Modifying;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface PetReidPhotoRepository extends JpaRepository<PetReidPhoto, UUID> {

    /** Thông tin ảnh, KHÔNG tải dữ liệu ảnh (liệt kê nhanh). */
    record Info(UUID id, UUID petId, String source, Boolean usable, Instant createdAt) {
    }

    @Query("SELECT new com.aurapet.backend.domain.repository.PetReidPhotoRepository$Info(p.id, p.petId, p.source, p.usable, p.createdAt) "
            + "FROM PetReidPhoto p WHERE p.petId = :petId ORDER BY p.createdAt ASC")
    List<Info> findInfoByPetId(@Param("petId") UUID petId);

    @Query("SELECT new com.aurapet.backend.domain.repository.PetReidPhotoRepository$Info(p.id, p.petId, p.source, p.usable, p.createdAt) "
            + "FROM PetReidPhoto p ORDER BY p.createdAt ASC")
    List<Info> findAllInfo();

    long countByPetId(UUID petId);

    @Modifying
    @Query("DELETE FROM PetReidPhoto p WHERE p.petId = :petId")
    void deleteByPetId(@Param("petId") UUID petId);

    @Modifying
    @Query("UPDATE PetReidPhoto p SET p.usable = :usable WHERE p.id = :id")
    int updateUsable(@Param("id") UUID id, @Param("usable") Boolean usable);
}
