package com.github.kevinldg.backend.configfile;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ConfigFileBackupRepository extends MongoRepository<ConfigFileBackup, String> {
}
