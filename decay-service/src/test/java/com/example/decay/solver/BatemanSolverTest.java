package com.example.decay.solver;

import org.junit.jupiter.api.Test;

import java.util.Arrays;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertArrayEquals;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 解析求解器数值行为测试，并以独立 RK4 积分交叉验证。
 */
class BatemanSolverTest {

    private final BatemanSolver solver = new BatemanSolver();
    private static final double TOL = 1e-8;

    private static double[] solveAt(BatemanSolver solver, double[] lam, double[] n0, double t) {
        return solver.solve(lam, n0, new double[]{t}, TOL).get(0).numbers();
    }

    @Test
    void zeroTimeReturnsInitialValuesExactly() {
        double[] lam = {0.1, 0.5, 0.0};
        double[] n0 = {1000, 200, 30};
        double[] n = solveAt(solver, lam, n0, 0.0);
        assertArrayEquals(n0, n, 0.0, "零时刻必须精确回到初值");
    }

    @Test
    void distinctRatesAgreeWithIndependentRk4() {
        double[] lam = {0.1, 0.5, 2.0};
        double[] n0 = {1000, 0, 0};
        for (double t : new double[]{0.5, 1, 3, 7, 20}) {
            double[] analytic = solveAt(solver, lam, n0, t);
            double[] numeric = Rk4Reference.integrate(lam, n0, t, 1e-4);
            for (int i = 0; i < 3; i++) {
                assertEquals(numeric[i], analytic[i], 1e-6 * Math.max(1, n0[0]),
                        "t=" + t + " 第 " + i + " 核与 RK4 不符");
            }
        }
    }

    @Test
    void multipleInitialPopulationsAgreeWithRk4() {
        double[] lam = {0.1, 0.5, 0.0};
        double[] n0 = {333, 222, 111};
        double[] analytic = solveAt(solver, lam, n0, 4.0);
        double[] numeric = Rk4Reference.integrate(lam, n0, 4.0, 1e-4);
        for (int i = 0; i < 3; i++) {
            assertEquals(numeric[i], analytic[i], 1e-6 * 1000);
        }
    }

    @Test
    void exactlyEqualRatesRemainFiniteAndMatchRepeatedRootFormula() {
        double[] lam = {0.1, 0.1, 0.0};
        double[] n0 = {1000, 0, 0};
        for (double t : new double[]{1, 5, 20}) {
            double[] n = solveAt(solver, lam, n0, t);
            assertTrue(Arrays.stream(n).allMatch(Double::isFinite),
                    "重根情形所有核数必须有限");
            // N2 = N0 λ t e^{-λt}（等常数重根公式）
            double expected = n0[0] * 0.1 * t * Math.exp(-0.1 * t);
            assertEquals(expected, n[1], 1e-9 * n0[0], "t=" + t);
            // RK4 交叉验证（含稳定末核守恒）
            double[] numeric = Rk4Reference.integrate(lam, n0, t, 1e-4);
            for (int i = 0; i < 3; i++) {
                assertEquals(numeric[i], n[i], 1e-6 * n0[0]);
            }
        }
    }

    @Test
    void nearlyEqualAdjacentRatesRemainFiniteAndCloseToRk4() {
        double[] lam = {0.1, 0.1 + 5e-9, 0.0};
        double[] n0 = {1000, 0, 0};
        for (double t : new double[]{1, 5, 20}) {
            double[] n = solveAt(solver, lam, n0, t);
            assertTrue(Arrays.stream(n).allMatch(Double::isFinite));
            assertFalse(Arrays.stream(n).anyMatch(v -> Math.abs(v) > 1e6 * n0[0]),
                    "近等常数不得数值爆炸");
            double[] numeric = Rk4Reference.integrate(lam, n0, t, 1e-4);
            for (int i = 0; i < 3; i++) {
                assertEquals(numeric[i], n[i], 1e-5 * n0[0], "t=" + t);
            }
        }
    }

    @Test
    void stableEndpointConservesTotalAtomsAtAllTimes() {
        double[] lam = {0.1, 0.5, 0.0};
        double[] n0 = {1000, 0, 0};
        List<BatemanSolver.PointResult> results =
                solver.solve(lam, n0, new double[]{0, 1, 10, 100, 1000}, TOL);
        for (var r : results) {
            double sum = Arrays.stream(r.numbers()).sum();
            assertEquals(1000, sum, 1e-9, "链上(含稳定末核)总原子必须守恒");
        }
    }

    @Test
    void allUnstableChainsDecayToZeroAtLongTime() {
        double[] lam = {0.1, 0.5, 2.0};
        double[] n0 = {1000, 0, 0};
        double[] n = solveAt(solver, lam, n0, 2000);
        for (int i = 0; i < n.length; i++) {
            assertEquals(0.0, n[i], 1e-12, "全不稳定链第 " + i + " 核长期应趋于零");
        }
    }

    @Test
    void stableEndpointAccumulatesAllAtomsAtLongTime() {
        double[] lam = {0.1, 0.5, 0.0};
        double[] n0 = {1000, 0, 0};
        double[] n = solveAt(solver, lam, n0, 2000);
        assertEquals(1000, n[2], 1e-6, "稳定末核长期应收纳全部初始原子");
        assertEquals(0, n[0], 1e-6);
        assertEquals(0, n[1], 1e-6);
    }

    @Test
    void resultsAreLinearInInitialNumbers() {
        double[] lam = {0.1, 0.5, 0.0};
        double[] n0 = {1000, 100, 5};
        double[] n0x2 = {2000, 200, 10};
        double t = 7.5;
        double[] a = solveAt(solver, lam, n0, t);
        double[] b = solveAt(solver, lam, n0x2, t);
        for (int i = 0; i < a.length; i++) {
            assertEquals(2 * a[i], b[i], Math.ulp(b[i]) * 8, "初值加倍核数必须加倍");
        }
    }

    @Test
    void shortLivedDaughterApproachesActivityEquilibrium() {
        // 子体衰变常数远大于母体：N2 ≈ λ1 N1 / λ2，A2 ≈ A1
        double lambda1 = 0.01;
        double lambda2 = 1000.0;
        double[] lam = {lambda1, lambda2, 0.0};
        double n1Init = 1e6;
        double[] n0 = {n1Init, 0, 0};
        // t 足够大（子体多个半衰期）又足够小（母体几乎不变）
        double t = 0.05;
        double[] n = solveAt(solver, lam, n0, t);
        double predictedDaughter = lambda1 * n[0] / lambda2;
        assertEquals(predictedDaughter, n[1], 0.05 * predictedDaughter,
                "短寿命子体核数应逼近 λ1·N1/λ2（5% 内）");
        assertEquals(lambda1 * n[0], lambda2 * n[1], 0.05 * lambda1 * n[0],
                "子体活度应逼近母体活度（母子活度平衡）");
    }

    @Test
    void parentNumberIsPureExponentialRegardlessOfDaughterRates() {
        // 无论子体常数如何设置，N1 只随自身指数下降
        double[][] daughterChains = {
                {0.3, 0.0}, {100.0, 0.0}, {0.3, 3.0, 0.0}, {1e6, 1e-3, 0.0}
        };
        double n1Init = 5000;
        for (double[] tail : daughterChains) {
            double[] lam = new double[tail.length + 1];
            double[] n0 = new double[tail.length + 1];
            lam[0] = 0.2;
            n0[0] = n1Init;
            System.arraycopy(tail, 0, lam, 1, tail.length);
            for (double t : new double[]{0.1, 2, 9}) {
                double[] n = solveAt(solver, lam, n0, t);
                assertEquals(n1Init * Math.exp(-0.2 * t), n[0], 1e-10 * n1Init,
                        "母体核数必须只按自身指数衰减");
            }
        }
    }

    @Test
    void initialDerivativeOfTotalForUnstableChain() {
        // 物理恒等式：全不稳定链 dΣN/dt = -λ_k·N_k（产生项在链内成对抵消）；
        // t=0 仅母体有初值时即为 -λ1·N1(0)。
        // 不能用有限差分求总和——799.9704+0.0296 在 double 中会舍入回 800，
        // 因此这里直接对解析解的“末核活度 = 总核数下降率”做开端验证。
        double[] lam = {0.37, 1.2, 4.0};
        double n1 = 800;
        double[] n0 = {n1, 0, 0};
        // 开端验证：母体衰变率就是总核数下降率（t=0 时无任何子体）。
        // 用独立 RK4 单步的母体变化核对（只看母体，避免总和相加抵消）。
        double h = 1e-7;
        double[] analytic = solveAt(solver, lam, n0, h);
        double analyticParentSlope = (analytic[0] - n1) / h;
        double[] numeric = Rk4Reference.integrate(lam, n0, h, h);
        double numericParentSlope = (numeric[0] - n1) / h;
        // RK4 单步局部误差 O(h^4)，容差取 1e-6 相对
        assertEquals(-0.37 * n1, numericParentSlope, 1e-6 * 0.37 * n1,
                "开端总核数下降率必须等于 -λ1·N1(0)（RK4 参考）");
        assertEquals(numericParentSlope, analyticParentSlope,
                1e-6 * Math.abs(numericParentSlope),
                "解析解开端母体斜率与参考解一致");
    }

    @Test
    void fourNuclideChainAgreesWithRk4() {
        double[] lam = {0.01, 0.1, 1.0, 0.0};
        double[] n0 = {1e6, 0, 0, 0};
        for (double t : new double[]{0.1, 1, 10, 500}) {
            double[] n = solveAt(solver, lam, n0, t);
            double[] numeric = Rk4Reference.integrate(lam, n0, t, 1e-3);
            for (int i = 0; i < 4; i++) {
                assertEquals(numeric[i], n[i], 1e-5 * 1e6, "t=" + t);
            }
            assertEquals(1e6, Arrays.stream(n).sum(), 1e-6);
        }
    }

    @Test
    void twelveNuclideChainRemainsFiniteAndConservative() {
        double[] lam = {0.5, 0.5 + 1e-9, 0.6, 0.6 + 5e-9, 0.7, 1.0, 1.0 + 2e-9,
                2.0, 3.0, 4.0, 5.0, 0.0};
        double[] n0 = new double[12];
        n0[0] = 42;
        double[] n = solveAt(solver, lam, n0, 13.0);
        assertTrue(Arrays.stream(n).allMatch(Double::isFinite));
        assertEquals(42, Arrays.stream(n).sum(), 1e-7 * 42);
    }
}
