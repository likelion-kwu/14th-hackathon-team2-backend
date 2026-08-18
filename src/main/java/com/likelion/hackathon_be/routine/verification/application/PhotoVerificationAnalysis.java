package com.likelion.hackathon_be.routine.verification.application;

public record PhotoVerificationAnalysis(
        boolean decidable,
        boolean objectDetected,
        boolean gestureDetected,
        String reasonCode
) {

    public PhotoVerificationAnalysis(boolean decidable, boolean objectDetected, boolean gestureDetected) {
        this(decidable, objectDetected, gestureDetected, deriveReason(decidable, objectDetected, gestureDetected));
    }

    public static PhotoVerificationAnalysis success() {
        return new PhotoVerificationAnalysis(true, true, true, "MATCHED");
    }

    private static String deriveReason(boolean decidable, boolean objectDetected, boolean gestureDetected) {
        if (!decidable) {
            return "IMAGE_UNCLEAR";
        }
        if (objectDetected && gestureDetected) {
            return "MATCHED";
        }
        if (!objectDetected && !gestureDetected) {
            return "BOTH_MISSING";
        }
        return objectDetected ? "GESTURE_MISSING" : "OBJECT_MISSING";
    }
}
