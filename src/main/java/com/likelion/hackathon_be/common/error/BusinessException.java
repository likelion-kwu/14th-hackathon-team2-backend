package com.likelion.hackathon_be.common.error;

import java.util.List;

public class BusinessException extends RuntimeException {

    private final ErrorCode errorCode;
    private final List<ValidationErrorDetail> details;

    public BusinessException(ErrorCode errorCode) {
        this(errorCode, errorCode.defaultMessage(), List.of());
    }

    public BusinessException(ErrorCode errorCode, String message) {
        this(errorCode, message, List.of());
    }

    public BusinessException(ErrorCode errorCode, String message, List<ValidationErrorDetail> details) {
        super(message);
        this.errorCode = errorCode;
        this.details = List.copyOf(details);
    }

    public ErrorCode getErrorCode() {
        return errorCode;
    }

    public List<ValidationErrorDetail> getDetails() {
        return details;
    }
}
