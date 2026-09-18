package com.example.decay.web;

import com.example.decay.physics.ConservationException;
import com.example.decay.service.RecordNotFoundException;
import com.example.decay.validation.ValidationException;
import com.example.decay.validation.ValidationIssue;
import com.example.decay.web.dto.ApiError;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.http.converter.HttpMessageNotReadableException;
import org.springframework.web.bind.annotation.ExceptionHandler;
import org.springframework.web.bind.annotation.RestControllerAdvice;

import java.util.List;
import java.util.UUID;

/**
 * 把各类异常统一转换为可读、结构化的 JSON 错误，任何输入都不会让服务直接崩溃。
 */
@RestControllerAdvice
public class GlobalExceptionHandler {

    @ExceptionHandler(ValidationException.class)
    public ResponseEntity<ApiError> handleValidation(ValidationException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                "VALIDATION_FAILED", "输入校验未通过", null, ex.issues()));
    }

    @ExceptionHandler(ConservationException.class)
    public ResponseEntity<ApiError> handleConservation(ConservationException ex) {
        return ResponseEntity.status(HttpStatus.UNPROCESSABLE_ENTITY).body(new ApiError(
                "CONSERVATION_FAILED", ex.getMessage(), null, List.of(new ValidationIssue(
                "CONSERVATION_FAILED", ex.getMessage(), "computation", null, null))));
    }

    @ExceptionHandler(RecordNotFoundException.class)
    public ResponseEntity<ApiError> handleNotFound(RecordNotFoundException ex) {
        return ResponseEntity.status(HttpStatus.NOT_FOUND).body(new ApiError(
                "RECORD_NOT_FOUND", ex.getMessage(), null, List.of()));
    }

    @ExceptionHandler(HttpMessageNotReadableException.class)
    public ResponseEntity<ApiError> handleUnreadable(HttpMessageNotReadableException ex) {
        return ResponseEntity.badRequest().body(new ApiError(
                "MALFORMED_JSON", "请求体不是合法 JSON：" + rootMessage(ex), null, List.of()));
    }

    @ExceptionHandler(Exception.class)
    public ResponseEntity<ApiError> handleOther(Exception ex) {
        String ticket = UUID.randomUUID().toString();
        return ResponseEntity.status(HttpStatus.INTERNAL_SERVER_ERROR).body(new ApiError(
                "INTERNAL_ERROR", "服务内部错误，问题工单 " + ticket, ticket, List.of()));
    }

    private String rootMessage(Throwable ex) {
        Throwable t = ex;
        while (t.getCause() != null && t.getCause() != t) {
            t = t.getCause();
        }
        return t.getMessage() == null ? t.getClass().getSimpleName() : t.getMessage();
    }
}
