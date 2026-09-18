package com.example.decay.validation;

import com.example.decay.config.DecayProperties;
import com.example.decay.model.DecayChain;
import com.fasterxml.jackson.databind.JsonNode;
import org.springframework.stereotype.Component;

import java.util.ArrayList;
import java.util.List;

/**
 * 从原始 JSON 树解析并校验核算请求。
 *
 * <p>直接基于 {@link JsonNode} 解析，是为了在“字段缺失、类型错误、NaN/Infinity、
 * 长度不一致、负零伪装稳定核”等情况下，都能返回指明<b>第几组、第几个核、哪个参数</b>
 * 的结构化错误，而不是让 Jackson 反序列化抛出语义不明的异常。</p>
 */
@Component
public class DecayRequestValidator {

    private final DecayProperties props;

    public DecayRequestValidator(DecayProperties props) {
        this.props = props;
    }

    /** 校验显式时刻求解请求（{@code times} 必填）。 */
    public ParsedRequest validateSolve(JsonNode body) {
        return validate(body, false, null);
    }

    /** 校验均匀网格请求（{@code startTime,endTime,steps} 必填）。 */
    public ParsedRequest validateGrid(JsonNode body) {
        return validate(body, true, null);
    }

    /**
     * 校验批量请求中的某一组。组级问题作为返回值的 issue 返回（不抛出），
     * 以便同批其余组继续计算；信封级问题（缺 items 等）直接抛 {@link ValidationException}。
     */
    public BatchItemResult validateBatchItem(JsonNode item, int batchIndex) {
        if (item == null || item.isNull()) {
            return new BatchItemResult(null, List.of(issue(
                    "MISSING_FIELD", "批量中的核算项不能为 null",
                    "item", null, batchIndex)));
        }
        if (!item.isObject()) {
            return new BatchItemResult(null, List.of(issue(
                    "TYPE_MISMATCH", "批量中的核算项必须是 JSON 对象",
                    "item", null, batchIndex)));
        }
        // 组内若是网格请求，则按网格规则处理；统一按“可含 times 或网格参数”解析
        List<ValidationIssue> issues = new ArrayList<>();
        DecayChain chain = parseChain(item, issues, batchIndex);
        List<Double> times = parseTimesAny(item, issues, batchIndex);
        if (!issues.isEmpty()) {
            return new BatchItemResult(null, issues);
        }
        return new BatchItemResult(new ParsedRequest(chain, times), List.of());
    }

    /** 校验批量信封：items 必须为非空且不超过上限的数组。 */
    public List<JsonNode> validateBatchEnvelope(JsonNode body) {
        if (body == null || !body.hasNonNull("items")) {
            throw new ValidationException(ValidationIssue.of(
                    "MISSING_FIELD", "缺少必填字段 items", "items"));
        }
        JsonNode items = body.get("items");
        if (!items.isArray()) {
            throw new ValidationException(ValidationIssue.of(
                    "TYPE_MISMATCH", "items 必须是数组", "items"));
        }
        if (items.isEmpty()) {
            throw new ValidationException(ValidationIssue.of(
                    "EMPTY_BATCH", "批量请求至少包含一组核算", "items"));
        }
        if (items.size() > props.maxBatchItems()) {
            ValidationIssue issue = new ValidationIssue(
                    "BATCH_TOO_LARGE",
                    String.format("批量组数 %d 超过上限 %d", items.size(), props.maxBatchItems()),
                    "items", null, null);
            throw new ValidationException(issue);
        }
        List<JsonNode> nodes = new ArrayList<>(items.size());
        for (JsonNode node : items) {
            nodes.add(node);
        }
        return nodes;
    }

    /** 单组解析结果（成功带请求，失败带问题列表）。 */
    public record BatchItemResult(ParsedRequest request, List<ValidationIssue> issues) {
        public boolean ok() {
            return request != null;
        }
    }

    private ParsedRequest validate(JsonNode body, boolean gridMode, Integer batchIndex) {
        List<ValidationIssue> issues = new ArrayList<>();
        DecayChain chain = parseChain(body, issues, batchIndex);
        List<Double> times = gridMode
                ? parseGridTimes(body, issues, batchIndex)
                : parseExplicitTimes(body, issues, batchIndex);
        if (!issues.isEmpty()) {
            throw new ValidationException(issues);
        }
        return new ParsedRequest(chain, times);
    }

    private DecayChain parseChain(JsonNode body, List<ValidationIssue> issues, Integer batchIndex) {
        JsonNode lambdasNode = requireArray(body, "decayConstants", issues, batchIndex);
        JsonNode initialsNode = requireArray(body, "initialNumbers", issues, batchIndex);
        JsonNode labelsNode = body == null ? null : body.get("nuclides");
        if (labelsNode != null && !labelsNode.isNull() && !labelsNode.isArray()) {
            issues.add(issue("TYPE_MISMATCH", "nuclides 必须是字符串数组",
                    "nuclides", null, batchIndex));
        }

        // 长度/链长校验优先，保证后续按位置报错有意义
        int len = -1;
        if (lambdasNode != null && initialsNode != null) {
            int nl = lambdasNode.size();
            int ni = initialsNode.size();
            if (nl != ni) {
                issues.add(issue("LENGTH_MISMATCH",
                        String.format("衰变常数长度 %d 与初始核数长度 %d 不一致", nl, ni),
                        "initialNumbers", null, batchIndex));
            }
            if (labelsNode != null && labelsNode.isArray() && labelsNode.size() != nl) {
                issues.add(issue("LENGTH_MISMATCH",
                        String.format("核素名称长度 %d 与衰变常数长度 %d 不一致",
                                labelsNode.size(), nl),
                        "nuclides", null, batchIndex));
            }
            len = nl;
            if (len < 2) {
                issues.add(issue("CHAIN_TOO_SHORT",
                        String.format("链长度为 %d，至少需要 2 个核素", len),
                        "decayConstants", null, batchIndex));
            }
            if (len > props.maxChainLength()) {
                issues.add(issue("CHAIN_TOO_LONG",
                        String.format("链长度为 %d，超过上限 %d", len, props.maxChainLength()),
                        "decayConstants", null, batchIndex));
            }
        }

        List<Double> lambdas = new ArrayList<>();
        List<Double> initials = new ArrayList<>();
        List<String> labels = new ArrayList<>();
        boolean chainUsable = len >= 2 && len <= props.maxChainLength()
                && lambdasNode != null && initialsNode != null;

        if (chainUsable) {
            for (int i = 0; i < len; i++) {
                lambdas.add(parseNonNegativeFinite(lambdasNode.get(i), "decayConstants",
                        i, issues, batchIndex));
                initials.add(parseNonNegativeFinite(initialsNode.get(i), "initialNumbers",
                        i, issues, batchIndex));
                String label = null;
                if (labelsNode != null && labelsNode.isArray() && i < labelsNode.size()) {
                    JsonNode ln = labelsNode.get(i);
                    label = ln != null && ln.isTextual() ? ln.asText()
                            : "N" + (i + 1);
                    if (ln != null && !ln.isTextual()) {
                        issues.add(issue("TYPE_MISMATCH",
                                "核素名称必须是字符串", "nuclides", i, batchIndex));
                    }
                } else {
                    label = "N" + (i + 1);
                }
                labels.add(label);
            }
            // 仅末核允许为零（稳定终点）；中间核零衰变常数属于矛盾输入
            for (int i = 0; i < len - 1; i++) {
                Double v = lambdas.get(i);
                if (v != null && v == 0.0) {
                    issues.add(issue("STABLE_NON_TERMINAL",
                            String.format("第 %d 个核素（%s）不是末核，衰变常数不能为 0；"
                                            + "只有末核允许为稳定终点",
                                    i + 1, labels.get(i)),
                            "decayConstants", i, batchIndex));
                }
            }
        }
        return issues.isEmpty() ? new DecayChain(List.copyOf(lambdas), List.copyOf(initials),
                List.copyOf(labels)) : null;
    }

    private List<Double> parseExplicitTimes(JsonNode body, List<ValidationIssue> issues,
                                            Integer batchIndex) {
        JsonNode node = requireArray(body, "times", issues, batchIndex);
        if (node == null) {
            return List.of();
        }
        if (node.isEmpty()) {
            issues.add(issue("EMPTY_TIMES", "times 至少包含一个时刻",
                    "times", null, batchIndex));
            return List.of();
        }
        if (node.size() > props.maxTimePoints()) {
            issues.add(issue("TOO_MANY_TIME_POINTS",
                    String.format("时刻数 %d 超过上限 %d", node.size(), props.maxTimePoints()),
                    "times", null, batchIndex));
        }
        return parseTimeArray(node, issues, batchIndex);
    }

    /** 批量单组：允许显式 times 或网格参数二选一。 */
    private List<Double> parseTimesAny(JsonNode body, List<ValidationIssue> issues,
                                       Integer batchIndex) {
        boolean hasTimes = body != null && body.has("times");
        boolean hasGrid = body != null
                && (body.has("startTime") || body.has("endTime") || body.has("steps"));
        if (hasTimes && hasGrid) {
            issues.add(issue("AMBIGUOUS_TIME_SPEC",
                    "times 与均匀网格参数(startTime/endTime/steps)只能二选一",
                    "times", null, batchIndex));
            return List.of();
        }
        if (!hasTimes && !hasGrid) {
            issues.add(issue("MISSING_FIELD",
                    "缺少时刻定义：需要 times，或 startTime/endTime/steps",
                    "times", null, batchIndex));
            return List.of();
        }
        return hasTimes ? parseExplicitTimes(body, issues, batchIndex)
                : parseGridTimes(body, issues, batchIndex);
    }

    private List<Double> parseGridTimes(JsonNode body, List<ValidationIssue> issues,
                                        Integer batchIndex) {
        Double start = requireFiniteNumber(body, "startTime", issues, batchIndex);
        Double end = requireFiniteNumber(body, "endTime", issues, batchIndex);
        Integer steps = requirePositiveInt(body, "steps", issues, batchIndex);
        if (start != null && start < 0) {
            issues.add(issue("NEGATIVE_TIME",
                    String.format("startTime 不能为负：%s", start), "startTime", null, batchIndex));
        }
        if (start != null && end != null && end < start) {
            issues.add(issue("INVALID_RANGE",
                    String.format("endTime(%s) 不能小于 startTime(%s)", end, start),
                    "endTime", null, batchIndex));
        }
        if (steps != null && steps > props.maxGridSteps()) {
            issues.add(issue("GRID_STEPS_TOO_LARGE",
                    String.format("步数 %d 超过上限 %d", steps, props.maxGridSteps()),
                    "steps", null, batchIndex));
        }
        if (!issues.isEmpty() || start == null || end == null || steps == null) {
            return List.of();
        }
        List<Double> times = new ArrayList<>(steps + 1);
        for (int i = 0; i <= steps; i++) {
            double t = start + (end - start) * i / steps;
            times.add(t);
        }
        return times;
    }

    private List<Double> parseTimeArray(JsonNode node, List<ValidationIssue> issues,
                                        Integer batchIndex) {
        List<Double> times = new ArrayList<>(node.size());
        for (int i = 0; i < node.size(); i++) {
            JsonNode element = node.get(i);
            Double v = readFiniteNumber(element);
            if (v == null) {
                String reason = element == null || element.isNull() ? "不能为 null"
                        : "必须是有限数值";
                issues.add(new ValidationIssue("INVALID_TIME",
                        String.format("times 中第 %d 个时刻%s", i + 1, reason),
                        "times", null, batchIndex));
                times.add(0.0);
                continue;
            }
            if (v < 0) {
                issues.add(new ValidationIssue("NEGATIVE_TIME",
                        String.format("times 中第 %d 个时刻为负：%s", i + 1, v),
                        "times", null, batchIndex));
            }
            times.add(v);
        }
        return times;
    }

    private JsonNode requireArray(JsonNode body, String field, List<ValidationIssue> issues,
                                  Integer batchIndex) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            issues.add(issue("MISSING_FIELD", "缺少必填字段 " + field, field, null, batchIndex));
            return null;
        }
        JsonNode node = body.get(field);
        if (!node.isArray()) {
            issues.add(issue("TYPE_MISMATCH", field + " 必须是数组", field, null, batchIndex));
            return null;
        }
        return node;
    }

    /**
     * 解析“非负有限数”，并识别把稳定核写成负零（-0.0）的矛盾输入。
     * JSON 文本 {@code -0} 解析后可用 {@code Double.doubleToRawLongBits} 检出符号位。
     */
    private Double parseNonNegativeFinite(JsonNode element, String field, int nuclideIndex,
                                          List<ValidationIssue> issues, Integer batchIndex) {
        if (element == null || element.isNull()) {
            issues.add(issue("NOT_A_NUMBER", "不能为 null", field, nuclideIndex, batchIndex));
            return null;
        }
        if (element.isNumber() || element.isBoolean()) {
            if (element.isBoolean()) {
                issues.add(issue("NOT_A_NUMBER", "必须是数值，不能是布尔值",
                        field, nuclideIndex, batchIndex));
                return null;
            }
            double v = element.asDouble();
            if (!Double.isFinite(v)) {
                issues.add(issue("NON_FINITE_NUMBER",
                        String.format("必须是有限数值，实际为 %s", v),
                        field, nuclideIndex, batchIndex));
                return null;
            }
            if (Double.doubleToRawLongBits(v) < 0 && v == 0.0) {
                // -0.0：衰变常数与初值都不接受负零
                String code = "initialNumbers".equals(field) ? "NEGATIVE_INITIAL_NUMBER"
                        : "NEGATIVE_ZERO_LAMBDA";
                String msg = "initialNumbers".equals(field)
                        ? "初始核数不能为负零(-0)"
                        : "衰变常数不能写成负零(-0)；稳定核请直接写 0";
                issues.add(issue(code, msg, field, nuclideIndex, batchIndex));
                return null;
            }
            if (v < 0) {
                String code = "initialNumbers".equals(field) ? "NEGATIVE_INITIAL_NUMBER"
                        : "NEGATIVE_LAMBDA";
                String msg = "initialNumbers".equals(field)
                        ? String.format("初始核数不能为负：%s", v)
                        : String.format("衰变常数不能为负：%s", v);
                issues.add(issue(code, msg, field, nuclideIndex, batchIndex));
                return null;
            }
            return v;
        }
        // 字符串、对象等都属于非数值（注意字符串形式的 "NaN"/"Infinity" 不会被接受）
        issues.add(issue("NOT_A_NUMBER",
                String.format("必须是数值，实际类型为 %s", nodeKind(element)),
                field, nuclideIndex, batchIndex));
        return null;
    }

    private Double requireFiniteNumber(JsonNode body, String field, List<ValidationIssue> issues,
                                       Integer batchIndex) {
        if (body == null || !body.has(field) || body.get(field).isNull()) {
            issues.add(issue("MISSING_FIELD", "缺少必填字段 " + field,
                    field, null, batchIndex));
            return null;
        }
        Double v = readFiniteNumber(body.get(field));
        if (v == null) {
            issues.add(issue("NOT_A_NUMBER", field + " 必须是有限数值",
                    field, null, batchIndex));
        }
        return v;
    }

    private Integer requirePositiveInt(JsonNode body, String field, List<ValidationIssue> issues,
                                       Integer batchIndex) {
        Double v = requireFiniteNumber(body, field, issues, batchIndex);
        if (v == null) {
            return null;
        }
        if (v != Math.rint(v) || v <= 0) {
            issues.add(issue("INVALID_STEPS",
                    String.format("steps 必须是正整数，实际为 %s", v),
                    field, null, batchIndex));
            return null;
        }
        if (v > Integer.MAX_VALUE) {
            issues.add(issue("INVALID_STEPS", "steps 过大", field, null, batchIndex));
            return null;
        }
        return v.intValue();
    }

    /** 严格有限数：布尔、字符串、NaN/Infinity 一律拒绝。 */
    private Double readFiniteNumber(JsonNode node) {
        if (node == null || !node.isNumber()) {
            return null;
        }
        double v = node.asDouble();
        return Double.isFinite(v) ? v : null;
    }

    private String nodeKind(JsonNode node) {
        if (node.isTextual()) {
            return "字符串";
        }
        if (node.isObject()) {
            return "对象";
        }
        if (node.isArray()) {
            return "数组";
        }
        if (node.isBoolean()) {
            return "布尔";
        }
        return node.getNodeType().name().toLowerCase();
    }

    private ValidationIssue issue(String code, String message, String parameter,
                                  Integer nuclideIndex, Integer batchIndex) {
        return new ValidationIssue(code, message, parameter,
                nuclideIndex == null ? null : nuclideIndex + 1,
                batchIndex == null ? null : batchIndex + 1);
    }
}
