package com.example.decay.web.dto;

import java.util.List;

/**
 * 批量核算响应。即使部分组非法也返回 200，非法组在 items 中以 ok=false 标出。
 */
public record BatchResponse(
        String requestId,
        int total,
        int succeeded,
        int failed,
        List<BatchItemResponse> items
) {
}
