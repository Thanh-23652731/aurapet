package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.FeedingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.List;
import java.util.UUID;

@Repository
public interface FeedingLogRepository extends JpaRepository<FeedingLog, UUID> {
    List<FeedingLog> findAllByOrderByTimestampDesc();

    List<FeedingLog> findByPetIdOrderByTimestampDesc(UUID petId);

    List<FeedingLog> findByPetIdAndTimestampBetweenOrderByTimestampAsc(UUID petId, Instant start, Instant end);

    @Query("SELECT COALESCE(SUM(f.foodGram), 0) FROM FeedingLog f WHERE f.timestamp >= :since")
    Integer sumFoodGramSince(@Param("since") Instant since);

    @Query("SELECT COUNT(f) FROM FeedingLog f WHERE f.timestamp >= :since")
    Integer countSessionsSince(@Param("since") Instant since);

    @Query("SELECT COALESCE(SUM(f.foodGram), 0) FROM FeedingLog f WHERE f.petId = :petId AND f.timestamp >= :since")
    Integer sumFoodGramByPetIdSince(@Param("petId") UUID petId, @Param("since") Instant since);
}
