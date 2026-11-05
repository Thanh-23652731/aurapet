package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.FeedingLog;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.time.Instant;
import java.util.Collection;
import java.util.List;
import java.util.Optional;
import java.util.UUID;

@Repository
public interface FeedingLogRepository extends JpaRepository<FeedingLog, UUID> {

    List<FeedingLog> findByPetIdOrderByTimestampDesc(UUID petId);

    List<FeedingLog> findByPetIdAndTimestampBetweenOrderByTimestampAsc(UUID petId, Instant start, Instant end);

    List<FeedingLog> findByPetIdInAndTimestampAfterOrderByTimestampAsc(Collection<UUID> petIds, Instant since);

    List<FeedingLog> findTop100ByPetIdInOrderByTimestampDesc(Collection<UUID> petIds);

    Optional<FeedingLog> findFirstByOrderByTimestampDesc();
}
