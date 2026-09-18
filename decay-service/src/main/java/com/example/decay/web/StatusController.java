package com.example.decay.web;

import com.example.decay.persistence.CalcRecordRepository;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 基本运行状态，供监控采集；标准健康检查另由 Spring Boot Actuator 暴露。
 */
@RestController
@RequestMapping("/api/status")
public class StatusController {

    private final CalcRecordRepository repository;
    private final Instant startedAt = Instant.now();

    public StatusController(CalcRecordRepository repository) {
        this.repository = repository;
    }

    @GetMapping
    public Map<String, Object> status() {
        Map<String, Object> map = new LinkedHashMap<>();
        map.put("status", "UP");
        map.put("startedAt", startedAt);
        map.put("now", Instant.now());
        map.put("persistedRecords", repository.count());
        return map;
    }
}
