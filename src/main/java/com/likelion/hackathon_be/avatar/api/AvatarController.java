package com.likelion.hackathon_be.avatar.api;

import com.likelion.hackathon_be.avatar.application.AvatarService;
import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.dto.AvatarResponse;
import com.likelion.hackathon_be.avatar.dto.CreateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.RegenerateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentRequest;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentResponse;
import com.likelion.hackathon_be.common.api.ApiResponse;
import jakarta.validation.Valid;
import jakarta.validation.constraints.NotNull;
import org.springframework.core.io.Resource;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PatchMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;

@Validated
@RestController
@RequestMapping("/api/v1/avatars/me")
public class AvatarController {

    private final AvatarService avatarService;

    public AvatarController(AvatarService avatarService) {
        this.avatarService = avatarService;
    }

    @GetMapping
    public ApiResponse<AvatarResponse> getMyAvatar() {
        return ApiResponse.of(avatarService.getMyAvatar());
    }

    @PutMapping(consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<CreateAvatarResponse> createAvatar(
            @NotNull @RequestParam AvatarGrowthTrack growthTrack,
            @RequestParam(required = false) MultipartFile facePhoto
    ) {
        return ApiResponse.of(avatarService.createAvatar(growthTrack, facePhoto));
    }

    @GetMapping(value = "/image", produces = MediaType.IMAGE_PNG_VALUE)
    public ResponseEntity<Resource> getMyAvatarImage() {
        return avatarService.getMyAvatarImage();
    }

    @PostMapping(value = "/regenerate", consumes = MediaType.MULTIPART_FORM_DATA_VALUE)
    public ApiResponse<RegenerateAvatarResponse> regenerateAvatar(
            @RequestParam(required = false) MultipartFile facePhoto
    ) {
        return ApiResponse.of(avatarService.regenerateAvatar(facePhoto));
    }

    @PutMapping("/equipment")
    public ApiResponse<UpdateAvatarEquipmentResponse> updateEquipment(
            @Valid @RequestBody UpdateAvatarEquipmentRequest request
    ) {
        return ApiResponse.of(avatarService.updateEquipment(request));
    }
}
