package com.campus.club.activity.controller;

import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 活动接口集成测试。
 * <p>
 * 连接 docker-compose 起的真实 MySQL，因此断言的是「SQL 真的能跑通」，
 * 而不是 Mock 出来的假数据。
 * <p>
 * 注意：断言依赖 {@code 02_seed.sql} 的种子数据（5 个活动、4 个社团）。
 * 如果手工改过数据库，这些用例需要同步调整。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityApiIntegrationTest {

    @Autowired
    private MockMvc mockMvc;

    @Test
    @DisplayName("默认列表返回全部已发布活动，并带出社团名称")
    void listReturnsSeedData() throws Exception {
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.total").value(5))
                .andExpect(jsonPath("$.data.records[0].clubName").isNotEmpty())
                .andExpect(jsonPath("$.data.records[0].phase").isNotEmpty());
    }

    @Test
    @DisplayName("关键词筛选只返回标题或地点命中的活动")
    void keywordFilterWorks() throws Exception {
        mockMvc.perform(get("/api/activities").param("keyword", "摄影"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].title").value("秋日校园摄影采风"));
    }

    @Test
    @DisplayName("phase=OPEN 只返回报名开放且未满员的活动")
    void openPhaseFilterWorks() throws Exception {
        mockMvc.perform(get("/api/activities").param("phase", "OPEN"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(2));
    }

    @Test
    @DisplayName("phase=FULL 能筛出已满员活动，且剩余名额为 0")
    void fullPhaseFilterWorks() throws Exception {
        mockMvc.perform(get("/api/activities").param("phase", "FULL"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.total").value(1))
                .andExpect(jsonPath("$.data.records[0].remaining").value(0));
    }

    @Test
    @DisplayName("详情返回规则列表，且不泄漏原始 rules 字段")
    void detailReturnsRuleList() throws Exception {
        mockMvc.perform(get("/api/activities/1"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(1))
                .andExpect(jsonPath("$.data.ruleList").isArray())
                .andExpect(jsonPath("$.data.ruleList[0]").isNotEmpty())
                .andExpect(jsonPath("$.data.rules").doesNotExist())
                .andExpect(jsonPath("$.data.creatorName").isNotEmpty());
    }

    @Test
    @DisplayName("活动不存在返回 404 与业务码 40401")
    void detailNotFoundReturns404() throws Exception {
        mockMvc.perform(get("/api/activities/999999"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("size 超过上限返回 400 与业务码 40000")
    void invalidSizeReturns400() throws Exception {
        mockMvc.perform(get("/api/activities").param("size", "1000"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("活动 ID 非正整数返回 400")
    void nonPositiveIdReturns400() throws Exception {
        mockMvc.perform(get("/api/activities/0"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("分页参数生效，第二页数据量与 size 一致")
    void paginationWorks() throws Exception {
        mockMvc.perform(get("/api/activities").param("page", "2").param("size", "2"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.page").value(2))
                .andExpect(jsonPath("$.data.size").value(2))
                .andExpect(jsonPath("$.data.pages").value(3))
                .andExpect(jsonPath("$.data.records.length()").value(2));
    }
}
