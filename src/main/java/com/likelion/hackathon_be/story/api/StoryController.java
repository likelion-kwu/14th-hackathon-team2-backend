package com.likelion.hackathon_be.story.api;

import com.likelion.hackathon_be.common.api.ApiResponse;
import com.likelion.hackathon_be.story.application.StoryService;
import com.likelion.hackathon_be.story.dto.StoryProgressResponse;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@RestController
@RequestMapping("/api/v1/stories")
public class StoryController {

    private final StoryService storyService;

    public StoryController(StoryService storyService) {
        this.storyService = storyService;
    }

    @GetMapping
    public ApiResponse<StoryProgressResponse> getStories() {
        return ApiResponse.of(storyService.getStories());
    }
}
