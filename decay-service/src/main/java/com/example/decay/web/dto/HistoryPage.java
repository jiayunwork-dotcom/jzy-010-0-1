package com.example.decay.web.dto;

import java.util.List;

/**
 * 历史分页结果。
 */
public record HistoryPage(
        int page,
        int size,
        long total,
        List<HistoryItem> items
) {
}
