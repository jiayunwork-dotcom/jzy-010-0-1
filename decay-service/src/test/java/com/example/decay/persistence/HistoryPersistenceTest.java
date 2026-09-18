package com.example.decay.persistence;

import com.example.decay.physics.ChainAccountant;
import com.example.decay.service.DecayService;
import com.example.decay.service.HistoryRecorder;
import com.example.decay.solver.BatemanSolver;
import com.example.decay.validation.DecayRequestValidator;
import com.example.decay.validation.ValidationException;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.time.Instant;
import java.time.temporal.ChronoUnit;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 历史持久化专项测试：成功与失败请求都入库、条件过滤生效、
 * 失败留痕独立事务提交（不随外层异常回滚）。
 */
@SpringBootTest
class HistoryPersistenceTest {

    @Autowired
    DecayService service;

    @Autowired
    CalcRecordRepository repository;

    @Autowired
    ObjectMapper om;

    private JsonNode json(String s) throws Exception {
        return om.readTree(s);
    }

    @Test
    void successfulRequestIsPersistedWithFullPayload() throws Exception {
        long before = repository.count();
        var resp = service.solve(json("""
                {"decayConstants":[0.2,0.0],"initialNumbers":[100,5],"times":[0,1,2]}"""),
                "SOLVE");
        assertEquals(before + 1, repository.count());

        CalcRecord record = repository.findById(resp.requestId()).orElseThrow();
        assertEquals("SOLVE", record.getMode());
        assertTrue(record.isSuccess());
        assertEquals(2, record.getChainLength());
        assertEquals(3, record.getTimePointCount());
        assertNotNull(record.getRequestJson());
        assertNotNull(record.getResponseJson());
        assertTrue(record.getRequestJson().contains("decayConstants"));
        assertTrue(record.getResponseJson().contains("points"));
        assertNotNull(record.getCreatedAt());
    }

    @Test
    void failedValidationRequestIsStillPersisted() throws Exception {
        long before = repository.count();
        assertThrows(ValidationException.class, () -> service.solve(json("""
                {"decayConstants":[0.2,-0.9],"initialNumbers":[1,2],"times":[1]}"""),
                "SOLVE"));
        assertEquals(before + 1, repository.count(), "校验失败也必须独立事务留痕");
        CalcRecord failed = repository.findAll().stream()
                .filter(r -> !r.isSuccess())
                .reduce((a, b) -> b)
                .orElseThrow();
        assertEquals("SOLVE", failed.getMode());
        assertFalse(failed.isSuccess());
        assertNotNull(failed.getErrorSummary());
        assertNotNull(failed.getResponseJson());
    }

    @Test
    void batchRequestIsPersistedAsOneRecord() throws Exception {
        long before = repository.count();
        var resp = service.batch(json("""
                {"items":[
                  {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],"times":[1]},
                  {"decayConstants":[999],"initialNumbers":[1],"times":[1]}
                ]}"""));
        assertEquals(before + 1, repository.count());
        CalcRecord record = repository.findById(resp.requestId()).orElseThrow();
        assertEquals("BATCH", record.getMode());
        assertEquals(2, record.getItemCount());
    }

    @Test
    void historyFilteringByModeSuccessAndTimeRangeWorks() throws Exception {
        Instant start = Instant.now().minusSeconds(2);
        service.solve(json("""
                {"decayConstants":[0.9,0.0],"initialNumbers":[1,0],
                 "startTime":0,"endTime":1,"steps":2}"""),
                "GRID");
        Instant end = Instant.now().plusSeconds(2);

        var onlyGrid = service.queryHistory("GRID", null, null, null, null, 0, 50);
        assertTrue(onlyGrid.items().stream().allMatch(i -> "GRID".equals(i.mode())));

        var onlySuccess = service.queryHistory(null, true, null, start, end, 0, 50);
        assertTrue(onlySuccess.items().stream().allMatch(i -> i.createdAt().isAfter(start)
                && i.createdAt().isBefore(end)));
        assertTrue(onlySuccess.total() >= 1);

        var failures = service.queryHistory(null, false, null,
                start.minus(1, ChronoUnit.DAYS), end.plus(1, ChronoUnit.DAYS), 0, 50);
        assertTrue(failures.items().stream().noneMatch(i -> i.success()));
    }

    @Test
    void historyDetailRoundTripsJson() throws Exception {
        var resp = service.solve(json("""
                {"decayConstants":[0.5,0.0],"initialNumbers":[42,0],"times":[3]}"""),
                "SOLVE");
        var detail = service.getRecord(resp.requestId());
        assertEquals(0.5, detail.request().get("decayConstants").get(0).asDouble(), 0);
        assertEquals(42, detail.response().get("initialNumbers").get(0).asDouble(), 0);
        assertEquals(3, detail.response().get("points").get(0).get("time").asDouble(), 0);
    }
}
