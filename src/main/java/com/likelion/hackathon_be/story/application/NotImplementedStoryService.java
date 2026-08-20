package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.story.dto.StoryProgressResponse;

public class NotImplementedStoryService implements StoryService {

    @Override
    public StoryProgressResponse getStories() {
        throw new FeatureNotImplementedException("Story");
    }
}
