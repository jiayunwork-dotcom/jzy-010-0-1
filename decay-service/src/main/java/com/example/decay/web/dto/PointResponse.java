package com.example.decay.web.dto;

import java.util.List;

/**
 * 单个时刻的对外结果结构。
 *
 * @param numbers                 各核素核数（顺序与请求一致）
 * @param activities              各核素活度 λ_i N_i（稳定核为 0）
 * @param parentRemaining         母体剩余核数 N_1(t)
 * @param parentRemainingFraction 母体剩余占其初值的比例
 * @param chainRemainingAtoms     模型内原子总数（含稳定终点核）
 * @param activeAtoms             仍在衰变中的原子数（仅末核稳定时给出）
 * @param stableEndpointAtoms     已迁入稳定终点的核数（仅末核稳定时给出）
 * @param initialTotalAtoms       初始总原子数
 * @param conservationResidual    (模型内原子-初始总数)/初始总数
 */
public record PointResponse(
        double time,
        List<Double> numbers,
        List<Double> activities,
        double parentRemaining,
        double parentRemainingFraction,
        double chainRemainingAtoms,
        Double activeAtoms,
        Double stableEndpointAtoms,
        double initialTotalAtoms,
        double conservationResidual
) {
}
