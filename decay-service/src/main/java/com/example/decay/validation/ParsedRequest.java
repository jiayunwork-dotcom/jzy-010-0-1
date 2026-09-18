package com.example.decay.validation;

import com.example.decay.model.DecayChain;

import java.util.List;

/**
 * 解析并通过校验后的单个核算请求。
 */
public record ParsedRequest(DecayChain chain, List<Double> times) {
}
