package com.campus.club.attendance.controller;

import com.campus.club.auth.JwtService;
import com.campus.club.cache.ActivityCache;
import com.campus.club.user.entity.SysUser;
import com.campus.club.user.mapper.SysUserMapper;
import com.fasterxml.jackson.databind.JsonNode;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.autoconfigure.web.servlet.AutoConfigureMockMvc;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.math.BigDecimal;
import java.nio.charset.StandardCharsets;
import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNotNull;
import static org.junit.jupiter.api.Assertions.assertTrue;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.delete;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.get;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.post;
import static org.springframework.test.web.servlet.request.MockMvcRequestBuilders.put;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.jsonPath;
import static org.springframework.test.web.servlet.result.MockMvcResultMatchers.status;

/**
 * 签到接口的集成测试（真连 MySQL + Redis）。
 *
 * <p>单测已经把「哪个分支抛哪个码」钉住了，这里只回答单测回答不了的问题：
 * <ul>
 *   <li>签到真的落库了吗？唯一键真的拦得住第二次吗？</li>
 *   <li>时间窗用的是数据库里的活动时间，而不是请求里的某个字段吗？</li>
 *   <li>代签写下的 method 真的是 ADMIN，事后能按人查出来吗？</li>
 *   <li>「已签到不能取消报名」在真库的事务里也成立吗？</li>
 * </ul>
 *
 * <p><b>为什么自己造活动</b>：种子活动的起止时间是写死的（活动 1 是 2026-10-12），
 * 而签到窗口是相对「现在」计算的 —— 只有自己造一条「现在正好在窗口内 / 太早 / 已结束」的活动，
 * 才能不依赖跑测试的日期。造出来的活动在 {@link #tearDown()} 里删掉（签到、报名随活动级联删除），
 * 并顺手失效缓存，避免把活动列表总数带偏。
 *
 * <p>种子归属关系：用户 1 平台管理员、用户 2 社团 1 负责人、用户 3 社团 2 负责人、用户 6/7/8 学生。
 */
@SpringBootTest
@AutoConfigureMockMvc
class AttendanceApiIntegrationTest {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;

    private static final long PLATFORM_ADMIN = 1L;
    private static final long CLUB_1_OWNER = 2L;
    private static final long CLUB_2_OWNER = 3L;
    private static final long STUDENT = 6L;
    private static final long OTHER_STUDENT = 7L;
    private static final long CLUB_1 = 1L;
    private static final long SEED_CREATOR = 2L;

    private static final int DRAFT = 0;
    private static final int PUBLISHED = 1;
    private static final int CANCELLED = 2;

    @Autowired
    private MockMvc mockMvc;

    @Autowired
    private JwtService jwtService;

    @Autowired
    private SysUserMapper userMapper;

    @Autowired
    private JdbcTemplate jdbcTemplate;

    @Autowired
    private ActivityCache activityCache;

    @Autowired
    private ObjectMapper objectMapper;

    private final List<Long> createdActivityIds = new ArrayList<>();
    private final List<Long> createdUserIds = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : createdActivityIds) {
            // 先失效缓存再删行：否则详情缓存里会留下一条已经不存在（或已被删掉）的活动
            activityCache.evict(id);
            jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
        }
        for (Long id : createdUserIds) {
            jdbcTemplate.update("DELETE FROM sys_user WHERE id = ?", id);
        }
        createdActivityIds.clear();
        createdUserIds.clear();
    }

    // ------------------------------------------------------------------ 夹具

    private String bearer(long userId) {
        SysUser user = userMapper.selectById(userId);
        return "Bearer " + jwtService.issue(user);
    }

    private static LocalDateTime minutesFromNow(long minutes) {
        return LocalDateTime.now().plusMinutes(minutes);
    }

    /**
     * 造一条活动。start/end 直接给定，保证「窗口内/太早/已结束」是确定的。
     */
    private long createActivity(LocalDateTime start, LocalDateTime end, int status) {
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO activity (club_id, title, description, location, category, "
                            + "start_time, end_time, signup_deadline, capacity, joined_count, status, "
                            + "cover_color, rules, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, CLUB_1);
            ps.setString(2, "签到测试活动-" + UUID.randomUUID().toString().substring(0, 8));
            ps.setString(3, "仅用于自动化测试");
            ps.setString(4, "测试地点");
            ps.setString(5, "公益志愿");
            ps.setObject(6, start);
            ps.setObject(7, end);
            ps.setObject(8, start.minusDays(1));
            ps.setInt(9, 50);
            ps.setInt(10, status);
            ps.setString(11, "#c8d9ff");
            ps.setString(12, "测试规则");
            ps.setLong(13, SEED_CREATOR);
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        createdActivityIds.add(id);
        return id;
    }

    /** 现在正好在签到窗口内：10 分钟前开始、2 小时后结束 */
    private long liveActivity() {
        return createActivity(minutesFromNow(-10), minutesFromNow(120), PUBLISHED);
    }

    /** 有效报名。status=1 才算「还在活动里」 */
    private void register(long activityId, long userId) {
        jdbcTemplate.update("INSERT INTO activity_registration (activity_id, user_id, status, registered_at) "
                + "VALUES (?, ?, 1, NOW())", activityId, userId);
        jdbcTemplate.update("UPDATE activity SET joined_count = joined_count + 1 WHERE id = ?", activityId);
    }

    private int attendanceRows(long activityId, long userId) {
        Integer count = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_attendance WHERE activity_id = ? AND user_id = ?",
                Integer.class, activityId, userId);
        return count == null ? 0 : count;
    }

    private JsonNode dataOf(MvcResult result) throws Exception {
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    // ------------------------------------------------------------------ 本人签到

    @Test
    @DisplayName("窗口内本人签到：真实落库、记 MANUAL、时长从 0 起，再查状态是已签到")
    void selfCheckInInsideWindowWritesRecord() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);
        String student = bearer(STUDENT);

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedIn").value(true))
                .andExpect(jsonPath("$.data.method").value("MANUAL"))
                .andExpect(jsonPath("$.data.volunteerHours").value(0));

        assertEquals(1, attendanceRows(activityId, STUDENT), "签到必须真的写进 activity_attendance");

        mockMvc.perform(get("/api/activities/{id}/check-in/me", activityId).header(AUTH, student))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkedIn").value(true))
                .andExpect(jsonPath("$.data.checkInOpen").value(true))
                .andExpect(jsonPath("$.data.method").value("MANUAL"));

        // 负责人能在名单里看到他，且方式为自签
        mockMvc.perform(get("/api/activities/{id}/attendances", activityId).header(AUTH, bearer(CLUB_1_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.length()").value(1))
                .andExpect(jsonPath("$.data[0].userId").value((int) STUDENT))
                .andExpect(jsonPath("$.data[0].method").value("MANUAL"));
    }

    @Test
    @DisplayName("未签到查状态返回 checkedIn=false 而不是 404")
    void myCheckInBeforeCheckInIsFalse() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);

        mockMvc.perform(get("/api/activities/{id}/check-in/me", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andExpect(jsonPath("$.data.checkedIn").value(false))
                .andExpect(jsonPath("$.data.checkInOpen").value(true));
    }

    @Test
    @DisplayName("重复签到 → 409/40906，库里仍然只有一条")
    void duplicateCheckInIsRejected() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);
        String student = bearer(STUDENT);

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, student))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        assertEquals(1, attendanceRows(activityId, STUDENT));
    }

    @Test
    @DisplayName("太早签到 → 409/40911，库里不写记录")
    void checkInTooEarlyIsRejected() throws Exception {
        long activityId = createActivity(minutesFromNow(180), minutesFromNow(300), PUBLISHED);
        register(activityId, STUDENT);

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40911));

        assertEquals(0, attendanceRows(activityId, STUDENT));

        // 「为什么不能签」也要能提前告诉前端，否则按钮只能靠试出来
        mockMvc.perform(get("/api/activities/{id}/check-in/me", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.checkInOpen").value(false));
    }

    @Test
    @DisplayName("活动已结束 → 409/40912，提示去找负责人补录")
    void checkInAfterEndIsRejected() throws Exception {
        long activityId = createActivity(minutesFromNow(-180), minutesFromNow(-60), PUBLISHED);
        register(activityId, STUDENT);

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40912))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("补录")));
    }

    @Test
    @DisplayName("没报名的人签到 → 409/40903（「到场即报名」这条不变量不能被绕过）")
    void unregisteredUserCannotCheckIn() throws Exception {
        long activityId = liveActivity();

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(OTHER_STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));

        assertEquals(0, attendanceRows(activityId, OTHER_STUDENT));
    }

    @Test
    @DisplayName("已取消报名的人签到 → 409/40903，不能给退出的人记时长")
    void cancelledRegistrationCannotCheckIn() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);
        jdbcTemplate.update("UPDATE activity_registration SET status = 0, cancelled_at = NOW() "
                + "WHERE activity_id = ? AND user_id = ?", activityId, STUDENT);

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903));
    }

    @Test
    @DisplayName("已下架的活动签到 → 409/40904，而不是 404（状态是可解释的）")
    void cancelledActivityCannotCheckIn() throws Exception {
        long activityId = createActivity(minutesFromNow(-10), minutesFromNow(120), CANCELLED);
        register(activityId, STUDENT);

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40904));
    }

    @Test
    @DisplayName("未登录签到 → 401/40100（不是 403）")
    void anonymousCannotCheckIn() throws Exception {
        long activityId = liveActivity();

        mockMvc.perform(post("/api/activities/{id}/check-in", activityId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));
    }

    @Test
    @DisplayName("活动不存在 → 404/40401")
    void checkInOnMissingActivityReturns404() throws Exception {
        mockMvc.perform(post("/api/activities/{id}/check-in", 999_999L).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    // ------------------------------------------------------------------ 代签

    @Test
    @DisplayName("代签不受窗口限制：活动结束后补录也成功，记 ADMIN 与指定时长")
    void adminCheckInBypassesWindow() throws Exception {
        long activityId = createActivity(minutesFromNow(-300), minutesFromNow(-180), PUBLISHED);
        register(activityId, OTHER_STUDENT);

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d, \"volunteerHours\": 2.5}".formatted(OTHER_STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.userId").value((int) OTHER_STUDENT))
                .andExpect(jsonPath("$.data.method").value("ADMIN"))
                .andExpect(jsonPath("$.data.volunteerHours").value(2.5));

        assertEquals(1, attendanceRows(activityId, OTHER_STUDENT));
        BigDecimal hours = jdbcTemplate.queryForObject(
                "SELECT volunteer_hours FROM activity_attendance WHERE activity_id = ? AND user_id = ?",
                BigDecimal.class, activityId, OTHER_STUDENT);
        assertEquals(0, new BigDecimal("2.5").compareTo(hours), "DECIMAL(4,1) 必须精确存下 2.5");

        mockMvc.perform(get("/api/activities/{id}/attendances", activityId).header(AUTH, bearer(CLUB_1_OWNER)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data[0].method").value("ADMIN"))
                .andExpect(jsonPath("$.data[0].volunteerHours").value(2.5));
    }

    @Test
    @DisplayName("代签：目标用户没报名 → 409/40903，提示「该用户」")
    void adminCheckInTargetMustBeRegistered() throws Exception {
        long activityId = createActivity(minutesFromNow(-300), minutesFromNow(-180), PUBLISHED);

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d}".formatted(STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40903))
                .andExpect(jsonPath("$.message").value(org.hamcrest.Matchers.containsString("该用户")));
    }

    @Test
    @DisplayName("代签：本人已签到 → 409/40906，不覆盖时长")
    void adminCheckInDuplicateIsRejected() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);
        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isOk());

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d, \"volunteerHours\": 3.0}".formatted(STUDENT)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40906));

        BigDecimal hours = jdbcTemplate.queryForObject(
                "SELECT volunteer_hours FROM activity_attendance WHERE activity_id = ? AND user_id = ?",
                BigDecimal.class, activityId, STUDENT);
        assertEquals(0, BigDecimal.ZERO.compareTo(hours), "被拒绝的代签不能把自签的 0 时长改成 3.0");
    }

    @Test
    @DisplayName("代签：别家社团负责人 → 403/40300；学生 → 403/40300；未登录 → 401")
    void adminCheckInRequiresRoleAndOwnership() throws Exception {
        long activityId = createActivity(minutesFromNow(-300), minutesFromNow(-180), PUBLISHED);
        register(activityId, OTHER_STUDENT);
        String body = "{\"userId\": %d}".formatted(OTHER_STUDENT);

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, bearer(CLUB_2_OWNER))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, bearer(STUDENT))
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .contentType(MediaType.APPLICATION_JSON).content(body))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        assertEquals(0, attendanceRows(activityId, OTHER_STUDENT));
    }

    @Test
    @DisplayName("代签：用户 ID 不合法/时长超范围 → 400/40000，参数问题不落到业务层")
    void adminCheckInValidatesBody() throws Exception {
        long activityId = createActivity(minutesFromNow(-300), minutesFromNow(-180), PUBLISHED);
        String owner = bearer(CLUB_1_OWNER);

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": 0}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, owner).contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d, \"volunteerHours\": 1000}".formatted(OTHER_STUDENT)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    // ------------------------------------------------------------------ 志愿时长

    @Test
    @DisplayName("设置志愿时长：改库生效，「我的签到」累计值同步变化")
    void updateVolunteerHoursAffectsSummary() throws Exception {
        long activityId = createActivity(minutesFromNow(-300), minutesFromNow(-180), PUBLISHED);
        register(activityId, STUDENT);
        mockMvc.perform(post("/api/activities/{id}/attendances", activityId)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"userId\": %d, \"volunteerHours\": 1.0}".formatted(STUDENT)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/activities/{id}/attendances/{userId}/hours", activityId, STUDENT)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volunteerHours\": 3.5}"))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        BigDecimal hours = jdbcTemplate.queryForObject(
                "SELECT volunteer_hours FROM activity_attendance WHERE activity_id = ? AND user_id = ?",
                BigDecimal.class, activityId, STUDENT);
        assertEquals(0, new BigDecimal("3.5").compareTo(hours));

        // 学生 6 的种子时长 1.5（活动 1）+ 本次 3.5 = 5.0，记录数 2
        MvcResult summary = mockMvc.perform(get("/api/attendances/me").header(AUTH, bearer(STUDENT)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(2))
                .andReturn();
        assertEquals(0, new BigDecimal("5.0").compareTo(
                dataOf(summary).path("totalHours").decimalValue()));
    }

    @Test
    @DisplayName("给没签到的人设时长 → 404/40405（时长依附于签到记录）")
    void updateHoursWithoutAttendanceReturns404() throws Exception {
        long activityId = createActivity(minutesFromNow(-300), minutesFromNow(-180), PUBLISHED);
        register(activityId, STUDENT);

        mockMvc.perform(put("/api/activities/{id}/attendances/{userId}/hours", activityId, STUDENT)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volunteerHours\": 1.0}"))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40405));
    }

    @Test
    @DisplayName("负时长 / 两位小数 → 400/40000")
    void updateHoursRejectsInvalidValues() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);
        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isOk());

        mockMvc.perform(put("/api/activities/{id}/attendances/{userId}/hours", activityId, STUDENT)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volunteerHours\": -1}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        mockMvc.perform(put("/api/activities/{id}/attendances/{userId}/hours", activityId, STUDENT)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("{\"volunteerHours\": 1.25}"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    // ------------------------------------------------------------------ 名单与取消

    @Test
    @DisplayName("签到名单：别家负责人 403、学生 403、未登录 401、活动不存在 404")
    void attendanceListPermission() throws Exception {
        long activityId = liveActivity();

        mockMvc.perform(get("/api/activities/{id}/attendances", activityId).header(AUTH, bearer(CLUB_2_OWNER)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/api/activities/{id}/attendances", activityId).header(AUTH, bearer(STUDENT)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/api/activities/{id}/attendances", activityId))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(get("/api/activities/{id}/attendances", 999_999L).header(AUTH, bearer(PLATFORM_ADMIN)))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));
    }

    @Test
    @DisplayName("签到后取消报名 → 409/40913，报名仍然是有效状态（不会留下孤儿签到）")
    void checkedInRegistrationCannotBeCancelled() throws Exception {
        long activityId = liveActivity();
        register(activityId, STUDENT);
        String student = bearer(STUDENT);
        mockMvc.perform(post("/api/activities/{id}/check-in", activityId).header(AUTH, student))
                .andExpect(status().isOk());

        mockMvc.perform(delete("/api/activities/{id}/registrations/me", activityId).header(AUTH, student))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40913));

        Integer statusValue = jdbcTemplate.queryForObject(
                "SELECT status FROM activity_registration WHERE activity_id = ? AND user_id = ?",
                Integer.class, activityId, STUDENT);
        assertNotNull(statusValue);
        assertEquals(1, statusValue, "取消被拒后报名必须保持有效，否则签到记录就成了孤儿");
        assertEquals(1, attendanceRows(activityId, STUDENT));
    }

    @Test
    @DisplayName("我的签到记录：没有记录的人返回 totalHours=0、count=0，而不是 null")
    void myAttendancesEmptyIsZero() throws Exception {
        // 造一个干净的账号，避免受种子里已有签到记录的影响
        long userId = createStudent();

        MvcResult result = mockMvc.perform(get("/api/attendances/me").header(AUTH, bearer(userId)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.count").value(0))
                .andExpect(jsonPath("$.data.records.length()").value(0))
                .andReturn();
        JsonNode data = dataOf(result);
        assertTrue(data.path("totalHours").isNumber(), "totalHours 必须是数字 0，不能是 null（前端会渲染成 null 小时）");
        assertEquals(0, BigDecimal.ZERO.compareTo(data.path("totalHours").decimalValue()));
    }

    private long createStudent() {
        String username = "chk_" + UUID.randomUUID().toString().replace("-", "").substring(0, 10);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbcTemplate.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO sys_user (username, password_hash, nickname, role, status) "
                            + "VALUES (?, ?, ?, 'STUDENT', 1)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setString(1, username);
            ps.setString(2, "$2a$10$Ef85syL4SInZAlefEG3p7.E52EQNC1wSzD/k6MkNIaKTi9lbdaEmK");
            ps.setString(3, "签到测试用户");
            return ps;
        }, keyHolder);
        long id = keyHolder.getKey().longValue();
        createdUserIds.add(id);
        return id;
    }
}
