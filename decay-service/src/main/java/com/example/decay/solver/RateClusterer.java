package com.example.decay.solver;

import java.util.ArrayList;
import java.util.Arrays;
import java.util.List;

/**
 * 将彼此接近（相对差小于容差）的正衰变常数归并为同一个锚点常数。
 *
 * <p>这使 Bateman 公式中 {@code 1/(λ_i-λ_j)} 的分母不会出现“接近零”的情况：
 * 同簇常数按重根极限公式（含 {@code t^q} 项）精确处理，不同簇之间至少相差
 * 容差倍锚点值。零（仅末核稳定时出现）不参与归并。</p>
 *
 * <p>归并采用排序后“相邻连通”并集：只要两个排序相邻的常数相对差不超过容差，
 * 就把它们并入同一簇，因而 {@code a、a(1+ε)、a(1+2ε)} 这类链式接近的值
 * 也会被整体归并。</p>
 */
public final class RateClusterer {

    private RateClusterer() {
    }

    /**
     * @param rates  原始衰变常数（仅末位允许为 0）
     * @param relTol 归并相对容差
     * @return 与输入等长、每个位置替换为所在簇锚点（簇内最小值）的新数组
     */
    public static double[] cluster(double[] rates, double relTol) {
        int n = rates.length;
        int[] order = new int[n];
        int positiveCount = 0;
        for (int i = 0; i < n; i++) {
            if (rates[i] != 0.0) {
                order[positiveCount++] = i;
            }
        }
        // 对正常数按值做索引排序（插入排序，链长上限 12，代价可忽略）
        for (int a = 1; a < positiveCount; a++) {
            int key = order[a];
            int b = a - 1;
            while (b >= 0 && rates[order[b]] > rates[key]) {
                order[b + 1] = order[b];
                b--;
            }
            order[b + 1] = key;
        }

        double[] snapped = Arrays.copyOf(rates, n);
        int p = 0;
        while (p < positiveCount) {
            int anchorPos = p;
            double anchor = rates[order[p]];
            int q = p + 1;
            // 相邻排序值相对差不超过阈值即连通并入本簇
            while (q < positiveCount) {
                double prev = rates[order[q - 1]];
                double cur = rates[order[q]];
                if ((cur - prev) <= relTol * prev) {
                    q++;
                } else {
                    break;
                }
            }
            for (int r = anchorPos; r < q; r++) {
                snapped[order[r]] = anchor;
            }
            p = q;
        }
        return snapped;
    }

    /** 由归并后的常数数组提取区间内的去重锚点（保持链上首次出现顺序）。 */
    public static List<Double> distinctAnchors(double[] snapped, int from, int to) {
        List<Double> anchors = new ArrayList<>();
        for (int i = from; i <= to; i++) {
            double v = snapped[i];
            if (!anchors.contains(v)) {
                anchors.add(v);
            }
        }
        return anchors;
    }
}
