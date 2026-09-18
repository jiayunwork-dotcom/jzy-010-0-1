package com.example.decay.service;

/**
 * 请求的历史记录不存在。
 */
public class RecordNotFoundException extends RuntimeException {

    public RecordNotFoundException(String message) {
        super(message);
    }
}
