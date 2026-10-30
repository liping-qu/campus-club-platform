package com.campus.club.auth;

import com.campus.club.support.RegistrationTestData;
import com.campus.club.user.entity.SysUser;
import com.campus.club.user.mapper.SysUserMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.test.web.servlet.MockMvc;

import java.time.Duration;
import java.util.ArrayList;
import java.util.List;

import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 认证接口的契约测试。
 * <p>
 * 重点不只是「登录成功」，而是几条<b>失败分支的语义</b>：
 * 不带令牌、令牌过期、令牌被篡改、账号被禁用，四种情况必须能被前端区分开，
 * 否则前端只能一律弹「操作失败」，用户不知道该重新登录还是该找管理员。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AuthApiIntegrationTest {

    private static final String LOGIN_BODY = "{\"username\":\"%s\",\"password\":\"%s\"}";

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SysUserMapper userMapper;

    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        createdUserIds.forEach(id -> jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id));
        createdUserIds.clear();
    }

    private RegistrationTestData.TestUser newUser(int status) {
        RegistrationTestData.TestUser user = RegistrationTestData.createUser(jdbcTemplate, status);
        createdUserIds.add(user.id());
        return user;
    }

    private String bearerFor(long userId, Duration ttl) {
        SysUser user = userMapper.selectById(userId);
        return "Bearer " + jwtService.issue(user, ttl);
    }

    @Test
    @DisplayName("正确的用户名密码 → 返回令牌与用户信息，且不泄漏密码哈希")
    void loginSucceeds() throws Exception {
        RegistrationTestData.TestUser user = newUser(1);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY.formatted(user.username(), "123456")))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.token").isNotEmpty())
                .andExpect(jsonPath("$.data.tokenType").value("Bearer"))
                .andExpect(jsonPath("$.data.expiresIn").value(7200))
                .andExpect(jsonPath("$.data.user.id").value(user.id()))
                .andExpect(jsonPath("$.data.user.username").value(user.username()))
                .andExpect(jsonPath("$.data.user.role").value("STUDENT"))
                // 响应体里绝不能出现密码相关字段
                .andExpect(jsonPath("$.data.user.passwordHash").doesNotExist())
                .andExpect(jsonPath("$.data.user.password").doesNotExist());
    }

    @Test
    @DisplayName("密码错误 → 401/40101")
    void loginWithWrongPasswordFails() throws Exception {
        RegistrationTestData.TestUser user = newUser(1);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY.formatted(user.username(), "wrong-password")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("用户不存在 → 与密码错误同一个码，避免泄漏账号是否存在")
    void loginWithUnknownUserReturnsSameCode() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY.formatted("no-such-user-x", "123456")))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40101));
    }

    @Test
    @DisplayName("账号被禁用 → 403/40301")
    void loginWithDisabledAccountFails() throws Exception {
        RegistrationTestData.TestUser user = newUser(0);

        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY.formatted(user.username(), "123456")))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40301));
    }

    @Test
    @DisplayName("用户名为空 → 400/40000，由参数校验拦下，不进业务层")
    void loginWithBlankUsernameReturns400() throws Exception {
        mockMvc.perform(post("/api/auth/login")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(LOGIN_BODY.formatted("", "123456")))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("不带令牌访问受保护接口 → 401/40100")
    void protectedEndpointWithoutTokenReturns401() throws Exception {
        mockMvc.perform(get("/api/auth/me"))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("令牌被篡改（签名不对）→ 401/40103")
    void tamperedTokenReturns401() throws Exception {
        RegistrationTestData.TestUser user = newUser(1);
        String token = bearerFor(user.id(), Duration.ofHours(1));
        // 改动签名部分的最后一个字符，模拟令牌被篡改
        String tampered = token.substring(0, token.length() - 1) + (token.endsWith("A") ? "B" : "A");

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, tampered))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40103));
    }

    @Test
    @DisplayName("令牌已过期 → 401/40102，与「凭证无效」区分开")
    void expiredTokenReturns401() throws Exception {
        RegistrationTestData.TestUser user = newUser(1);
        String expired = bearerFor(user.id(), Duration.ofSeconds(-60));

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, expired))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40102));
    }

    @Test
    @DisplayName("带有效令牌 → 返回本人资料")
    void meReturnsCurrentUser() throws Exception {
        RegistrationTestData.TestUser user = newUser(1);

        mockMvc.perform(get("/api/auth/me").header(HttpHeaders.AUTHORIZATION, bearerFor(user.id(), Duration.ofHours(1))))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.id()))
                .andExpect(jsonPath("$.data.username").value(user.username()))
                .andExpect(jsonPath("$.data.roleText").value("学生"));
    }

    @Test
    @DisplayName("身份只认令牌：另加一个 userId 请求头不会改变身份")
    void userIdHeaderIsIgnored() throws Exception {
        RegistrationTestData.TestUser user = newUser(1);
        RegistrationTestData.TestUser other = newUser(1);

        mockMvc.perform(get("/api/auth/me")
                        .header(HttpHeaders.AUTHORIZATION, bearerFor(user.id(), Duration.ofHours(1)))
                        // 第三阶段遗留的伪造入口，现在必须完全无效
                        .header("X-User-Id", String.valueOf(other.id())))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.id").value(user.id()));
    }

    @Test
    @DisplayName("公开接口不需要令牌")
    void publicActivityEndpointStaysPublic() throws Exception {
        mockMvc.perform(get("/api/activities"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));
    }
}
