package com.likelion.hackathon_be.story.api;

import com.likelion.hackathon_be.story.application.StoryService;
import com.likelion.hackathon_be.story.dto.StoryEpisodeResponse;
import com.likelion.hackathon_be.story.dto.StoryProgressResponse;
import java.time.OffsetDateTime;
import java.util.List;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.setup.MockMvcBuilders;

import static org.mockito.Mockito.mock;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

class StoryControllerTests {

    private StoryService storyService;
    private MockMvc mockMvc;

    @BeforeEach
    void setUp() {
        storyService = mock(StoryService.class);
        mockMvc = MockMvcBuilders.standaloneSetup(new StoryController(storyService)).build();
    }

    @Test
    void returnsStoryProgressAndEpisodeContract() throws Exception {
        when(storyService.getStories()).thenReturn(new StoryProgressResponse(
                27,
                27,
                3,
                List.of(
                        new StoryEpisodeResponse(
                                1,
                                10,
                                true,
                                OffsetDateTime.parse("2026-07-20T23:10:00+09:00")
                        ),
                        new StoryEpisodeResponse(3, 30, false, null)
                )
        ));

        mockMvc.perform(get("/api/v1/stories"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.currentStreakDays").value(27))
                .andExpect(jsonPath("$.data.maxAchievedStreakDays").value(27))
                .andExpect(jsonPath("$.data.avatarStage").value(3))
                .andExpect(jsonPath("$.data.episodes[0].episodeNumber").value(1))
                .andExpect(jsonPath("$.data.episodes[0].requiredStreakDays").value(10))
                .andExpect(jsonPath("$.data.episodes[0].unlocked").value(true))
                .andExpect(jsonPath("$.data.episodes[0].unlockedAt")
                        .value("2026-07-20T23:10:00+09:00"))
                .andExpect(jsonPath("$.data.episodes[1].unlocked").value(false))
                .andExpect(jsonPath("$.data.episodes[1].unlockedAt").doesNotExist());

        verify(storyService).getStories();
    }
}
