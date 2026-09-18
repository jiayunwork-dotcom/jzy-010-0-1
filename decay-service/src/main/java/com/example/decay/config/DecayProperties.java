package com.example.decay.config;

import org.springframework.boot.context.properties.ConfigurationProperties;

/**
 * 服务可调数值与限制配置，均可通过环境变量覆盖：
 * DECAY_MAX-CHAIN-LENGTH、DECAY_NEAR-EQUAL-REL-TOL 等（Spring Boot 松散绑定）。
 */
@ConfigurationProperties(prefix = "decay")
public record DecayProperties(
        int maxChainLength,
        double nearEqualRelTol,
        int maxGridSteps,
        int maxTimePoints,
        int maxBatchItems,
        double conservationRelTol
) {
    public DecayProperties {
        if (maxChainLength <= 0) {
            throw new IllegalArgumentException("decay.max-chain-length 必须为正数");
        }
        if (!(nearEqualRelTol > 0 && nearEqualRelTol < 0.1)) {
            throw new IllegalArgumentException("decay.near-equal-rel-tol 必须在 (0, 0.1) 内");
        }
        if (maxGridSteps <= 0 || maxTimePoints <= 0 || maxBatchItems <= 0) {
            throw new IllegalArgumentException("decay 的步数/时刻数/批量上限必须为正数");
        }
        if (!(conservationRelTol > 0 && conservationRelTol < 1e-3)) {
            throw new IllegalArgumentException("decay.conservation-rel-tol 必须在 (0, 1e-3) 内");
        }
    }
}
