package com.example.decay.web;

import com.example.decay.service.DecayService;
import com.example.decay.web.dto.BatchResponse;
import com.example.decay.web.dto.SolveResponse;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.http.HttpStatus;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 衰变核算接口：显式时刻、均匀网格、批量、预置算例。
 */
@RestController
@RequestMapping("/api/decay")
public class DecayController {

    private final DecayService service;

    public DecayController(DecayService service) {
        this.service = service;
    }

    /** 在调用方给出的一组时刻上求解。 */
    @PostMapping("/solve")
    public SolveResponse solve(@RequestBody JsonNode body) {
        return service.solve(body, "SOLVE");
    }

    /** 由起止时刻与步数生成均匀网格（含两端，共 steps+1 个时刻）。 */
    @PostMapping("/grid")
    public SolveResponse grid(@RequestBody JsonNode body) {
        return service.solve(body, "GRID");
    }

    /** 一次提交多组核算；某组非法不影响其余组，HTTP 状态仍为 200。 */
    @PostMapping("/batch")
    public BatchResponse batch(@RequestBody JsonNode body) {
        return service.batch(body);
    }

    /** 预置算例：可直接调用，无需请求体。 */
    @GetMapping("/example")
    public SolveResponse example() {
        return service.example();
    }

    /** HEAD 形式的轻量存活探测，方便监控简单拉取。 */
    @GetMapping("/ping")
    public ResponseEntity<Void> ping() {
        return new ResponseEntity<>(HttpStatus.OK);
    }
}
