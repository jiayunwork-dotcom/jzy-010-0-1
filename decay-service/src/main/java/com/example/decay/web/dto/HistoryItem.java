package com.example.decay.web.dto;

import java.time.Instant;

/**
 * 历史记录条目（列表查询不含完整 JSON 正文，详情接口才返回正文）。
 */
public record HistoryItem(
        String id,
        String mode,
        boolean success,
        Integer chainLength,
        Integer timePointCount,
        Integer itemCount,
        String errorSummary,
        Instant createdAt
) {
}
