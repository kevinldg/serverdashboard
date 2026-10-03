package com.github.kevinldg.backend.user;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.Collection;
import java.util.Optional;

public interface UserRepository extends MongoRepository<User, String> {

    Optional<User> findByUsername(String username);

    boolean existsByUsername(String username);

    boolean existsByRoleId(String roleId);

    long countByRoleId(String roleId);

    long countByRoleIdInAndActiveTrue(Collection<String> roleIds);
}
