package com.example.decay.web;

import com.example.decay.service.DecayService;
import com.example.decay.web.dto.HistoryDetail;
import com.example.decay.web.dto.HistoryPage;
import org.springframework.format.annotation.DateTimeFormat;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;

import java.time.Instant;

/**
 * 历史核算记录查询接口。
 */
@RestController
@RequestMapping("/api/history")
public class HistoryController {

    private final DecayService service;

    public HistoryController(DecayService service) {
        this.service = service;
    }

    /** 按模式、成功与否、链长、时间区间等条件分页查询。 */
    @GetMapping
    public HistoryPage query(
            @RequestParam(required = false) String mode,
            @RequestParam(required = false) Boolean success,
            @RequestParam(required = false) Integer chainLength,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant from,
            @RequestParam(required = false)
            @DateTimeFormat(iso = DateTimeFormat.ISO.DATE_TIME) Instant to,
            @RequestParam(defaultValue = "0") int page,
            @RequestParam(defaultValue = "20") int size) {
        return service.queryHistory(mode, success, chainLength, from, to, page, size);
    }

    /** 取单条记录详情（含请求与响应 JSON）。 */
    @GetMapping("/{id}")
    public HistoryDetail detail(@PathVariable String id) {
        return service.getRecord(id);
    }
}
