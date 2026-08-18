package com.likelion.hackathon_be.routine.verification.application;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.util.Arrays;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalVerificationPhotoStorage implements VerificationPhotoStorage {

    @Override
    public StoredVerificationPhoto store(MultipartFile photo) {
        if (photo == null || photo.isEmpty()
                || photo.getSize() > PhotoVerificationInput.MAX_IMAGE_BYTES) {
            throw new BusinessException(ErrorCode.VALIDATION_ERROR);
        }
        try {
            try (InputStream inputStream = photo.getInputStream()) {
                byte[] image = inputStream.readNBytes(PhotoVerificationInput.MAX_IMAGE_BYTES + 1);
                try {
                    if (image.length > PhotoVerificationInput.MAX_IMAGE_BYTES) {
                        throw new BusinessException(ErrorCode.VALIDATION_ERROR);
                    }
                    return new StoredVerificationPhoto(image, photo.getContentType());
                } finally {
                    Arrays.fill(image, (byte) 0);
                }
            }
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PHOTO_AI_UNAVAILABLE);
        }
    }

    @Override
    public void delete(StoredVerificationPhoto photo) {
        if (photo != null) {
            photo.destroy();
        }
    }
}
