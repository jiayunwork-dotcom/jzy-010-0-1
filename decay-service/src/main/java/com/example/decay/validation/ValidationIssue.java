package com.example.decay.validation;

/**
 * 一条可区分的结构化输入问题。
 *
 * @param code          机器可读错误码
 * @param message       中文可读说明
 * @param parameter     出问题的参数名（如 lambda / initialNumber / time）
 * @param nuclideIndex  核素序号（从 1 开始；不针对具体核素时为 null）
 * @param batchIndex    批量请求中的组序号（从 1 开始；非批量为 null）
 */
public record ValidationIssue(String code,
                              String message,
                              String parameter,
                              Integer nuclideIndex,
                              Integer batchIndex) {

    public static ValidationIssue of(String code, String message, String parameter) {
        return new ValidationIssue(code, message, parameter, null, null);
    }
}
