package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.LitterLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface LitterLogRepository extends JpaRepository<LitterLog, UUID> {

    List<LitterLog> findByPetIdOrderByTimestampDesc(UUID petId);

    List<LitterLog> findByPetIdAndTimestampBetweenOrderByTimestampAsc(UUID petId, Instant start, Instant end);

    long countByPetIdInAndTimestampAfter(Collection<UUID> petIds, Instant since);

    Optional<LitterLog> findFirstByOrderByTimestampDesc();
}
