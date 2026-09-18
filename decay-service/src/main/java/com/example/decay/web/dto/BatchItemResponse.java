package com.example.decay.web.dto;

import com.example.decay.validation.ValidationIssue;

import java.util.List;

/**
 * 批量请求中单组的结果：成功时返回 {@link SolveResponse}，失败时返回结构化问题。
 */
public record BatchItemResponse(
        int index,
        boolean ok,
        SolveResponse result,
        List<ValidationIssue> issues
) {
}
