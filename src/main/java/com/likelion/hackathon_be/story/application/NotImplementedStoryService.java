package com.likelion.hackathon_be.story.application;

import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import com.likelion.hackathon_be.story.dto.StoryProgressResponse;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedStoryService implements StoryService {

    @Override
    public StoryProgressResponse getStories() {
        throw new FeatureNotImplementedException("Story");
    }
}
