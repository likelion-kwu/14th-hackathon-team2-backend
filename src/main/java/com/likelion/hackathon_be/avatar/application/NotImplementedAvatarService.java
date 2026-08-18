package com.likelion.hackathon_be.avatar.application;

import com.likelion.hackathon_be.avatar.domain.AvatarGrowthTrack;
import com.likelion.hackathon_be.avatar.dto.AvatarResponse;
import com.likelion.hackathon_be.avatar.dto.CreateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.RegenerateAvatarResponse;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentRequest;
import com.likelion.hackathon_be.avatar.dto.UpdateAvatarEquipmentResponse;
import com.likelion.hackathon_be.common.error.FeatureNotImplementedException;
import org.springframework.core.io.Resource;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Service;
import org.springframework.web.multipart.MultipartFile;

@Service
public class NotImplementedAvatarService implements AvatarService {

    @Override
    public AvatarResponse getMyAvatar() {
        throw new FeatureNotImplementedException("Avatar");
    }

    @Override
    public CreateAvatarResponse createAvatar(AvatarGrowthTrack growthTrack, MultipartFile facePhoto) {
        throw new FeatureNotImplementedException("Avatar");
    }

    @Override
    public ResponseEntity<Resource> getMyAvatarImage() {
        throw new FeatureNotImplementedException("Avatar image");
    }

    @Override
    public RegenerateAvatarResponse regenerateAvatar(MultipartFile facePhoto) {
        throw new FeatureNotImplementedException("Avatar regeneration");
    }

    @Override
    public UpdateAvatarEquipmentResponse updateEquipment(UpdateAvatarEquipmentRequest request) {
        throw new FeatureNotImplementedException("Avatar equipment");
    }
}
