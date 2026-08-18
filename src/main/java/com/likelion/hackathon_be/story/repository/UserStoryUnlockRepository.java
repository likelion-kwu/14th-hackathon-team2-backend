package com.likelion.hackathon_be.story.repository;

import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import java.util.Optional;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserStoryUnlockRepository extends JpaRepository<UserStoryUnlock, Long> {
    @Query(value = """
            select max(se.avatar_stage)
              from user_story_unlocks usu
              join story_episodes se on se.id = usu.episode_id
             where usu.user_id = :userId
               and se.active = true
            """, nativeQuery = true)
    Optional<Integer> findMaximumAvatarStage(@Param("userId") Long userId);

    @Query(value = """
            select max(se.episode_number)
              from user_story_unlocks usu
              join story_episodes se on se.id = usu.episode_id
             where usu.user_id = :userId
               and se.active = true
            """, nativeQuery = true)
    Optional<Integer> findHighestUnlockedEpisodeNumber(@Param("userId") Long userId);
}
