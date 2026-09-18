package com.example.decay.physics;

/**
 * 计算结果未通过原子守恒等内部数值校验时抛出。属于可区分的结构化错误。
 */
public class ConservationException extends RuntimeException {

    public ConservationException(String message) {
        super(message);
    }
}
