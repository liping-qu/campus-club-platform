package com.campus.club.club.controller;

import com.campus.club.auth.JwtService;
import com.campus.club.user.entity.SysUser;
import com.campus.club.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * {@code GET /api/clubs/mine} 的接口测试。
 *
 * <p>这个接口只有一个用途：给「新建活动」的社团下拉框提供选项，所以它必须同时满足两件事 ——
 * <b>选得到</b>（负责人能看到自己负责的社团）和<b>选不到别人的</b>（横向越权在这里就要拦住）。
 * 前者错了功能不可用，后者错了就是把别人社团的活动创建权限开了个口子。
 *
 * <p>种子数据：社团 1 负责人用户 2、社团 2 负责人用户 3、平台管理员用户 1、学生用户 6，共 4 个社团。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ClubApiIntegrationTest {

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

    @Autowired
    private JdbcTemplate jdbcTemplate;

    private final List<Long> createdClubIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : createdClubIds) {
            jdbcTemplate.update("DELETE FROM club WHERE id = ?", id);
        }
        createdClubIds.clear();
    }

    private String bearer(long userId) {
        SysUser user = userMapper.selectById(userId);
        return "Bearer " + jwtService.issue(user);
    }

    private long createDissolvedClub(long ownerId) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO club (name, category, description, owner_user_id, member_count, status) "
                            + "VALUES (?, '公益志愿', '仅用于测试', ?, 0, 0)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, "已解散社团-" + UUID.randomUUID().toString().substring(0, 8));
            ps.setLong(2, ownerId);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        createdClubIds.add(id);
        return id;
    }

    @Test
    @DisplayName("社团负责人只看到自己负责的社团")
    void clubOwnerSeesOwnClubOnly() throws Exception {
        mockMvc.perform(get("/api/clubs/mine").header(HttpHeaders.AUTHORIZATION, bearer(CLUB_1_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[0].name").value("青年志愿者协会"));

        mockMvc.perform(get("/api/clubs/mine").header(HttpHeaders.AUTHORIZATION, bearer(CLUB_2_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].id").value(2))
                .andExpect(jsonPath("$.data[0].name").value("摄影协会"));
    }

    @Test
    @DisplayName("平台管理员看到全部 4 个正常社团，按 id 升序")
    void platformAdminSeesAllClubs() throws Exception {
        mockMvc.perform(get("/api/clubs/mine").header(HttpHeaders.AUTHORIZATION, bearer(PLATFORM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4))
                .andExpect(jsonPath("$.data[0].id").value(1))
                .andExpect(jsonPath("$.data[3].id").value(4));
    }

    @Test
    @DisplayName("已解散的社团不出现在下拉框里（status=0 被过滤）")
    void dissolvedClubIsHidden() throws Exception {
        createDissolvedClub(CLUB_1_OWNER);

        // 负责人自己名下的社团从 1 个变成 2 个，但已解散的那个不返回
        mockMvc.perform(get("/api/clubs/mine").header(HttpHeaders.AUTHORIZATION, bearer(CLUB_1_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1));

        // 平台管理员的总数也不变
        mockMvc.perform(get("/api/clubs/mine").header(HttpHeaders.AUTHORIZATION, bearer(PLATFORM_ADMIN)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(4));
    }

    @Test
    @DisplayName("学生没有管理社团的资格 → 403/40300")
    void studentCannotListManageableClubs() throws Exception {
        mockMvc.perform(get("/api/clubs/mine").header(HttpHeaders.AUTHORIZATION, bearer(STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));
    }

    @Test
    @DisplayName("未登录 → 401/40100")
    void anonymousIsRejected() throws Exception {
        mockMvc.perform(get("/api/clubs/mine"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }
}
