package com.example.decay.solver;

/**
 * 测试用独立参考解：用定步长 RK4 直接积分衰变链 ODE 方程组，
 * 与解析求解器完全独立，用于交叉验证。
 */
final class Rk4Reference {

    private Rk4Reference() {
    }

    /**
     * @param lambdas 衰变常数（末位可为 0）
     * @param n0      初值
     * @param t       目标时刻
     * @param step    积分步长
     */
    static double[] integrate(double[] lambdas, double[] n0, double t, double step) {
        double[] y = n0.clone();
        double remaining = t;
        double h = step;
        while (remaining > 0) {
            h = Math.min(h, remaining);
            double[] k1 = deriv(lambdas, y);
            double[] y2 = new double[y.length];
            for (int i = 0; i < y.length; i++) {
                y2[i] = y[i] + h / 2 * k1[i];
            }
            double[] k2 = deriv(lambdas, y2);
            double[] y3 = new double[y.length];
            for (int i = 0; i < y.length; i++) {
                y3[i] = y[i] + h / 2 * k2[i];
            }
            double[] k3 = deriv(lambdas, y3);
            double[] y4 = new double[y.length];
            for (int i = 0; i < y.length; i++) {
                y4[i] = y[i] + h * k3[i];
            }
            double[] k4 = deriv(lambdas, y4);
            for (int i = 0; i < y.length; i++) {
                y[i] += h / 6 * (k1[i] + 2 * k2[i] + 2 * k3[i] + k4[i]);
            }
            remaining -= h;
        }
        return y;
    }

    private static double[] deriv(double[] lambdas, double[] y) {
        double[] d = new double[y.length];
        for (int i = 0; i < y.length; i++) {
            double production = i > 0 ? lambdas[i - 1] * y[i - 1] : 0.0;
            d[i] = production - lambdas[i] * y[i];
        }
        return d;
    }
}
