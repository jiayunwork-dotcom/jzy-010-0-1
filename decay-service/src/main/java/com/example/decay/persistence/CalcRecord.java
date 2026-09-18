package com.example.decay.persistence;

import jakarta.persistence.Column;
import jakarta.persistence.Entity;
import jakarta.persistence.Id;
import jakarta.persistence.Index;
import jakarta.persistence.Lob;
import jakarta.persistence.Table;
import org.hibernate.annotations.JdbcTypeCode;
import org.hibernate.type.SqlTypes;

import java.time.Instant;

/**
 * 每次核算请求与结果的持久化记录。批量请求整体存为一条 BATCH 记录。
 */
@Entity
@Table(name = "calc_records", indexes = {
        @Index(name = "idx_records_created", columnList = "createdAt"),
        @Index(name = "idx_records_mode", columnList = "mode"),
        @Index(name = "idx_records_success", columnList = "success")
})
public class CalcRecord {

    @Id
    @Column(length = 36, nullable = false, updatable = false)
    private String id;

    /** SOLVE / GRID / BATCH。 */
    @Column(nullable = false, length = 16)
    private String mode;

    /** 请求是否整体成功（批量时允许部分失败，仍记为 true 并查看结果明细）。 */
    @Column(nullable = false)
    private boolean success;

    /** 链长度（批量信封级请求可能为 null）。 */
    private Integer chainLength;

    /** 时刻点数（批量为 null）。 */
    private Integer timePointCount;

    /** 批量组数（非批量为 null）。 */
    private Integer itemCount;

    /** 失败原因摘要（成功为 null）。 */
    @Column(length = 512)
    private String errorSummary;

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(nullable = false, updatable = false)
    private String requestJson;

    @Lob
    @JdbcTypeCode(SqlTypes.LONGVARCHAR)
    @Column(updatable = false)
    private String responseJson;

    @Column(nullable = false, updatable = false)
    private Instant createdAt;

    protected CalcRecord() {
        // JPA
    }

    public CalcRecord(String id, String mode, boolean success, Integer chainLength,
                      Integer timePointCount, Integer itemCount, String errorSummary,
                      String requestJson, String responseJson, Instant createdAt) {
        this.id = id;
        this.mode = mode;
        this.success = success;
        this.chainLength = chainLength;
        this.timePointCount = timePointCount;
        this.itemCount = itemCount;
        this.errorSummary = errorSummary;
        this.requestJson = requestJson;
        this.responseJson = responseJson;
        this.createdAt = createdAt;
    }

    public String getId() {
        return id;
    }

    public String getMode() {
        return mode;
    }

    public boolean isSuccess() {
        return success;
    }

    public Integer getChainLength() {
        return chainLength;
    }

    public Integer getTimePointCount() {
        return timePointCount;
    }

    public Integer getItemCount() {
        return itemCount;
    }

    public String getErrorSummary() {
        return errorSummary;
    }

    public String getRequestJson() {
        return requestJson;
    }

    public String getResponseJson() {
        return responseJson;
    }

    public Instant getCreatedAt() {
        return createdAt;
    }
}
