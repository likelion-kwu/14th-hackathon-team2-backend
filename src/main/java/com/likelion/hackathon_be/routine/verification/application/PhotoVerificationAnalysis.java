package com.likelion.hackathon_be.routine.verification.application;

public record PhotoVerificationAnalysis(
        boolean decidable,
        boolean objectDetected,
        boolean gestureDetected
) {

    public static PhotoVerificationAnalysis success() {
        return new PhotoVerificationAnalysis(true, true, true);
    }
}
