package com.likelion.hackathon_be.story.repository;

import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import java.util.Collection;
import java.util.List;
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
            """, nativeQuery = true)
    Optional<Integer> findMaximumAvatarStage(@Param("userId") Long userId);

    @Query(value = """
            select max(se.episode_number)
             from user_story_unlocks usu
              join story_episodes se on se.id = usu.episode_id
             where usu.user_id = :userId
            """, nativeQuery = true)
    Optional<Integer> findHighestUnlockedEpisodeNumber(@Param("userId") Long userId);

    List<UserStoryUnlock> findByUserId(Long userId);

    @Query("""
            select unlock.episodeId
            from UserStoryUnlock unlock
            where unlock.userId = :userId
              and unlock.episodeId in :episodeIds
            """)
    List<Long> findEpisodeIdsByUserIdAndEpisodeIdIn(
            @Param("userId") Long userId,
            @Param("episodeIds") Collection<Long> episodeIds
    );
}
