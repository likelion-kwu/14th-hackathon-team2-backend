package com.likelion.hackathon_be.story.repository;

import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import org.springframework.data.jpa.repository.JpaRepository;

public interface UserStoryUnlockRepository extends JpaRepository<UserStoryUnlock, Long> {
}
