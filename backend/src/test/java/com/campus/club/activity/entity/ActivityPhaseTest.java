package com.campus.club.activity.entity;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import java.time.LocalDateTime;

import static org.junit.jupiter.api.Assertions.assertEquals;

/**
 * 展示阶段推导的单元测试。
 *
 * <p>这是纯函数，但它是「同一个活动在列表、详情、我的报名三处显示同一个状态」的唯一保证，
 * 而且顺序写错不会报错、只会显示错 —— 所以值得单独钉住。
 */
class ActivityPhaseTest {

    private static final LocalDateTime NOW = LocalDateTime.of(2026, 10, 30, 20, 0);

    private ActivityPhase resolve(int status, int capacity, int joined,
                                  LocalDateTime start, LocalDateTime end, LocalDateTime deadline) {
        return ActivityPhase.resolve(status, capacity, joined, start, end, deadline, NOW);
    }

    @Test
    @DisplayName("草稿优先于一切：报名截止时间在未来也不能算成「报名中」")
    void draftWinsOverOpen() {
        ActivityPhase phase = resolve(ActivityStatus.DRAFT, 30, 0,
                NOW.plusDays(3), NOW.plusDays(4), NOW.plusDays(2));

        assertEquals(ActivityPhase.DRAFT, phase);
        assertEquals("草稿", phase.getText());
    }

    @Test
    @DisplayName("已下架显示为「已下架」，不伪装成「已结束」")
    void cancelledMapsToCancelled() {
        ActivityPhase phase = resolve(ActivityStatus.CANCELLED, 30, 0,
                NOW.plusDays(3), NOW.plusDays(4), NOW.plusDays(2));

        assertEquals(ActivityPhase.CANCELLED, phase);
        assertEquals("已下架", phase.getText());
    }

    @Test
    @DisplayName("已结束覆盖满员：结束时间过了就是「已结束」，不是「已满员」")
    void finishedWinsOverFull() {
        ActivityPhase phase = resolve(ActivityStatus.PUBLISHED, 2, 2,
                NOW.minusDays(2), NOW.minusDays(1), NOW.minusDays(3));

        assertEquals(ActivityPhase.FINISHED, phase);
    }

    @Test
    @DisplayName("满员优先于报名中：名额用完了就不能再显示「报名中」")
    void fullWinsOverOpen() {
        ActivityPhase phase = resolve(ActivityStatus.PUBLISHED, 2, 2,
                NOW.plusDays(3), NOW.plusDays(4), NOW.plusDays(2));

        assertEquals(ActivityPhase.FULL, phase);
    }

    @Test
    @DisplayName("截止后、开始前 → 即将开始")
    void betweenDeadlineAndStartIsUpcoming() {
        ActivityPhase phase = resolve(ActivityStatus.PUBLISHED, 30, 0,
                NOW.plusHours(6), NOW.plusHours(9), NOW.minusHours(1));

        assertEquals(ActivityPhase.UPCOMING, phase);
    }

    @Test
    @DisplayName("截止前且有余量 → 报名中")
    void beforeDeadlineIsOpen() {
        ActivityPhase phase = resolve(ActivityStatus.PUBLISHED, 30, 0,
                NOW.plusDays(3), NOW.plusDays(4), NOW.plusDays(2));

        assertEquals(ActivityPhase.OPEN, phase);
    }
}
