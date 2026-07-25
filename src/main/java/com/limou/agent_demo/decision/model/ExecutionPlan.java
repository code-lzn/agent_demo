package com.limou.agent_demo.decision.model;

import lombok.Builder;
import lombok.Data;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.Comparator;
import java.util.List;
import java.util.UUID;
import java.util.stream.Collectors;

/**
 * 任务执行计划 — 由 {@code TaskPlanner} 生成，{@code DecisionEngine} 驱动执行。
 * <p>
 * 计划包含一个总目标、推理过程和一组有序的步骤。
 * 执行过程中，步骤状态从 PENDING → IN_PROGRESS → COMPLETED/FAILED/SKIPPED 流转。
 *
 * @author lubo
 * @since 2026-07-25
 */
@Data
@Builder
public class ExecutionPlan {

    /** 计划唯一 ID */
    @Builder.Default
    private String id = UUID.randomUUID().toString();

    /** 用户的总目标 — 用一句话概括用户想要什么 */
    private String goal;

    /** 推理过程 — 为什么采用这个计划 */
    private String reasoning;

    /** 有序步骤列表 */
    @Builder.Default
    private List<PlanStep> steps = new ArrayList<>();

    /** 计划整体状态 */
    @Builder.Default
    private PlanStatus status = PlanStatus.CREATED;

    /** 最大执行轮次（防止无限循环） */
    @Builder.Default
    private int maxRounds = 5;

    @Builder.Default
    private LocalDateTime createdAt = LocalDateTime.now();

    private LocalDateTime completedAt;

    // ---- 便捷方法 ----

    /** 获取仍未完成的步骤 */
    public List<PlanStep> getPendingSteps() {
        return steps.stream()
                .filter(PlanStep::isPending)
                .sorted(Comparator.comparingInt(PlanStep::getOrder))
                .collect(Collectors.toList());
    }

    /** 获取下一步待执行步骤 */
    public PlanStep getNextPendingStep() {
        return steps.stream()
                .filter(PlanStep::isPending)
                .min(Comparator.comparingInt(PlanStep::getOrder))
                .orElse(null);
    }

    /** 所有步骤是否都已完成（成功或跳过） */
    public boolean isAllStepsResolved() {
        return steps.stream().allMatch(s -> s.isCompleted() || s.isFailed()
                || s.getStatus() == PlanStep.StepStatus.SKIPPED);
    }

    /** 是否有失败的步骤 */
    public boolean hasFailures() {
        return steps.stream().anyMatch(PlanStep::isFailed);
    }

    /** 成功完成的步骤数 */
    public long getCompletedCount() {
        return steps.stream().filter(PlanStep::isCompleted).count();
    }

    /** 步骤总数 */
    public int getTotalSteps() {
        return steps.size();
    }

    /** 获取进度摘要 */
    public String getProgressSummary() {
        return String.format("步骤 %d/%d 完成 (成功:%d, 失败:%d, 跳过:%d)",
                steps.stream().filter(s -> !s.isPending()
                        && s.getStatus() != PlanStep.StepStatus.IN_PROGRESS).count(),
                steps.size(),
                getCompletedCount(),
                steps.stream().filter(PlanStep::isFailed).count(),
                steps.stream().filter(s -> s.getStatus() == PlanStep.StepStatus.SKIPPED).count());
    }

    public void markInProgress() {
        this.status = PlanStatus.IN_PROGRESS;
    }

    public void markCompleted() {
        this.status = PlanStatus.COMPLETED;
        this.completedAt = LocalDateTime.now();
    }

    public void markFailed() {
        this.status = PlanStatus.FAILED;
        this.completedAt = LocalDateTime.now();
    }

    // ---- 内部枚举 ----

    public enum PlanStatus {
        CREATED,
        IN_PROGRESS,
        COMPLETED,
        FAILED
    }
}
