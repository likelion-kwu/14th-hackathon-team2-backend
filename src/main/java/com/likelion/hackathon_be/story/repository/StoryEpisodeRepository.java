package com.likelion.hackathon_be.story.repository;

import com.likelion.hackathon_be.story.domain.StoryEpisode;
import java.util.List;
import org.springframework.data.jpa.repository.JpaRepository;

public interface StoryEpisodeRepository extends JpaRepository<StoryEpisode, Long> {

    List<StoryEpisode> findByActiveTrueOrderByRequiredStreakAscEpisodeNumberAsc();
}
