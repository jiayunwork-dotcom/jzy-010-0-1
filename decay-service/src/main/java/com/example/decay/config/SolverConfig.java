package com.example.decay.config;

import com.example.decay.physics.ChainAccountant;
import com.example.decay.solver.BatemanSolver;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 求解器与核算器 Bean 装配（均为无状态线程安全组件，全应用单例）。
 */
@Configuration
public class SolverConfig {

    @Bean
    public BatemanSolver batemanSolver() {
        return new BatemanSolver();
    }

    @Bean
    public ChainAccountant chainAccountant(BatemanSolver solver, DecayProperties props) {
        return new ChainAccountant(solver, props.nearEqualRelTol(), props.conservationRelTol());
    }
}
