package com.limou.agent_demo.decision.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 执行计划中的一个步骤。
 * <p>
 * 每个步骤描述了一个原子任务：要做什么、可能用到哪个工具、期望的产出是什么。
 * 在执行过程中记录实际观察结果和状态变更。
 *
 * @author lubo
 * @since 2026-07-25
 */
@Data
@Builder
public class PlanStep {

    /** 步骤序号（从 0 开始） */
    private int order;

    /** 步骤描述 — 用自然语言说明这一步要做什么 */
    private String description;

    /** 预期会使用的工具名（可为 null，表示不需要工具调用） */
    private String expectedTool;

    /** 预期的产出/结果描述 */
    private String expectedOutcome;

    /** 当前状态 */
    @Builder.Default
    private StepStatus status = StepStatus.PENDING;

    /** 实际观察结果 — 执行后回填 */
    private String observation;

    @Builder.Default
    private LocalDateTime startedAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    // ---- 便捷方法 ----

    public void markInProgress() {
        this.status = StepStatus.IN_PROGRESS;
        this.startedAt = LocalDateTime.now();
    }

    public void markCompleted(String observation) {
        this.status = StepStatus.COMPLETED;
        this.observation = observation;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed(String error) {
        this.status = StepStatus.FAILED;
        this.observation = error;
        this.completedAt = LocalDateTime.now();
    }

    public void markSkipped(String reason) {
        this.status = StepStatus.SKIPPED;
        this.observation = reason;
        this.completedAt = LocalDateTime.now();
    }

    public boolean isPending() {
        return status == StepStatus.PENDING;
    }

    public boolean isCompleted() {
        return status == StepStatus.COMPLETED;
    }

    public boolean isFailed() {
        return status == StepStatus.FAILED;
    }

    // ---- 内部枚举 ----

    public enum StepStatus {
        PENDING,
        IN_PROGRESS,
        COMPLETED,
        FAILED,
        SKIPPED
    }
}
