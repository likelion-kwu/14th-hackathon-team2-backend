package com.likelion.hackathon_be.routine.verification.application;

import com.likelion.hackathon_be.common.error.BusinessException;
import com.likelion.hackathon_be.common.error.ErrorCode;
import java.io.IOException;
import java.io.InputStream;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import org.springframework.stereotype.Component;
import org.springframework.web.multipart.MultipartFile;

@Component
public class LocalVerificationPhotoStorage implements VerificationPhotoStorage {

    @Override
    public StoredVerificationPhoto store(MultipartFile photo) {
        try {
            Path tempFile = Files.createTempFile("routine-photo-", ".jpg");
            try (InputStream inputStream = photo.getInputStream()) {
                Files.copy(inputStream, tempFile, StandardCopyOption.REPLACE_EXISTING);
            }
            return new StoredVerificationPhoto(tempFile, photo.getContentType());
        } catch (IOException exception) {
            throw new BusinessException(ErrorCode.PHOTO_AI_UNAVAILABLE);
        }
    }

    @Override
    public void delete(StoredVerificationPhoto photo) {
        try {
            Files.deleteIfExists(photo.path());
        } catch (IOException ignored) {
            // The original verification photo must never be retained intentionally.
        }
    }
}
