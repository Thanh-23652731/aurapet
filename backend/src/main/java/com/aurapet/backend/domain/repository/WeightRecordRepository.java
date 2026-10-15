package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.WeightRecord;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.List;
import java.util.UUID;

@Repository
public interface WeightRecordRepository extends JpaRepository<WeightRecord, UUID> {
    List<WeightRecord> findByPetIdOrderByRecordedAtAsc(UUID petId);
}
