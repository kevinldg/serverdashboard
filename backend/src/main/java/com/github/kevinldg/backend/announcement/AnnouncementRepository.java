package com.github.kevinldg.backend.announcement;

import org.springframework.data.mongodb.repository.MongoRepository;

import java.util.List;

public interface AnnouncementRepository extends MongoRepository<Announcement, String> {

    List<Announcement> findByActiveTrue();
}
