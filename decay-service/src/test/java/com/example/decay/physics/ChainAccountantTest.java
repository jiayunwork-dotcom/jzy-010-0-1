package com.example.decay.physics;

import com.example.decay.model.DecayChain;
import com.example.decay.solver.BatemanSolver;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class ChainAccountantTest {

    private final ChainAccountant accountant =
            new ChainAccountant(new BatemanSolver(), 1e-8, 1e-6);

    private DecayChain chain(List<Double> lam, List<Double> n0) {
        return new DecayChain(lam, n0, null);
    }

    @Test
    void stableEndpointReportsActiveAndStableAndConserves() {
        var points = accountant.account(
                chain(List.of(0.1, 0.5, 0.0), List.of(1000.0, 0.0, 0.0)),
                List.of(0.0, 1.0, 50.0, 500.0));
        for (AccountingPoint p : points) {
            assertEquals(1000, p.initialTotalAtoms(), 1e-9);
            assertEquals(1000, p.chainRemainingAtoms(), 1e-7);
            assertEquals(p.chainRemainingAtoms() - p.stableEndpointAtoms(),
                    p.activeAtoms(), 1e-9);
            assertEquals(p.chainRemainingAtoms(),
                    p.activeAtoms() + p.stableEndpointAtoms(), 1e-9);
        }
        AccountingPoint last = points.get(points.size() - 1);
        assertEquals(1000, last.stableEndpointAtoms(), 1e-4, "长期稳定核趋于初始总数");
        assertEquals(0, last.activeAtoms(), 1e-4, "长期衰变中核趋于零");
    }

    @Test
    void allUnstableTotalsDecreaseStrictly() {
        var points = accountant.account(
                chain(List.of(0.1, 0.5, 2.0), List.of(1000.0, 0.0, 0.0)),
                List.of(0.0, 0.5, 1.0, 2.0, 5.0, 2000.0));
        for (int i = 1; i < points.size(); i++) {
            assertTrue(points.get(i).chainRemainingAtoms()
                            < points.get(i - 1).chainRemainingAtoms(),
                    "全不稳定时链上总核数必须严格下降");
        }
        AccountingPoint last = points.get(points.size() - 1);
        for (double n : last.numbers()) {
            assertEquals(0, n, 1e-9, "全不稳定长期全部趋于零");
        }
    }

    @Test
    void stableNuclideActivityIsAlwaysZero() {
        var points = accountant.account(
                chain(List.of(0.1, 0.0), List.of(10.0, 0.0)),
                List.of(0.0, 1.0, 10.0));
        for (AccountingPoint p : points) {
            assertEquals(0.0, p.activities()[1], 0.0, "稳定核活度必须精确为零");
        }
    }

    @Test
    void activityEqualsLambdaTimesNumber() {
        var points = accountant.account(
                chain(List.of(0.13, 0.7, 0.0), List.of(500.0, 100.0, 0.0)),
                List.of(0.0, 1.5, 4.0));
        for (AccountingPoint p : points) {
            assertEquals(0.13 * p.numbers()[0], p.activities()[0], 1e-9);
            assertEquals(0.7 * p.numbers()[1], p.activities()[1], 1e-9);
        }
    }

    @Test
    void parentRemainingReportedAndStartsAtInitialFractionOne() {
        var points = accountant.account(
                chain(List.of(0.2, 0.0), List.of(300.0, 0.0)),
                List.of(0.0, 1.0, 10.0));
        assertEquals(300, points.get(0).parentRemaining(), 0);
        assertEquals(1.0, points.get(0).parentRemainingFraction(), 0);
        for (AccountingPoint p : points) {
            assertEquals(300 * Math.exp(-0.2 * p.time()), p.parentRemaining(), 1e-9);
        }
    }

    @Test
    void noStableEndpointFieldsWhenAllUnstable() {
        var points = accountant.account(
                chain(List.of(0.1, 0.5), List.of(10.0, 0.0)), List.of(1.0));
        assertNull(points.get(0).stableEndpointAtoms());
        assertNull(points.get(0).activeAtoms());
    }

    @Test
    void doublingInitialValuesDoublesNumbersAndActivities() {
        var a = accountant.account(
                chain(List.of(0.1, 0.5, 0.0), List.of(100.0, 20.0, 5.0)),
                List.of(3.0)).get(0);
        var b = accountant.account(
                chain(List.of(0.1, 0.5, 0.0), List.of(200.0, 40.0, 10.0)),
                List.of(3.0)).get(0);
        for (int i = 0; i < 3; i++) {
            assertEquals(2 * a.numbers()[i], b.numbers()[i], Math.ulp(b.numbers()[i]) * 8);
            assertEquals(2 * a.activities()[i], b.activities()[i],
                    Math.ulp(b.activities()[i]) * 8);
        }
    }

    @Test
    void conservationViolationRaisesStructuredException() {
        // 1e-300 的容差下，正常浮点舍入残差也会被识别为破坏守恒，
        // 由此验证校验通路会抛出带说明的结构化异常。
        ChainAccountant strict = new ChainAccountant(new BatemanSolver(), 1e-8, 1e-300);
        assertThrows(ConservationException.class, () -> strict.account(
                chain(List.of(0.137, 0.513, 0.0), List.of(1234.567, 0.0, 7.5)),
                List.of(3.3, 11.7)));
    }
}
