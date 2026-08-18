package com.likelion.hackathon_be.avatar.application;

import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.dto.AvatarResponse;
import com.likelion.hackathon_be.avatar.dto.CreateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.RegenerateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentRequest;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentResponse;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.web.multipart.MultipartFile;

public interface AvatarService {

    AvatarResponse getMyAvatar();

    CreateAvatarResponse createAvatar(AvatarGrowthTrack growthTrack, MultipartFile facePhoto);

    ResponseEntity<Resource> getMyAvatarImage();

    RegenerateAvatarResponse regenerateAvatar(MultipartFile facePhoto);

    UpdateAvatarEquipmentResponse updateEquipment(UpdateAvatarEquipmentRequest request);
}
