package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.LitterLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface LitterLogRepository extends JpaRepository<LitterLog, UUID> {
    List<LitterLog> findByPetIdOrderByTimestampDesc(UUID petId);

    List<LitterLog> findByPetIdAndTimestampBetweenOrderByTimestampAsc(UUID petId, Instant start, Instant end);

    @Query("SELECT COUNT(l) FROM LitterLog l WHERE l.timestamp >= :since")
    Integer countLogsSince(@Param("since") Instant since);

    @Query("SELECT COUNT(l) FROM LitterLog l WHERE l.petId = :petId AND l.timestamp >= :since")
    Integer countLogsByPetIdSince(@Param("petId") UUID petId, @Param("since") Instant since);
}
