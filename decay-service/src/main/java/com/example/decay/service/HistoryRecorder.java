package com.example.decay.service;

import com.example.decay.persistence.CalcRecord;
import com.example.decay.persistence.CalcRecordRepository;
import com.fasterxml.jackson.core.JsonProcessingException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.springframework.stereotype.Component;
import org.springframework.transaction.annotation.Propagation;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;

/**
 * 历史记录写入器。独立事务（REQUIRES_NEW）保证：即使外层核算事务因异常回滚，
 * 失败请求的留痕也能独立提交，不会随之消失。
 */
@Component
public class HistoryRecorder {

    private final CalcRecordRepository repository;
    private final ObjectMapper objectMapper;

    public HistoryRecorder(CalcRecordRepository repository, ObjectMapper objectMapper) {
        this.repository = repository;
        this.objectMapper = objectMapper;
    }

    @Transactional(propagation = Propagation.REQUIRES_NEW)
    public void record(String id, String mode, boolean success, Integer chainLength,
                       Integer timePointCount, Integer itemCount, String errorSummary,
                       JsonNode requestBody, Object response) {
        CalcRecord entity = new CalcRecord(
                id, mode, success, chainLength, timePointCount, itemCount, errorSummary,
                writeJson(requestBody), writeJson(response), Instant.now());
        repository.save(entity);
    }

    private String writeJson(Object value) {
        if (value == null) {
            return null;
        }
        try {
            return objectMapper.writeValueAsString(value);
        } catch (JsonProcessingException e) {
            throw new IllegalStateException("记录序列化失败", e);
        }
    }
}
