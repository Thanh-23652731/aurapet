package com.aurapet.backend.domain.repository;

import com.aurapet.backend.domain.model.DeviceSettings;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.stereotype.Repository;

@Repository
public interface DeviceSettingsRepository extends JpaRepository<DeviceSettings, Integer> {
}
