package com.likelion.hackathon_be.avatar.application;

import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.avatar.dto.SelectAvatarDialogueRequest;
import com.likelion.hackathon_be.common.auth.CurrentUserProvider;
import com.likelion.hackathon_be.speech.application.AvatarDialogueSelector;
import org.springframework.stereotype.Service;

@Service
public class DefaultAvatarDialogueService implements AvatarDialogueService {
    private final CurrentUserProvider currentUserProvider;
    private final AvatarDialogueSelector dialogueSelector;

    public DefaultAvatarDialogueService(
            CurrentUserProvider currentUserProvider,
            AvatarDialogueSelector dialogueSelector
    ) {
        this.currentUserProvider = currentUserProvider;
        this.dialogueSelector = dialogueSelector;
    }

    @Override
    public AvatarDialogueSelectionResponse selectDialogue(SelectAvatarDialogueRequest request) {
        Long userId = currentUserProvider.getCurrentUser().id();
        return dialogueSelector.selectForUser(userId, request.situation());
    }
}
