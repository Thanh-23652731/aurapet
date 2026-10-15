package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.SystemStatus;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;
import java.util.UUID;

@Repository
public interface SystemStatusRepository extends JpaRepository<SystemStatus, UUID> {
    Optional<SystemStatus> findFirstByOrderByLastUpdatedDesc();
}
