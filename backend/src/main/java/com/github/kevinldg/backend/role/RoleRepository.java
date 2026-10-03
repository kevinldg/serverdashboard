package com.github.kevinldg.backend.role;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;
import java.util.Optional;

public interface RoleRepository extends MongoRepository<Role, String> {

    Optional<Role> findByName(String name);

    boolean existsByNameIgnoreCase(String name);

    List<Role> findBySuperuserTrue();
}
