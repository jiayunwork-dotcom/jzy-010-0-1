package com.example.decay.physics;

/**
 * 单个时刻的核算结果：各核核数、活度，以及母体剩余、链上原子、稳定终点迁入量等汇总。
 */
public record AccountingPoint(
        double time,
        double[] numbers,
        double[] activities,
        double parentRemaining,
        double parentRemainingFraction,
        double chainRemainingAtoms,
        Double activeAtoms,
        Double stableEndpointAtoms,
        double initialTotalAtoms,
        Double conservationResidual
) {
}
