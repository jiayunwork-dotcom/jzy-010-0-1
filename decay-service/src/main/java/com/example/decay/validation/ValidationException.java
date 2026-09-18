package com.example.decay.validation;

import java.util.List;

/**
 * 输入不合法时抛出的异常，携带一条或多条结构化问题；批量场景下只用于整批信封级错误。
 */
public class ValidationException extends RuntimeException {

    private final transient List<ValidationIssue> issues;

    public ValidationException(List<ValidationIssue> issues) {
        super(issues.isEmpty() ? "输入不合法" : issues.get(0).message());
        this.issues = List.copyOf(issues);
    }

    public ValidationException(ValidationIssue issue) {
        this(List.of(issue));
    }

    public List<ValidationIssue> issues() {
        return issues;
    }
}
