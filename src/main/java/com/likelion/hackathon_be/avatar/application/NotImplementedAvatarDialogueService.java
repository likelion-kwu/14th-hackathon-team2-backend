package com.likelion.hackathon_be.avatar.application;

import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.avatar.dto.SelectAvatarDialogueRequest;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import org.springframework.stereotype.Service;

@Service
public class NotImplementedAvatarDialogueService implements AvatarDialogueService {

    @Override
    public AvatarDialogueSelectionResponse selectDialogue(SelectAvatarDialogueRequest request) {
        throw new FeatureNotImplementedException("Avatar dialogue");
    }
}
