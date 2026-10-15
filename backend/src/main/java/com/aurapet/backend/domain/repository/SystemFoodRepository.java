package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.SystemFood;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

import java.util.Optional;

@Repository
public interface SystemFoodRepository extends JpaRepository<SystemFood, String> {
    Optional<SystemFood> findFirstByOrderByUpdatedAtDesc();
    Optional<SystemFood> findByDeviceId(String deviceId);
}
