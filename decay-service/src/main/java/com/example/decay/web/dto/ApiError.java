package com.example.decay.web.dto;

import com.example.decay.validation.ValidationIssue;

import java.util.List;

/**
 * 统一结构化错误响应体。
 */
public record ApiError(
        String error,
        String message,
        String requestId,
        List<ValidationIssue> issues
) {
}
