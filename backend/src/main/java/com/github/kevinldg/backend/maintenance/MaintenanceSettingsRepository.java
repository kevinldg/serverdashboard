package com.github.kevinldg.backend.maintenance;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface MaintenanceSettingsRepository extends MongoRepository<MaintenanceSettings, String> {
}
