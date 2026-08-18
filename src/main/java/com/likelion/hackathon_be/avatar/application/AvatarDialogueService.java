package com.likelion.hackathon_be.avatar.application;

import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.avatar.dto.SelectAvatarDialogueRequest;

public interface AvatarDialogueService {

    AvatarDialogueSelectionResponse selectDialogue(SelectAvatarDialogueRequest request);
}
