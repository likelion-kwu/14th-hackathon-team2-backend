package com.likelion.hackathon_be.routine.photo;

public interface PhotoVerificationAnalyzer {
    PhotoVerificationDecision analyze(PhotoVerificationInput input);
}
