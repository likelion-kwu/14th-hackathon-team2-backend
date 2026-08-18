package com.likelion.hackathon_be.routine.verification.application;

import org.springframework.web.multipart.MultipartFile;

public interface VerificationPhotoStorage {

    StoredVerificationPhoto store(MultipartFile photo);

    void delete(StoredVerificationPhoto photo);
}
