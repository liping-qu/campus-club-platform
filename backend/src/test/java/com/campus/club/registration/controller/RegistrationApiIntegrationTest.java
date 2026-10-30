package com.campus.club.registration.controller;

import com.campus.club.auth.JwtService;
import com.campus.club.support.RegistrationTestData;
import com.campus.club.user.entity.SysUser;
import com.campus.club.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.util.ArrayList;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 报名接口的契约测试。
 * <p>
 * 与并发测试的分工：这里验证「HTTP 语义对不对」——
 * 状态码、业务码、鉴权边界、幂等取消；并发一致性由 {@code RegistrationConcurrencyTest} 负责。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationApiIntegrationTest {

    private static final String AUTH_HEADER = "Authorization";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SysUserMapper userMapper;

    private Long activityId;
    private List<Long> userIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        RegistrationTestData.cleanup(jdbcTemplate, activityId, userIds);
        activityId = null;
        userIds = new ArrayList<>();
    }

    /**
     * 建一个测试用户并签发它的令牌。
     * <p>
     * 这里不走登录接口：登录本身有专门的用例覆盖，报名用例关心的是「带着有效身份能否报名」。
     * 每个用例都跑一次 BCrypt 会明显拖慢测试，也没有额外收益。
     */
    private String bearerForNewUser() {
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 1);
        return bearer(userIds.get(0));
    }

    private String bearer(long userId) {
        SysUser user = userMapper.selectById(userId);
        return "Bearer " + jwtService.issue(user);
    }

    @Test
    @DisplayName("缺少身份信息时报名返回 401，且不产生任何数据")
    void registerWithoutIdentityReturns401() throws Exception {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 5);

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        assertEquals(0, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("报名成功返回最新计数，重复报名返回 409/40902")
    void registerThenDuplicateReturnsConflict() throws Exception {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 5);
        String auth = bearerForNewUser();

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.joinedCount").value(1))
                .andExpect(jsonPath("$.data.remaining").value(4));

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40902));

        assertEquals(1, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("名额已满返回 409/40901")
    void registerFullActivityReturnsConflict() throws Exception {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 1);
        userIds = RegistrationTestData.createUsers(jdbcTemplate, 2);

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId)
                        .header(AUTH_HEADER, bearer(userIds.get(0))))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId)
                        .header(AUTH_HEADER, bearer(userIds.get(1))))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40901));
    }

    @Test
    @DisplayName("报名状态查询：未报名 NONE → 报名 REGISTERED → 取消后 NONE")
    void myRegistrationStatusChanges() throws Exception {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 5);
        String auth = bearerForNewUser();

        mockMvc.perform(get("/api/activities/{id}/registrations/me", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("NONE"));

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/activities/{id}/registrations/me", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("REGISTERED"))
                .andExpect(jsonPath("$.data.registeredAt").isNotEmpty());

        mockMvc.perform(delete("/api/activities/{id}/registrations/me", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        mockMvc.perform(get("/api/activities/{id}/registrations/me", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value("CANCELLED"));
    }

    @Test
    @DisplayName("重复取消返回 409/40903，计数不会继续下降")
    void doubleCancelReturnsConflict() throws Exception {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 5);
        String auth = bearerForNewUser();

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/activities/{id}/registrations/me", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/activities/{id}/registrations/me", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        assertEquals(0, RegistrationTestData.joinedCount(jdbcTemplate, activityId));
    }

    @Test
    @DisplayName("我的报名列表包含刚报名的活动，并带出推导出的展示阶段")
    void myRegistrationsListContainsActivity() throws Exception {
        activityId = RegistrationTestData.createOpenActivity(jdbcTemplate, 5);
        String auth = bearerForNewUser();

        mockMvc.perform(post("/api/activities/{id}/registrations", activityId).header(AUTH_HEADER, auth))
                .andExpect(status().isOk());

        mockMvc.perform(get("/api/registrations/me").header(AUTH_HEADER, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(activityId))
                .andExpect(jsonPath("$.data[0].registrationStatus").value(1))
                .andExpect(jsonPath("$.data[0].phase").value("OPEN"))
                .andExpect(jsonPath("$.data[0].clubName").isNotEmpty());
    }

    @Test
    @DisplayName("我的报名列表未登录返回 401")
    void myRegistrationsWithoutIdentityReturns401() throws Exception {
        mockMvc.perform(get("/api/registrations/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("活动 ID 非正整数返回 400")
    void nonPositiveActivityIdReturns400() throws Exception {
        mockMvc.perform(post("/api/activities/{id}/registrations", 0).header(AUTH_HEADER, bearer(6L)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }
}
