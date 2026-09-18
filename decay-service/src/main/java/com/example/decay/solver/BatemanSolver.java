package com.example.decay.solver;

import java.util.ArrayList;
import java.util.List;

/**
 * 单向线性衰变链的解析求解器（Bateman 广义解，支持重根极限）。
 *
 * <p>对源核 s（初值 N_s(0)）到目标核 i（s ≤ i），拉普拉斯域传递函数为</p>
 * <pre>
 * X_{s→i}(z) = N_s(0) · C / ∏_{j=s}^{i} (z + λ_j),  C = ∏_{j=s}^{i-1} λ_j
 * </pre>
 * 把 λ_s...λ_i 中彼此接近的常数归并为同一锚点 a（重数 m_a），部分分式展开后：
 * <pre>
 * N_{s→i}(t) = N_s(0) · C · Σ_a e^{-at} Σ_{q=0}^{m_a-1}
 *                  g_a(q) · t^{m_a-1-q} / (m_a-1-q)!
 * </pre>
 * 其中 g_a(q) 是
 * <pre>
 * G_a(w) = 1 / ∏_{b≠a} (w + (b-a))^{m_b}
 * </pre>
 * 在 w=0 处的带符号 Taylor 系数（恒有 g_a(0) 符号 = 1/∏_{b<a}(a-b)^{m_b}…）。
 * 所有指数项按对数空间、带符号 log-sum-exp 累加，相邻常数相等或仅差相对容差
 * 以内时也不会除零、变号或数值爆炸。
 */
public class BatemanSolver {

    private static final double LOG_CLAMP = 700.0;

    /** 每个时刻每个核的计算结果。 */
    public record PointResult(double[] numbers) {
    }

    /**
     * 求解全部时刻的核数矩阵。
     *
     * @param lambdas 衰变常数（归并在内部完成；仅末位可为 0）
     * @param n0      初始核数
     * @param times   非负时刻
     * @param relTol  近等常数归并容差
     * @return 长度与 times 相同，每项为该时刻长度 k 的核数数组
     */
    public List<PointResult> solve(double[] lambdas, double[] n0, double[] times, double relTol) {
        int k = lambdas.length;
        double[] snapped = RateClusterer.cluster(lambdas, relTol);

        // 预计算每个 (s, i) 的源核传递结构
        Transfer[][] table = new Transfer[k][k];
        for (int s = 0; s < k; s++) {
            for (int i = s; i < k; i++) {
                table[s][i] = buildTransfer(snapped, s, i);
            }
        }

        List<PointResult> results = new ArrayList<>(times.length);
        for (double t : times) {
            double[] n = new double[k];
            if (t == 0.0) {
                // 零时刻精确回到初值，避免任何数值残差
                System.arraycopy(n0, 0, n, 0, k);
            } else {
                for (int s = 0; s < k; s++) {
                    if (n0[s] == 0.0) {
                        continue;
                    }
                    for (int i = s; i < k; i++) {
                        n[i] += n0[s] * table[s][i].evaluate(t);
                    }
                }
            }
            results.add(new PointResult(n));
        }
        return results;
    }

    /** 单个源核 s 到目标核 i 的传递函数（t 的带符号加权指数和）。 */
    private static final class Transfer {
        /** 极点锚点（λ_s..λ_i 归并后的去重值，含目标核自身常数与可能的 0）。 */
        private final double[] anchors;
        /** 每个锚点在极点集合 λ_s..λ_i 中的重数（≥1）。 */
        private final int[] mult;
        /** 前导常数 log|C|，C = ∏_{j=s}^{i-1} λ_j（s==i 时为 1）。 */
        private final double logPrefix;
        /** g_a(q) 的计算结果缓存（每个锚点一组，带符号）。 */
        private final double[][] gCache;

        private Transfer(double[] anchors, int[] mult, double logPrefix) {
            this.anchors = anchors;
            this.mult = mult;
            this.logPrefix = logPrefix;
            this.gCache = new double[anchors.length][];
        }

        double evaluate(double t) {
            int aCount = anchors.length;

            int termCount = 0;
            for (int ia = 0; ia < aCount; ia++) {
                termCount += mult[ia];
            }
            double[] logs = new double[termCount];
            int[] signs = new int[termCount];
            double maxLog = Double.NEGATIVE_INFINITY;
            int idx = 0;
            for (int ia = 0; ia < aCount; ia++) {
                int m = mult[ia];
                double a = anchors[ia];
                double[] g = coefficients(ia);
                // q=0..m-1 对应 t 的幂次 m-1-q
                for (int q = 0; q < m; q++) {
                    int power = m - 1 - q;
                    double gq = g[q];
                    if (gq == 0.0) {
                        logs[idx] = Double.NEGATIVE_INFINITY;
                        signs[idx] = 0;
                    } else {
                        // log | C · g_a(q) · t^power / power! · e^{-at} |
                        logs[idx] = logPrefix
                                + Math.log(Math.abs(gq))
                                + power * Math.log(t)
                                - logFactorial(power)
                                - a * t;
                        signs[idx] = gq > 0 ? 1 : -1;
                        if (logs[idx] > maxLog) {
                            maxLog = logs[idx];
                        }
                    }
                    idx++;
                }
            }
            if (maxLog == Double.NEGATIVE_INFINITY) {
                return 0.0;
            }
            double sum = 0.0;
            for (int j = 0; j < termCount; j++) {
                if (signs[j] == 0) {
                    continue;
                }
                double delta = Math.min(LOG_CLAMP, logs[j] - maxLog);
                sum += signs[j] * Math.exp(delta);
            }
            double value = Math.exp(Math.min(LOG_CLAMP, maxLog)) * sum;
            // 滤掉抵消后理论为零、实际出现的微小负噪声
            return value < 0.0 && value > -1e-12 ? 0.0 : value;
        }

        private double[] coefficients(int ia) {
            if (gCache[ia] == null) {
                gCache[ia] = taylorCoefficients(anchors, mult, ia);
            }
            return gCache[ia];
        }
    }

    private static Transfer buildTransfer(double[] snapped, int s, int i) {
        List<Double> anchorList = RateClusterer.distinctAnchors(snapped, s, i);
        double[] anchors = new double[anchorList.size()];
        int[] mult = new int[anchors.length];
        for (int r = 0; r < anchors.length; r++) {
            anchors[r] = anchorList.get(r);
        }
        // 极点集合为 λ_s..λ_i（含目标核自身常数；0 也作为 s=0 平面极点）
        for (int j = s; j <= i; j++) {
            mult[indexOf(anchors, snapped[j])]++;
        }

        double logPrefix = 0.0;
        for (int j = s; j < i; j++) {
            logPrefix += Math.log(snapped[j]);
        }
        return new Transfer(anchors, mult, logPrefix);
    }

    /**
     * 计算 G_a(w)=1/∏_{b≠a}(w+(b-a))^{m_b} 在 w=0 处带符号 Taylor 系数
     * g_a(q)（q=0..m_a-1）。
     *
     * <p>令 G_a(w)=exp(H_a(w))，由 log(1+w/d) 展开有
     * H_0 = -Σ_{b≠a} m_b log(b-a)（带符号 log），
     * H_r = (-1)^r (r-1)! · Σ_{b≠a} m_b/(b-a)^r（r≥1），
     * 再按指数生成函数递推：g_0=exp(H_0)，q·g_q = Σ_{r=1}^{q} H_r · g_{q-r}。
     * 锚点互异，故 (b-a) 永不为零。</p>
     */
    private static double[] taylorCoefficients(double[] anchors, int[] mult, int ia) {
        double a = anchors[ia];
        int qMax = mult[ia] - 1;
        double[] h = new double[qMax + 1];

        // H(0)：log 取绝对值，符号由 (b-a)<0 的负因子个数决定
        double h0 = 0.0;
        int h0Sign = 1;
        for (int ib = 0; ib < anchors.length; ib++) {
            if (ib == ia) {
                continue;
            }
            double d = anchors[ib] - a; // = b-a
            h0 -= mult[ib] * Math.log(Math.abs(d));
            if (d < 0 && (mult[ib] % 2 == 1)) {
                h0Sign = -h0Sign;
            }
        }
        for (int r = 1; r <= qMax; r++) {
            double sum = 0.0;
            for (int ib = 0; ib < anchors.length; ib++) {
                if (ib == ia) {
                    continue;
                }
                sum += mult[ib] / Math.pow(anchors[ib] - a, r);
            }
            h[r] = (r % 2 == 1 ? -1.0 : 1.0) * factorial(r - 1) * sum;
        }

        double[] g = new double[qMax + 1];
        g[0] = h0Sign * Math.exp(Math.min(LOG_CLAMP, Math.max(-LOG_CLAMP, h0)));
        for (int q = 1; q <= qMax; q++) {
            double acc = 0.0;
            for (int r = 1; r <= q; r++) {
                acc += h[r] * g[q - r];
            }
            g[q] = acc / q;
        }
        return g;
    }

    private static int indexOf(double[] arr, double target) {
        for (int i = 0; i < arr.length; i++) {
            if (arr[i] == target) {
                return i;
            }
        }
        throw new IllegalStateException("锚点未找到: " + target);
    }

    private static double factorial(int n) {
        double f = 1.0;
        for (int i = 2; i <= n; i++) {
            f *= i;
        }
        return f;
    }

    private static double logFactorial(int n) {
        double s = 0.0;
        for (int i = 2; i <= n; i++) {
            s += Math.log(i);
        }
        return s;
    }
}
