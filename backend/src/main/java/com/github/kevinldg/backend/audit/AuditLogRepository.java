package com.github.kevinldg.backend.audit;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface AuditLogRepository extends MongoRepository<AuditEntry, String> {
}
