package com.example.decay.web.dto;

import java.util.List;

/**
 * 单条链核算响应。
 */
public record SolveResponse(
        String requestId,
        List<String> nuclides,
        List<Double> decayConstants,
        List<Double> initialNumbers,
        boolean stableEndpoint,
        List<PointResponse> points
) {
}
