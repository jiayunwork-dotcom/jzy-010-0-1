package com.example.decay.web.dto;

/**
 * 服务限制与数值容差配置回显。
 */
public record ConfigResponse(
        int maxChainLength,
        double nearEqualRelTol,
        int maxGridSteps,
        int maxTimePoints,
        int maxBatchItems,
        double conservationRelTol,
        int minChainLength
) {
}
