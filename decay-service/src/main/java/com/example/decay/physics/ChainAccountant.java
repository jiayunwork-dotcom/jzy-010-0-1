package com.example.decay.physics;

import com.example.decay.model.DecayChain;
import com.example.decay.solver.BatemanSolver;

import java.util.ArrayList;
import java.util.List;

/**
 * 活度、母体剩余与原子守恒核算层。
 *
 * <p>在解析求解器之上完成：</p>
 * <ul>
 *   <li>活度 A_i = λ_i N_i（稳定核 λ=0，活度恒为 0）；</li>
 *   <li>母体剩余 N_1(t) 及其相对初值的比例；</li>
 *   <li>链上原子总数（模型中全部核素之和）；稳定终点链额外给出
 *       “仍在衰变中的原子”与“已迁入稳定终点的原子”，满足
 *       activeAtoms + stableEndpointAtoms = initialTotal；</li>
 *   <li>守恒残差，相对超差时抛出 {@link ConservationException}。</li>
 * </ul>
 */
public class ChainAccountant {

    private final BatemanSolver solver;
    private final double nearEqualRelTol;
    private final double conservationRelTol;

    public ChainAccountant(BatemanSolver solver, double nearEqualRelTol, double conservationRelTol) {
        this.solver = solver;
        this.nearEqualRelTol = nearEqualRelTol;
        this.conservationRelTol = conservationRelTol;
    }

    /** 计算一条链在全部时刻上的核算结果。 */
    public List<AccountingPoint> account(DecayChain chain, List<Double> times) {
        int k = chain.length();
        double[] lambdas = new double[k];
        double[] n0 = new double[k];
        for (int i = 0; i < k; i++) {
            lambdas[i] = chain.decayConstants().get(i);
            n0[i] = chain.initialNumbers().get(i);
        }
        double[] ts = new double[times.size()];
        for (int i = 0; i < ts.length; i++) {
            ts[i] = times.get(i);
        }

        double initialTotal = sum(n0);
        boolean stableEndpoint = chain.hasStableEndpoint();
        List<BatemanSolver.PointResult> solved = solver.solve(lambdas, n0, ts, nearEqualRelTol);

        List<AccountingPoint> points = new ArrayList<>(solved.size());
        for (int t = 0; t < solved.size(); t++) {
            double[] raw = solved.get(t).numbers();
            double[] numbers = sanitize(raw, n0);
            double[] activities = new double[k];
            for (int i = 0; i < k; i++) {
                // 稳定核（λ=0）活度恒为零，不依赖任何数值噪声
                activities[i] = lambdas[i] == 0.0 ? 0.0 : lambdas[i] * numbers[i];
            }

            double parentRemaining = numbers[0];
            double parentFraction = n0[0] == 0.0 ? 0.0 : parentRemaining / n0[0];
            double chainRemaining = sum(numbers);

            Double activeAtoms = null;
            Double stableAtoms = null;
            // 原子守恒只对封闭系统（末核稳定）成立：
            // activeAtoms + stableEndpointAtoms 必须与初始总数一致。
            // 全不稳定链是开放系统，原子随末核衰变离开，residual 记为 null。
            Double residual = null;
            if (stableEndpoint) {
                activeAtoms = chainRemaining - numbers[k - 1];
                stableAtoms = numbers[k - 1];
                double r = initialTotal == 0.0
                        ? chainRemaining
                        : (chainRemaining - initialTotal) / initialTotal;
                if (Math.abs(r) > conservationRelTol) {
                    throw new ConservationException(
                            String.format("时刻 t=%s 原子守恒校验失败：链上剩余 %.12g 与初始总数 %.12g 相对偏差 %.3e 超过容差 %.3e",
                                    ts[t], chainRemaining, initialTotal, r,
                                    conservationRelTol));
                }
                residual = r;
            }

            points.add(new AccountingPoint(
                    ts[t], numbers, activities,
                    parentRemaining, parentFraction,
                    chainRemaining, activeAtoms, stableAtoms,
                    initialTotal, residual == null ? Double.NaN : residual));
        }
        return points;
    }

    /** 清除求解器抵消残差产生的微小负值（不超过初始总量的 1e-12 或绝对值 1e-9）。 */
    private double[] sanitize(double[] raw, double[] n0) {
        double scale = Math.max(1.0, sum(n0));
        double[] out = new double[raw.length];
        for (int i = 0; i < raw.length; i++) {
            double v = raw[i];
            if (v < 0.0 && v > -1e-9 * scale) {
                v = 0.0;
            }
            out[i] = v;
        }
        return out;
    }

    private static double sum(double[] values) {
        double s = 0.0;
        for (double v : values) {
            s += v;
        }
        return s;
    }
}
