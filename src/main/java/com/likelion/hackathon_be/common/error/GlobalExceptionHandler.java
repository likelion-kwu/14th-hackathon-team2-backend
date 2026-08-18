package com.likelion.hackathon_be.common.error;

import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.ConstraintViolationException;
import java.util.List;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.validation.FieldError;
import org.springframework.web.bind.MethodArgumentNotValidException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;
import org.springframework.web.method.annotation.HandlerMethodValidationException;

@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(BusinessException.class)
    public ResponseEntity<ErrorResponse> handleBusinessException(
            BusinessException exception,
            HttpServletRequest request
    ) {
        ErrorCode errorCode = exception.getErrorCode();
        return ResponseEntity
                .status(errorCode.status())
                .body(ErrorResponse.of(errorCode, exception.getMessage(), exception.getDetails(), traceId(request)));
    }

    @ExceptionHandler(MethodArgumentNotValidException.class)
    public ResponseEntity<ErrorResponse> handleMethodArgumentNotValidException(
            MethodArgumentNotValidException exception,
            HttpServletRequest request
    ) {
        List<ValidationErrorDetail> details = exception.getBindingResult()
                .getFieldErrors()
                .stream()
                .map(this::toValidationErrorDetail)
                .toList();

        return validationErrorResponse(details, request);
    }

    @ExceptionHandler(ConstraintViolationException.class)
    public ResponseEntity<ErrorResponse> handleConstraintViolationException(
            ConstraintViolationException exception,
            HttpServletRequest request
    ) {
        List<ValidationErrorDetail> details = exception.getConstraintViolations()
                .stream()
                .map(violation -> new ValidationErrorDetail(
                        violation.getPropertyPath().toString(),
                        violation.getMessage()
                ))
                .toList();

        return validationErrorResponse(details, request);
    }

    @ExceptionHandler(HandlerMethodValidationException.class)
    public ResponseEntity<ErrorResponse> handleHandlerMethodValidationException(
            HandlerMethodValidationException exception,
            HttpServletRequest request
    ) {
        return validationErrorResponse(List.of(), request);
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ErrorResponse> handleHttpMessageNotReadableException(
            HttpMessageNotReadableException exception,
            HttpServletRequest request
    ) {
        return validationErrorResponse(List.of(), request);
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ErrorResponse> handleException(Exception exception, HttpServletRequest request) {
        return ResponseEntity
                .status(ErrorCode.INTERNAL_SERVER_ERROR.status())
                .body(ErrorResponse.of(
                        ErrorCode.INTERNAL_SERVER_ERROR,
                        ErrorCode.INTERNAL_SERVER_ERROR.defaultMessage(),
                        traceId(request)
                ));
    }

    private ResponseEntity<ErrorResponse> validationErrorResponse(
            List<ValidationErrorDetail> details,
            HttpServletRequest request
    ) {
        return ResponseEntity
                .status(ErrorCode.VALIDATION_ERROR.status())
                .body(ErrorResponse.of(
                        ErrorCode.VALIDATION_ERROR,
                        ErrorCode.VALIDATION_ERROR.defaultMessage(),
                        details,
                        traceId(request)
                ));
    }

    private ValidationErrorDetail toValidationErrorDetail(FieldError fieldError) {
        return new ValidationErrorDetail(fieldError.getField(), fieldError.getDefaultMessage());
    }

    private String traceId(HttpServletRequest request) {
        Object traceId = request.getAttribute("traceId");
        if (traceId != null) {
            return traceId.toString();
        }
        return request.getRequestId();
    }
}
