package com.example.decay.service;

import com.example.decay.model.DecayChain;
import com.example.decay.persistence.CalcRecord;
import com.example.decay.persistence.CalcRecordRepository;
import com.example.decay.physics.AccountingPoint;
import com.example.decay.physics.ChainAccountant;
import com.example.decay.physics.ConservationException;
import com.example.decay.validation.DecayRequestValidator;
import com.example.decay.validation.ParsedRequest;
import com.example.decay.validation.ValidationException;
import com.example.decay.validation.ValidationIssue;
import com.example.decay.web.dto.BatchItemResponse;
import com.example.decay.web.dto.BatchResponse;
import com.example.decay.web.dto.HistoryDetail;
import com.example.decay.web.dto.HistoryItem;
import com.example.decay.web.dto.HistoryPage;
import com.example.decay.web.dto.PointResponse;
import com.example.decay.web.dto.SolveResponse;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import jakarta.persistence.criteria.Predicate;
import org.springframework.data.domain.Page;
import org.springframework.data.domain.PageRequest;
import org.springframework.data.domain.Sort;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.time.Instant;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 核算服务门面：串联输入校验、解析求解、守恒核算与历史持久化。
 *
 * <p>本类无任何可变共享状态，求解器与核算器均为线程安全组件，可直接并发处理请求。
 * 写库通过 {@link HistoryRecorder} 在独立事务中完成，失败请求也能可靠留痕。</p>
 */
@Service
public class DecayService {

    private final DecayRequestValidator validator;
    private final ChainAccountant accountant;
    private final CalcRecordRepository repository;
    private final HistoryRecorder recorder;
    private final ObjectMapper objectMapper;

    public DecayService(DecayRequestValidator validator,
                        ChainAccountant accountant,
                        CalcRecordRepository repository,
                        HistoryRecorder recorder,
                        ObjectMapper objectMapper) {
        this.validator = validator;
        this.accountant = accountant;
        this.repository = repository;
        this.recorder = recorder;
        this.objectMapper = objectMapper;
    }

    public SolveResponse solve(JsonNode body, String mode) {
        ParsedRequest parsed;
        try {
            parsed = "GRID".equals(mode)
                    ? validator.validateGrid(body)
                    : validator.validateSolve(body);
        } catch (ValidationException ex) {
            recorder.record(newId(), mode, false, null, null, null,
                    ex.issues().get(0).message(), body, ex.issues());
            throw ex;
        }
        SolveResponse response;
        try {
            response = compute(parsed);
        } catch (ConservationException ex) {
            recorder.record(newId(), mode, false, parsed.chain().length(),
                    parsed.times().size(), null, ex.getMessage(), body,
                    List.of(new ValidationIssue("CONSERVATION_FAILED", ex.getMessage(),
                            "computation", null, null)));
            throw ex;
        }
        recorder.record(response.requestId(), mode, true, parsed.chain().length(),
                parsed.times().size(), null, null, body, response);
        return response;
    }

    public BatchResponse batch(JsonNode body) {
        List<JsonNode> itemNodes = validator.validateBatchEnvelope(body);
        String requestId = newId();

        List<BatchItemResponse> itemResponses = new ArrayList<>(itemNodes.size());
        int succeeded = 0;
        for (int i = 0; i < itemNodes.size(); i++) {
            DecayRequestValidator.BatchItemResult itemResult =
                    validator.validateBatchItem(itemNodes.get(i), i);
            if (!itemResult.ok()) {
                itemResponses.add(new BatchItemResponse(i + 1, false, null,
                        itemResult.issues()));
                continue;
            }
            try {
                SolveResponse result = compute(itemResult.request());
                itemResponses.add(new BatchItemResponse(i + 1, true, result, List.of()));
                succeeded++;
            } catch (ConservationException e) {
                itemResponses.add(new BatchItemResponse(i + 1, false, null,
                        List.of(new ValidationIssue("CONSERVATION_FAILED", e.getMessage(),
                                "computation", null, i + 1))));
            }
        }
        BatchResponse response = new BatchResponse(requestId, itemNodes.size(), succeeded,
                itemNodes.size() - succeeded, List.copyOf(itemResponses));
        recorder.record(requestId, "BATCH", true, null, null, itemNodes.size(),
                null, body, response);
        return response;
    }

    /** 单组核算（校验通过后）。 */
    private SolveResponse compute(ParsedRequest parsed) {
        DecayChain chain = parsed.chain();
        List<AccountingPoint> points = accountant.account(chain, parsed.times());
        List<PointResponse> pointResponses = new ArrayList<>(points.size());
        for (AccountingPoint p : points) {
            pointResponses.add(new PointResponse(
                    p.time(),
                    box(p.numbers()),
                    box(p.activities()),
                    p.parentRemaining(),
                    p.parentRemainingFraction(),
                    p.chainRemainingAtoms(),
                    p.activeAtoms(),
                    p.stableEndpointAtoms(),
                    p.initialTotalAtoms(),
                    p.conservationResidual()));        }
        return new SolveResponse(
                newId(),
                List.copyOf(chain.labels()),
                List.copyOf(chain.decayConstants()),
                List.copyOf(chain.initialNumbers()),
                chain.hasStableEndpoint(),
                List.copyOf(pointResponses));
    }

    @Transactional(readOnly = true)
    public HistoryPage queryHistory(String mode, Boolean success, Integer chainLength,
                                    Instant from, Instant to, int page, int size) {
        PageRequest pr = PageRequest.of(Math.max(0, page), Math.min(200, Math.max(1, size)),
                Sort.by(Sort.Direction.DESC, "createdAt"));
        Page<CalcRecord> result = repository.findAll((root, query, cb) -> {
            List<Predicate> predicates = new ArrayList<>();
            if (mode != null && !mode.isBlank()) {
                predicates.add(cb.equal(root.get("mode"), mode.toUpperCase()));
            }
            if (success != null) {
                predicates.add(cb.equal(root.get("success"), success));
            }
            if (chainLength != null) {
                predicates.add(cb.equal(root.get("chainLength"), chainLength));
            }
            if (from != null) {
                predicates.add(cb.greaterThanOrEqualTo(root.get("createdAt"), from));
            }
            if (to != null) {
                predicates.add(cb.lessThanOrEqualTo(root.get("createdAt"), to));
            }
            return cb.and(predicates.toArray(new Predicate[0]));
        }, pr);

        List<HistoryItem> items = result.getContent().stream()
                .map(r -> new HistoryItem(r.getId(), r.getMode(), r.isSuccess(),
                        r.getChainLength(), r.getTimePointCount(), r.getItemCount(),
                        r.getErrorSummary(), r.getCreatedAt()))
                .toList();
        return new HistoryPage(pr.getPageNumber(), pr.getPageSize(), result.getTotalElements(),
                items);
    }

    @Transactional(readOnly = true)
    public HistoryDetail getRecord(String id) {
        CalcRecord r = repository.findById(id).orElseThrow(() ->
                new RecordNotFoundException("历史记录不存在: " + id));
        return new HistoryDetail(r.getId(), r.getMode(), r.isSuccess(), r.getChainLength(),
                r.getTimePointCount(), r.getItemCount(), r.getErrorSummary(), r.getCreatedAt(),
                readJson(r.getRequestJson()), readJson(r.getResponseJson()));
    }

    /** 预置可直接调用算例：3 核（母体慢、子体快、末核稳定），时间短到母体几乎不变。 */
    public SolveResponse example() {
        JsonNode node;
        try {
            node = objectMapper.readTree("""
                    {
                      "decayConstants": [0.01, 10.0, 0.0],
                      "initialNumbers": [1000000.0, 0.0, 0.0],
                      "nuclides": ["母体(示例)", "短寿命子体(示例)", "稳定末核(示例)"],
                      "times": [0.0, 0.01, 0.05, 0.1, 0.5, 1.0]
                    }""");
        } catch (Exception e) {
            throw new IllegalStateException("预置算例构造失败", e);
        }
        ParsedRequest parsed = validator.validateSolve(node);
        SolveResponse response = compute(parsed);
        recorder.record(response.requestId(), "SOLVE", true, parsed.chain().length(),
                parsed.times().size(), null, null, node, response);
        return response;
    }

    private JsonNode readJson(String json) {
        if (json == null) {
            return null;
        }
        try {
            return objectMapper.readTree(json);
        } catch (Exception e) {
            return null;
        }
    }

    private static List<Double> box(double[] values) {
        List<Double> list = new ArrayList<>(values.length);
        for (double v : values) {
            list.add(v);
        }
        return list;
    }

    private static String newId() {
        return UUID.randomUUID().toString();
    }
}
