package com.likelion.hackathon_be.avatar.api;

import com.likelion.hackathon_be.avatar.application.AvatarDialogueService;
import com.likelion.hackathon_be.avatar.dto.AvatarDialogueSelectionResponse;
import com.likelion.hackathon_be.avatar.dto.SelectAvatarDialogueRequest;
import com.likelion.hackathon_be.common.api.ApiResponse;
import jakarta.validation.Valid;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

@Validated
@RestController
@RequestMapping("/api/v1/avatar-dialogues/selections")
public class AvatarDialogueController {

    private final AvatarDialogueService avatarDialogueService;

    public AvatarDialogueController(AvatarDialogueService avatarDialogueService) {
        this.avatarDialogueService = avatarDialogueService;
    }

    @PostMapping
    public ApiResponse<AvatarDialogueSelectionResponse> selectDialogue(
            @Valid @RequestBody SelectAvatarDialogueRequest request
    ) {
        return ApiResponse.of(avatarDialogueService.selectDialogue(request));
    }
}
