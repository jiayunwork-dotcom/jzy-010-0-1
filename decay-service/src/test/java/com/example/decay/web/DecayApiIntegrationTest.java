package com.example.decay.web;

import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import com.fasterxml.jackson.databind.node.ObjectNode;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.boot.test.web.client.TestRestTemplate;
import org.springframework.boot.test.web.server.LocalServerPort;
import org.springframework.http.HttpEntity;
import org.springframework.http.HttpHeaders;
import org.springframework.http.HttpStatus;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.jdbc.core.JdbcTemplate;

import java.util.ArrayList;
import java.util.List;
import java.util.concurrent.Callable;
import java.util.concurrent.ExecutorService;
import java.util.concurrent.Executors;
import java.util.concurrent.Future;
import java.util.concurrent.TimeUnit;
import java.util.concurrent.atomic.AtomicInteger;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;

/**
 * 端到端测试：HTTP 接口语义、结构化错误、批量部分失败、历史持久化、并发隔离。
 */
@SpringBootTest(webEnvironment = SpringBootTest.WebEnvironment.RANDOM_PORT)
class DecayApiIntegrationTest {

    @LocalServerPort
    int port;

    @Autowired
    TestRestTemplate rest;

    @Autowired
    ObjectMapper om;

    @Autowired
    JdbcTemplate jdbc;

    private String url(String path) {
        return "http://localhost:" + port + path;
    }

    private HttpHeaders jsonHeaders() {
        HttpHeaders h = new HttpHeaders();
        h.setContentType(MediaType.APPLICATION_JSON);
        return h;
    }

    private JsonNode post(String path, String body) {
        ResponseEntity<String> resp = rest.postForEntity(url(path),
                new HttpEntity<>(body, jsonHeaders()), String.class);
        try {
            ObjectNode node = (ObjectNode) om.readTree(resp.getBody());
            node.putObject("_meta").put("status", resp.getStatusCode().value());
            return node;
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    private JsonNode get(String path) {
        try {
            return om.readTree(rest.getForObject(url(path), String.class));
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void configEndpointEchoesLimits() {
        JsonNode cfg = get("/api/config");
        assertEquals(12, cfg.get("maxChainLength").asInt());
        assertEquals(2, cfg.get("minChainLength").asInt());
        assertTrue(cfg.get("nearEqualRelTol").asDouble() > 0);
        assertTrue(cfg.get("maxGridSteps").asInt() > 0);
    }

    @Test
    void statusEndpointReportsUp() {
        JsonNode s = get("/api/status");
        assertEquals("UP", s.get("status").asText());
        assertTrue(s.has("persistedRecords"));
    }

    @Test
    void solveEndpointReturnsAllRequiredFieldsAndZeroTimeExactness() {
        JsonNode resp = post("/api/decay/solve", """
                {"decayConstants":[0.1,0.5,0.0],
                 "initialNumbers":[1000,0,0],
                 "nuclides":["A","B","C"],
                 "times":[0,1,5]}""");
        assertEquals(200, resp.path("_meta").path("status").asInt());
        JsonNode t0 = resp.get("points").get(0);
        assertEquals(1000.0, t0.get("numbers").get(0).asDouble(), 0);
        assertEquals(0.0, t0.get("numbers").get(1).asDouble(), 0);
        assertEquals(0.0, t0.get("numbers").get(2).asDouble(), 0);
        JsonNode t5 = resp.get("points").get(2);
        assertEquals(1000.0, t5.get("chainRemainingAtoms").asDouble(), 1e-6);
        assertEquals(0.0, t5.get("activities").get(2).asDouble(), 0,
                "稳定核活度必须为零");
        assertTrue(t5.has("stableEndpointAtoms"));
        assertTrue(t5.has("activeAtoms"));
        assertNotNull(resp.get("requestId").asText());
    }

    @Test
    void gridEndpointGeneratesMeshAndPersists() {
        long before = countRecords();
        JsonNode resp = post("/api/decay/grid", """
                {"decayConstants":[0.1,0.0],
                 "initialNumbers":[100,0],
                 "startTime":0,"endTime":10,"steps":5}""");
        assertEquals(200, resp.path("_meta").path("status").asInt());
        assertEquals(6, resp.get("points").size());
        assertEquals(10.0, resp.get("points").get(5).get("time").asDouble(), 0);
        assertTrue(countRecords() >= before + 1, "成功请求必须入库");
    }

    @Test
    void invalidInputReturnsStructuredErrorAndIsPersisted() {
        long before = countRecords();
        ResponseEntity<String> raw = rest.postForEntity(url("/api/decay/solve"),
                new HttpEntity<>("""
                        {"decayConstants":[0.1,-0.5,0.0],
                         "initialNumbers":[1,2,3],"times":[1]}""", jsonHeaders()),
                String.class);
        assertEquals(HttpStatus.BAD_REQUEST, raw.getStatusCode());
        JsonNode err;
        try {
            err = om.readTree(raw.getBody());
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
        assertEquals("VALIDATION_FAILED", err.get("error").asText());
        JsonNode issue = err.get("issues").get(0);
        assertEquals("NEGATIVE_LAMBDA", issue.get("code").asText());
        assertEquals(2, issue.get("nuclideIndex").asInt(), "指出第几个核");
        assertEquals("decayConstants", issue.get("parameter").asText(), "指出哪个参数");
        assertTrue(countRecords() >= before + 1, "失败请求也必须留痕");
    }

    @Test
    void malformedJsonDoesNotCrash() {
        ResponseEntity<String> raw = rest.postForEntity(url("/api/decay/solve"),
                new HttpEntity<>("{not json", jsonHeaders()), String.class);
        assertEquals(HttpStatus.BAD_REQUEST, raw.getStatusCode());
        assertTrue(raw.getBody().contains("MALFORMED_JSON"));
    }

    @Test
    void missingFieldReturnsReadableError() {
        JsonNode resp = post("/api/decay/solve", """
                {"initialNumbers":[1,0]}""");
        assertEquals(400, resp.path("_meta").path("status").asInt());
        assertTrue(resp.get("issues").size() > 0);
    }

    @Test
    void batchPartialFailureKeepsOtherResults() {
        JsonNode resp = post("/api/decay/batch", """
                {"items":[
                  {"decayConstants":[0.1,0.0],"initialNumbers":[100,0],"times":[1]},
                  {"decayConstants":[0.1],"initialNumbers":[1],"times":[1]},
                  {"decayConstants":[0.2,0.0],"initialNumbers":[50,0],
                   "startTime":0,"endTime":1,"steps":2},
                  {"decayConstants":[0.1,-1.0],"initialNumbers":[1,2],"times":[1]}
                ]}""");
        assertEquals(200, resp.path("_meta").path("status").asInt());
        assertEquals(4, resp.get("total").asInt());
        assertEquals(2, resp.get("succeeded").asInt());
        assertEquals(2, resp.get("failed").asInt());
        assertTrue(resp.get("items").get(0).get("ok").asBoolean());
        assertFalse(resp.get("items").get(1).get("ok").asBoolean());
        assertEquals("CHAIN_TOO_SHORT",
                resp.get("items").get(1).get("issues").get(0).get("code").asText());
        assertTrue(resp.get("items").get(2).get("ok").asBoolean());
        assertEquals(3, resp.get("items").get(2).get("result").get("points").size());
        JsonNode issue4 = resp.get("items").get(3).get("issues").get(0);
        assertEquals(4, issue4.get("batchIndex").asInt(), "指出第几组");
        assertEquals(2, issue4.get("nuclideIndex").asInt(), "指出第几个核");
    }

    @Test
    void historyCanBeFilteredAndDetailsContainRequestAndResponse() throws Exception {
        // 制造一条已知记录
        JsonNode created = post("/api/decay/solve", """
                {"decayConstants":[0.33,0.0],"initialNumbers":[777,0],"times":[2]}""");
        String id = created.get("requestId").asText();

        JsonNode page = get("/api/history?mode=SOLVE&success=true&chainLength=2&page=0&size=5");
        assertTrue(page.get("total").asLong() >= 1);
        boolean found = false;
        for (JsonNode item : page.get("items")) {
            if (id.equals(item.get("id").asText())) {
                found = true;
            }
        }
        assertTrue(found, "历史列表应包含刚创建的记录");

        JsonNode detail = get("/api/history/" + id);
        assertEquals(0.33, detail.get("request").get("decayConstants").get(0).asDouble(), 0);
        assertEquals(777, detail.get("response").get("initialNumbers").get(0).asDouble(), 0);

        // 不存在的记录返回 404 结构化错误
        ResponseEntity<String> missing = rest.getForEntity(
                url("/api/history/does-not-exist"), String.class);
        assertEquals(HttpStatus.NOT_FOUND, missing.getStatusCode());
    }

    @Test
    void exampleEndpointIsDirectlyCallableAndDaughterGrowsLinearlyEarlyOn() {
        JsonNode resp = get("/api/decay/example");
        assertEquals(200, 200);
        assertTrue(resp.get("points").size() >= 2);
        JsonNode p0 = resp.get("points").get(0);
        assertEquals(1.0e6, p0.get("numbers").get(0).asDouble(), 0);
        // 极短时间内子体生长量应与 λ1·N1(0)·t 同量级（此处 t=0.01，λ1=0.01）
        JsonNode p1 = resp.get("points").get(1);
        double daughter = p1.get("numbers").get(1).asDouble();
        double expected = 0.01 * 1.0e6 * 0.01;
        assertEquals(expected, daughter, expected * 0.5,
                "短时间子体生长量与 λ1·N1(0)·t 同量级");
    }

    @Test
    void concurrentRequestsDoNotInterfere() throws Exception {
        int threads = 24;
        int perThread = 20;
        ExecutorService pool = Executors.newFixedThreadPool(threads);
        List<Callable<Boolean>> tasks = new ArrayList<>();
        AtomicInteger checked = new AtomicInteger();
        for (int w = 0; w < threads; w++) {
            final int worker = w;
            tasks.add(() -> {
                for (int j = 0; j < perThread; j++) {
                    double lambda = 0.01 + 0.001 * worker;
                    double n0 = 1000.0 + 100.0 * j;
                    String body = String.format(
                            "{\"decayConstants\":[%.6f,0.0],\"initialNumbers\":[%.1f,0],"
                                    + "\"times\":[0,1,3]}", lambda, n0);
                    JsonNode resp = post("/api/decay/solve", body);
                    if (resp.path("_meta").path("status").asInt() != 200) {
                        return false;
                    }
                    // 母体必须是“本请求自己的”指数曲线
                    for (JsonNode p : resp.get("points")) {
                        double t = p.get("time").asDouble();
                        double expect = n0 * Math.exp(-lambda * t);
                        double actual = p.get("numbers").get(0).asDouble();
                        if (Math.abs(expect - actual) > 1e-6 * n0) {
                            return false;
                        }
                    }
                    checked.incrementAndGet();
                }
                return true;
            });
        }
        List<Future<Boolean>> futures = pool.invokeAll(tasks);
        pool.shutdown();
        assertTrue(pool.awaitTermination(60, TimeUnit.SECONDS));
        for (Future<Boolean> f : futures) {
            assertTrue(f.get(), "并发请求结果串扰或失败");
        }
        assertEquals(threads * perThread, checked.get());
    }

    private long countRecords() {
        Long c = jdbc.queryForObject("select count(*) from calc_records", Long.class);
        return c == null ? 0 : c;
    }
}
