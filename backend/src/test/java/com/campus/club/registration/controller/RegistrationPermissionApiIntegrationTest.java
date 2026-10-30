package com.campus.club.registration.controller;

import com.campus.club.auth.JwtService;
import com.campus.club.user.entity.SysUser;
import com.campus.club.user.mapper.SysUserMapper;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.test.web.servlet.MockMvc;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 权限控制的接口测试 —— 只读，不动任何数据。
 *
 * <p>用的是种子数据里现成的归属关系：
 * <ul>
 *   <li>活动 1 属于社团 1（青年志愿者协会）</li>
 *   <li>社团 1 的负责人是用户 2（林清雅，CLUB_ADMIN）</li>
 *   <li>用户 3 是社团 2 的负责人（CLUB_ADMIN），用户 1 是平台管理员，用户 6 是学生</li>
 * </ul>
 *
 * <p>这组用例要钉住三件事：
 * 角色不对要 403、角色对但不是自己的社团也要 403、没登录要 401。
 * 只测第一件事的话，「横向越权」这条最常见的漏洞就漏掉了。
 */
@SpringBootTest
@AutoConfigureMockMvc
class RegistrationPermissionApiIntegrationTest {

    private static final long ACTIVITY_OF_CLUB_1 = 1L;
    private static final long PLATFORM_ADMIN = 1L;
    private static final long CLUB_1_OWNER = 2L;
    private static final long CLUB_2_OWNER = 3L;
    private static final long STUDENT = 6L;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SysUserMapper userMapper;

    private String bearerOf(long userId) {
        SysUser user = userMapper.selectById(userId);
        return "Bearer " + jwtService.issue(user);
    }

    @Test
    @DisplayName("平台管理员可以查看任意活动的报名名单")
    void platformAdminCanReadAttendees() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/registrations", ACTIVITY_OF_CLUB_1)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(PLATFORM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(3))
                .andExpect(jsonPath("$.data[0].nickname").isNotEmpty())
                .andExpect(jsonPath("$.data[0].checkedIn").isBoolean());
    }

    @Test
    @DisplayName("本社团负责人可以查看自己社团活动的名单")
    void clubOwnerCanReadOwnClubAttendees() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/registrations", ACTIVITY_OF_CLUB_1)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(CLUB_1_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(3));
    }

    @Test
    @DisplayName("其他社团的负责人查看别家名单 → 403/40300（横向越权被拦住）")
    void clubOwnerCannotReadOtherClubAttendees() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/registrations", ACTIVITY_OF_CLUB_1)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(CLUB_2_OWNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("普通学生查看名单 → 403/40300（角色不够）")
    void studentCannotReadAttendees() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/registrations", ACTIVITY_OF_CLUB_1)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("未登录查看名单 → 401/40100（而不是 403）")
    void anonymousCannotReadAttendees() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/registrations", ACTIVITY_OF_CLUB_1))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("活动不存在时，即便有权限也返回 404/40401")
    void missingActivityReturns404() throws Exception {
        mockMvc.perform(get("/api/activities/{id}/registrations", 999_999L)
                        .header(HttpHeaders.AUTHORIZATION, bearerOf(PLATFORM_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }
}
