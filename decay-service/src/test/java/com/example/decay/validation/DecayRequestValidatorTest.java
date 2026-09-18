package com.example.decay.validation;

import com.example.decay.config.DecayProperties;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.BeforeEach;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertFalse;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.junit.jupiter.api.Assertions.assertTrue;

class DecayRequestValidatorTest {

    private DecayRequestValidator validator;
    private final ObjectMapper om = new ObjectMapper();

    @BeforeEach
    void setUp() {
        validator = new DecayRequestValidator(new DecayProperties(
                12, 1e-8, 10000, 10000, 200, 1e-6));
    }

    private JsonNode json(String s) {
        try {
            return om.readTree(s);
        } catch (Exception e) {
            throw new RuntimeException(e);
        }
    }

    @Test
    void acceptsValidRequest() {
        ParsedRequest parsed = validator.validateSolve(json("""
                {"decayConstants":[0.1,0.0],"initialNumbers":[100,0],"times":[0,1,2]}"""));
        assertEquals(2, parsed.chain().length());
        assertEquals(List.of(0.0, 1.0, 2.0), parsed.times());
    }

    @Test
    void rejectsNegativeLambdaAndPointsToNuclideAndParameter() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,-0.2,0.0],"initialNumbers":[1,2,3],
                        "times":[1]}""")));
        ValidationIssue issue = ex.issues().get(0);
        assertEquals("NEGATIVE_LAMBDA", issue.code());
        assertEquals(2, issue.nuclideIndex(), "必须指出是第 2 个核");
        assertEquals("decayConstants", issue.parameter());
    }

    @Test
    void rejectsNegativeInitialNumber() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,0.0],"initialNumbers":[1,-5],
                        "times":[1]}""")));
        ValidationIssue issue = ex.issues().get(0);
        assertEquals("NEGATIVE_INITIAL_NUMBER", issue.code());
        assertEquals(2, issue.nuclideIndex());
    }

    @Test
    void rejectsNegativeZeroLambda() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,-0.0],"initialNumbers":[1,0],
                        "times":[1]}""")));
        assertEquals("NEGATIVE_ZERO_LAMBDA", ex.issues().get(0).code());
    }

    @Test
    void rejectsNonFiniteValues() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                        "times":[1,1e999]}""")));
        assertTrue(ex.issues().stream().anyMatch(i -> "NON_FINITE_NUMBER".equals(i.code())
                || "INVALID_TIME".equals(i.code())
                || "NOT_A_NUMBER".equals(i.code())));
    }

    @Test
    void rejectsStringNaN() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":["NaN",0.0],"initialNumbers":[1,0],
                        "times":[1]}""")));
        assertEquals("NOT_A_NUMBER", ex.issues().get(0).code());
        assertEquals(1, ex.issues().get(0).nuclideIndex());
    }

    @Test
    void rejectsChainShorterThanTwo() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1],"initialNumbers":[1],"times":[1]}""")));
        assertEquals("CHAIN_TOO_SHORT", ex.issues().get(0).code());
    }

    @Test
    void rejectsChainLongerThanTwelve() {
        String lams = "[0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.1,0.0]";
        String inits = "[1,1,1,1,1,1,1,1,1,1,1,1,1]";
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json(
                        "{\"decayConstants\":" + lams + ",\"initialNumbers\":" + inits
                                + ",\"times\":[1]}")));
        assertEquals("CHAIN_TOO_LONG", ex.issues().get(0).code());
    }

    @Test
    void rejectsLengthMismatch() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,0.2,0.0],"initialNumbers":[1,2],
                        "times":[1]}""")));
        assertEquals("LENGTH_MISMATCH", ex.issues().get(0).code());
    }

    @Test
    void rejectsZeroLambdaAtNonTerminalNuclide() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,0.0,0.2],"initialNumbers":[1,2,3],
                        "times":[1]}""")));
        ValidationIssue issue = ex.issues().get(0);
        assertEquals("STABLE_NON_TERMINAL", issue.code());
        assertEquals(2, issue.nuclideIndex());
    }

    @Test
    void rejectsMissingFields() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("{}")));
        assertTrue(ex.issues().stream().anyMatch(i -> "MISSING_FIELD".equals(i.code())));
    }

    @Test
    void rejectsNonNumericAndWrongTypes() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[true,0.0],"initialNumbers":[{"x":1},2],
                        "times":"soon"}""")));
        assertTrue(ex.issues().size() >= 3);
    }

    @Test
    void rejectsNegativeTime() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateSolve(json("""
                        {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                        "times":[0,-1]}""")));
        assertEquals("NEGATIVE_TIME", ex.issues().get(0).code());
    }

    @Test
    void gridRequestRejectsTooManySteps() {
        ValidationException ex = assertThrows(ValidationException.class, () ->
                validator.validateGrid(json("""
                        {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                        "startTime":0,"endTime":10,"steps":10001}""")));
        assertEquals("GRID_STEPS_TOO_LARGE", ex.issues().get(0).code());
    }

    @Test
    void gridRequestBuildsUniformMeshIncludingEndpoints() {
        ParsedRequest parsed = validator.validateGrid(json("""
                {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                "startTime":0,"endTime":2,"steps":4}"""));
        assertEquals(List.of(0.0, 0.5, 1.0, 1.5, 2.0), parsed.times());
    }

    @Test
    void gridRejectsEndBeforeStartAndBadStep() {
        assertThrows(ValidationException.class, () ->
                validator.validateGrid(json("""
                        {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                        "startTime":5,"endTime":1,"steps":4}""")));
        assertThrows(ValidationException.class, () ->
                validator.validateGrid(json("""
                        {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                        "startTime":0,"endTime":1,"steps":0}""")));
    }

    @Test
    void batchItemIssuesIdentifyGroupNuclideAndParameter() {
        JsonNode item = json("""
                {"decayConstants":[0.1,-3.0,0.0],"initialNumbers":[1,2,3],
                "times":[1]}""");
        var result = validator.validateBatchItem(item, 6);
        assertFalse(result.ok());
        ValidationIssue issue = result.issues().get(0);
        assertEquals(7, issue.batchIndex(), "必须指出是第几组");
        assertEquals(2, issue.nuclideIndex(), "必须指出是哪个核");
        assertEquals("decayConstants", issue.parameter(), "必须指出是哪个参数");
    }

    @Test
    void batchItemAcceptsGridSpec() {
        JsonNode item = json("""
                {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                "startTime":0,"endTime":1,"steps":2}""");
        var result = validator.validateBatchItem(item, 0);
        assertTrue(result.ok());
        assertEquals(3, result.request().times().size());
    }

    @Test
    void batchItemRejectsAmbiguousTimeSpecification() {
        JsonNode item = json("""
                {"decayConstants":[0.1,0.0],"initialNumbers":[1,0],
                "times":[1],"startTime":0,"endTime":1,"steps":2}""");
        var result = validator.validateBatchItem(item, 0);
        assertFalse(result.ok());
        assertNotNull(result.issues());
    }

    @Test
    void batchEnvelopeRejectsMissingOrNonArrayItems() {
        assertThrows(ValidationException.class,
                () -> validator.validateBatchEnvelope(json("{}")));
        assertThrows(ValidationException.class,
                () -> validator.validateBatchEnvelope(json("{\"items\":\"nope\"}")));
        assertThrows(ValidationException.class,
                () -> validator.validateBatchEnvelope(json("{\"items\":[]}")));
    }

    @Test
    void batchItemNullOrNonObjectDoesNotCrash() {
        var a = validator.validateBatchItem(json("null"), 0);
        assertFalse(a.ok());
        assertEquals(1, a.issues().get(0).batchIndex());
        var b = validator.validateBatchItem(json("[1,2,3]"), 1);
        assertFalse(b.ok());
        assertEquals(2, b.issues().get(0).batchIndex());
    }
}
