package com.example.decay.model;

import java.util.List;

/**
 * 一条经过校验的线性核素链。
 *
 * <p>{@code decayConstants[i]} 为第 i 个核素（序号从 1 开始）的衰变常数，允许仅末核为 0
 * （稳定终点）；{@code initialNumbers[i]} 为初始核数；{@code labels} 为可选核素名称。</p>
 */
public record DecayChain(List<Double> decayConstants,
                        List<Double> initialNumbers,
                        List<String> labels) {

    public int length() {
        return decayConstants.size();
    }

    /** 末核是否为稳定终点。 */
    public boolean hasStableEndpoint() {
        return decayConstants.get(decayConstants.size() - 1) == 0.0;
    }
}
