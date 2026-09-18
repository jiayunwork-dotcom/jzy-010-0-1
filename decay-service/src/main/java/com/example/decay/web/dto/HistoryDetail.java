package com.example.decay.web.dto;

import com.fasterxml.jackson.databind.JsonNode;

import java.time.Instant;

/**
 * 单条历史记录详情，含原始请求与结果 JSON。
 */
public record HistoryDetail(
        String id,
        String mode,
        boolean success,
        Integer chainLength,
        Integer timePointCount,
        Integer itemCount,
        String errorSummary,
        Instant createdAt,
        JsonNode request,
        JsonNode response
) {
}
