package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.SystemWater;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemWaterRepository extends JpaRepository<SystemWater, String> {
    Optional<SystemWater> findFirstByOrderByUpdatedAtDesc();
    Optional<SystemWater> findByDeviceId(String deviceId);
}
