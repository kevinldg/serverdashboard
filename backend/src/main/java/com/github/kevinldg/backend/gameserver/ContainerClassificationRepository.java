package com.github.kevinldg.backend.gameserver;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContainerClassificationRepository extends MongoRepository<ContainerClassification, String> {
}
