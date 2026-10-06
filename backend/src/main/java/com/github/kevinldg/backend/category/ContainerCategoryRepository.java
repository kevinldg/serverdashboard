package com.github.kevinldg.backend.category;

import org.springframework.data.mongodb.repository.MongoRepository;

public interface ContainerCategoryRepository extends MongoRepository<ContainerCategory, String> {

    boolean existsByNameIgnoreCase(String name);
}
