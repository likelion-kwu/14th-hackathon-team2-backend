package com.likelion.hackathon_be.routine.photo;

public record PhotoVerificationDecision(
        boolean decidable,
        boolean objectMatched,
        boolean gestureMatched,
        ReasonCode reasonCode
) {
    public enum ReasonCode {
        MATCHED,
        OBJECT_MISSING,
        GESTURE_MISSING,
        BOTH_MISSING,
        IMAGE_UNCLEAR,
        MODEL_REFUSED
    }

    public boolean passed() {
        return decidable && objectMatched && gestureMatched;
    }
}
