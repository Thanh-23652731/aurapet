package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.Alert;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Collection;
import java.util.List;
import java.util.UUID;

@Repository
public interface AlertRepository extends JpaRepository<Alert, UUID> {

    List<Alert> findTop15ByOrderByCreatedAtDesc();

    /** Cảnh báo chung của thiết bị + cảnh báo về các bé trong danh sách. */
    List<Alert> findTop15ByPetIdIsNullOrPetIdInOrderByCreatedAtDesc(Collection<UUID> petIds);

    /** Chỉ cảnh báo chung (người dùng chưa có bé nào). */
    List<Alert> findTop15ByPetIdIsNullOrderByCreatedAtDesc();
}
