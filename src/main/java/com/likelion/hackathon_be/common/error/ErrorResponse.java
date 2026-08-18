package com.likelion.hackathon_be.common.error;

import com.fasterxml.jackson.annotation.JsonInclude;
import java.util.List;

@JsonInclude(JsonInclude.Include.NON_EMPTY)
public record ErrorResponse(
        String code,
        String message,
        List<ValidationErrorDetail> details,
        String traceId
) {

    public static ErrorResponse of(ErrorCode errorCode, String message, String traceId) {
        return new ErrorResponse(errorCode.name(), message, List.of(), traceId);
    }

    public static ErrorResponse of(
            ErrorCode errorCode,
            String message,
            List<ValidationErrorDetail> details,
            String traceId
    ) {
        return new ErrorResponse(errorCode.name(), message, details, traceId);
    }
}
