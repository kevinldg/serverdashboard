package com.github.kevinldg.backend.gameserver;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContainerClassificationRepository extends MongoRepository<ContainerClassification, String> {

    long countByCategoryId(String categoryId);

    /** @return the number of removed classifications */
    long deleteByCategoryId(String categoryId);
}
