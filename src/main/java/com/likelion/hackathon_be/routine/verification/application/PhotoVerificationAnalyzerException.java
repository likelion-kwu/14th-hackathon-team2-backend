package com.likelion.hackathon_be.routine.verification.application;

public class PhotoVerificationAnalyzerException extends RuntimeException {

    public PhotoVerificationAnalyzerException(String message) {
        super(message);
    }

    public PhotoVerificationAnalyzerException(String message, Throwable cause) {
        super(message, cause);
    }
}
