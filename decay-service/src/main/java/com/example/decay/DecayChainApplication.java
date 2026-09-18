package com.example.decay;

import org.springframework.boot.SpringApplication;
import org.springframework.boot.autoconfigure.SpringBootApplication;
import org.springframework.boot.context.properties.ConfigurationPropertiesScan;

/**
 * 放射性衰变链核算服务启动入口。
 */
@SpringBootApplication
@ConfigurationPropertiesScan
public class DecayChainApplication {

    public static void main(String[] args) {
        SpringApplication.run(DecayChainApplication.class, args);
    }
}
