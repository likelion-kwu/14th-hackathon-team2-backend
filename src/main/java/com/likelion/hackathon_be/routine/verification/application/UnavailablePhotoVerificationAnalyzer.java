package com.likelion.hackathon_be.routine.verification.application;

public class UnavailablePhotoVerificationAnalyzer implements PhotoVerificationAnalyzer {

    @Override
    public PhotoVerificationAnalysis analyze(PhotoVerificationInput input) {
        throw new PhotoVerificationAnalyzerException("Photo verification analyzer is not configured.");
    }
}
