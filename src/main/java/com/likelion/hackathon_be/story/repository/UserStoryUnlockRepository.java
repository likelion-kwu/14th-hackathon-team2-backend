package com.likelion.hackathon_be.story.repository;

import com.likelion.hackathon_be.story.domain.UserStoryUnlock;
import java.util.Collection;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;
import org.springframework.data.jpa.repository.Query;
import org.springframework.data.repository.query.Param;

public interface UserStoryUnlockRepository extends JpaRepository<UserStoryUnlock, Long> {

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
