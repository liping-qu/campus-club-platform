package com.campus.club.activity.controller;

import com.campus.club.auth.JwtService;
import com.campus.club.cache.ActivityCache;
import com.campus.club.support.RegistrationTestData;
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
import org.springframework.test.web.servlet.MockMvc;
import org.springframework.test.web.servlet.MvcResult;

import java.nio.charset.StandardCharsets;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.List;

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
 * 活动管理接口的集成测试（真连 MySQL + Redis）。
 *
 * <p>与单元测试的分工：单元测试钉住分支判断，这里钉住「整条链路真的能跑通」——
 * 建库记录、状态机改库、缓存失效后重新可查、以及权限在 HTTP 层真的拦得住。
 *
 * <p>种子数据的归属关系（见 {@code 02_seed.sql}）：
 * <ul>
 *   <li>活动 1 属于社团 1（青年志愿者协会），负责人是用户 2；</li>
 *   <li>用户 3 是社团 2 的负责人，用户 1 是平台管理员，用户 6 是学生。</li>
 * </ul>
 *
 * <p><b>清理</b>：管理接口会真的写库，所以每条用例结束都要把造出来的活动删掉，
 * 并顺手失效缓存 —— 否则 {@code ActivityApiIntegrationTest} 里「总数 = 5」的断言会被这些残留数据带偏。
 */
@SpringBootTest
@AutoConfigureMockMvc
class ActivityAdminApiIntegrationTest {

    private static final String AUTH = HttpHeaders.AUTHORIZATION;
    /**
     * 接口约定的时间格式是「yyyy-MM-dd HH:mm:ss」（空格，不是 ISO 的 T）。
     * 用错格式不会得到 400 参数错误，而是 400/40001「请求体格式错误」——
     * 因为 Jackson 在反序列化阶段就失败了，还没轮到 Bean Validation。
     */
    private static final DateTimeFormatter ISO = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss");

    private static final long PLATFORM_ADMIN = 1L;
    private static final long CLUB_1_OWNER = 2L;
    private static final long CLUB_2_OWNER = 3L;
    private static final long STUDENT = 6L;
    private static final long CLUB_1 = 1L;
    private static final long CLUB_2 = 2L;
    /** 种子活动 1：社团 1，已发布，已报名 3 人（用于验证「名额不能改到已报名人数以下」） */
    private static final long SEED_ACTIVITY_OF_CLUB_1 = 1L;

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

    private final List<Long> createdIds = new ArrayList<>();
    private final List<Long> createdActivityIdsByJdbc = new ArrayList<>();

    @AfterEach
    void tearDown() {
        for (Long id : createdIds) {
            // 先失效缓存再删行：直接删行会让列表缓存里留着一条已经不存在的活动
            activityCache.evict(id);
            jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
        }
        for (Long id : createdActivityIdsByJdbc) {
            activityCache.evict(id);
            jdbcTemplate.update("DELETE FROM activity WHERE id = ?", id);
        }
        createdIds.clear();
        createdActivityIdsByJdbc.clear();
    }

    // ------------------------------------------------------------------ 夹具

    private String bearer(long userId) {
        SysUser user = userMapper.selectById(userId);
        return "Bearer " + jwtService.issue(user);
    }

    private static String at(int plusDays) {
        return LocalDateTime.now().plusDays(plusDays).format(ISO);
    }

    /** 一份合法的建活动请求体；需要非法值时在测试里覆盖单字段 */
    private String body(long clubId, String title, int capacity) {
        return """
                {
                  "clubId": %d,
                  "title": "%s",
                  "description": "端到端测试创建的活动",
                  "location": "大学生活动中心",
                  "category": "公益志愿",
                  "startTime": "%s",
                  "endTime": "%s",
                  "signupDeadline": "%s",
                  "capacity": %d,
                  "coverColor": "#c8d9ff",
                  "rules": "规则一\\n规则二"
                }
                """.formatted(clubId, title, at(3), at(4), at(2), capacity);
    }

    private String updateBody(String title, int capacity) {
        return """
                {
                  "title": "%s",
                  "description": "改过的详情",
                  "location": "体育馆",
                  "category": "文体活动",
                  "startTime": "%s",
                  "endTime": "%s",
                  "signupDeadline": "%s",
                  "capacity": %d,
                  "coverColor": "#ffd9b8",
                  "rules": "改过的规则"
                }
                """.formatted(title, at(5), at(6), at(4), capacity);
    }

    private long create(String auth, String json) throws Exception {
        MvcResult result = mockMvc.perform(post("/api/activities")
                        .header(AUTH, auth)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(json))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        long id = objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data").path("id").asLong();
        assertTrue(id > 0, "新建活动必须返回真实主键");
        createdIds.add(id);
        return id;
    }

    private JsonNode manageList(String auth, String query) throws Exception {
        MvcResult result = mockMvc.perform(get("/api/activities/manage" + query).header(AUTH, auth))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0))
                .andReturn();
        return objectMapper.readTree(result.getResponse().getContentAsString(StandardCharsets.UTF_8)).path("data");
    }

    private int statusInDb(long id) {
        Integer status = jdbcTemplate.queryForObject(
                "SELECT status FROM activity WHERE id = ?", Integer.class, id);
        return status == null ? -1 : status;
    }

    // ------------------------------------------------------------------ 生命周期

    @Test
    @DisplayName("完整生命周期：草稿→发布→对外可见→修改→下架→对外 404→重新发布")
    void fullLifecycleFromDraftToPublishedAndBack() throws Exception {
        String owner = bearer(CLUB_1_OWNER);
        long id = create(owner, body(CLUB_1, "端到端测试活动", 20));

        // 1. 新建即草稿：对外详情看不到
        assertEquals(0, statusInDb(id));
        mockMvc.perform(get("/api/activities/{id}", id))
                .andExpect(status().isNotFound())
                .andExpect(jsonPath("$.code").value(40401));

        // 2. 管理端能看到草稿，且阶段推导成「草稿」而不是「报名中」
        JsonNode manage = manageList(owner, "?status=0&pageSize=50");
        JsonNode draft = findById(manage, id);
        assertNotNull(draft, "刚创建的草稿应该出现在管理端列表里");
        assertEquals("DRAFT", draft.path("phase").asText());
        assertEquals("草稿", draft.path("phaseText").asText());

        // 3. 发布后对外立刻可见（这一步同时验证「不存在」缓存被清掉）
        mockMvc.perform(post("/api/activities/{id}/publish", id).header(AUTH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        mockMvc.perform(get("/api/activities/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("端到端测试活动"));

        // 4. 修改后详情返回新值（验证详情缓存已失效，不是读到旧快照）
        mockMvc.perform(put("/api/activities/{id}", id)
                        .header(AUTH, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("端到端测试活动（改）", 25)))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("端到端测试活动（改）"))
                .andExpect(jsonPath("$.data.capacity").value(25));

        mockMvc.perform(get("/api/activities/{id}", id))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.title").value("端到端测试活动（改）"))
                .andExpect(jsonPath("$.data.location").value("体育馆"));

        // 5. 下架后对外不可见，管理端显示「已下架」
        mockMvc.perform(post("/api/activities/{id}/unpublish", id).header(AUTH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(2))
                .andExpect(jsonPath("$.data.phaseText").value("已下架"));

        mockMvc.perform(get("/api/activities/{id}", id))
                .andExpect(status().isNotFound());

        // 6. 已下架不是草稿：物理删除必须被拒绝（否则会级联删掉报名历史）
        mockMvc.perform(delete("/api/activities/{id}", id).header(AUTH, owner))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40910));

        // 7. 已下架还能重新发布 —— 撤回不等于永久报废
        mockMvc.perform(post("/api/activities/{id}/publish", id).header(AUTH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.data.status").value(1));

        assertEquals(1, statusInDb(id));
    }

    @Test
    @DisplayName("草稿可以直接删除，删完库里就没有这条记录了")
    void draftCanBeDeleted() throws Exception {
        String owner = bearer(CLUB_1_OWNER);
        long id = create(owner, body(CLUB_1, "待删除的草稿", 10));

        mockMvc.perform(delete("/api/activities/{id}", id).header(AUTH, owner))
                .andExpect(status().isOk())
                .andExpect(jsonPath("$.code").value(0));

        assertEquals(0, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity WHERE id = ?", Integer.class, id));

        mockMvc.perform(get("/api/activities/{id}", id))
                .andExpect(status().isNotFound());
    }

    // ------------------------------------------------------------------ 权限

    @Test
    @DisplayName("横向越权：给别的社团建活动 → 403，且不产生任何数据")
    void cannotCreateForOtherClub() throws Exception {
        Integer before = jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity", Integer.class);

        mockMvc.perform(post("/api/activities")
                        .header(AUTH, bearer(CLUB_2_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CLUB_1, "越权创建的活动", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        assertEquals(before, jdbcTemplate.queryForObject("SELECT COUNT(*) FROM activity", Integer.class));
    }

    @Test
    @DisplayName("横向越权：改/发布/删除别的社团的活动 → 403")
    void cannotOperateOtherClubActivity() throws Exception {
        String intruder = bearer(CLUB_2_OWNER);

        mockMvc.perform(put("/api/activities/{id}", SEED_ACTIVITY_OF_CLUB_1)
                        .header(AUTH, intruder)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("被越权修改", 30)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(post("/api/activities/{id}/unpublish", SEED_ACTIVITY_OF_CLUB_1)
                        .header(AUTH, intruder))
                .andExpect(status().isForbidden());

        mockMvc.perform(delete("/api/activities/{id}", SEED_ACTIVITY_OF_CLUB_1)
                        .header(AUTH, intruder))
                .andExpect(status().isForbidden());

        // 种子活动没被改动
        assertEquals(1, statusInDb(SEED_ACTIVITY_OF_CLUB_1));
    }

    @Test
    @DisplayName("管理端列表按归属过滤：负责人只看自己社团，平台管理员看全部")
    void manageListIsScopedByOwnership() throws Exception {
        JsonNode club1 = manageList(bearer(CLUB_1_OWNER), "?pageSize=50");
        assertTrue(club1.path("total").asLong() > 0);
        for (JsonNode record : club1.path("records")) {
            assertEquals(CLUB_1, record.path("clubId").asLong(), "社团负责人不该看到别家社团的活动");
        }

        JsonNode club2 = manageList(bearer(CLUB_2_OWNER), "?pageSize=50");
        assertTrue(club2.path("total").asLong() > 0);
        for (JsonNode record : club2.path("records")) {
            assertEquals(CLUB_2, record.path("clubId").asLong());
        }

        JsonNode all = manageList(bearer(PLATFORM_ADMIN), "?pageSize=50");
        assertTrue(all.path("total").asLong() >= club1.path("total").asLong() + club2.path("total").asLong(),
                "平台管理员看到的数量不应少于任意单个负责人");
    }

    @Test
    @DisplayName("未登录 → 401；学生 → 403；都不是 500")
    void writeEndpointsRequireAdminRole() throws Exception {
        mockMvc.perform(post("/api/activities")
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CLUB_1, "匿名创建", 10)))
                .andExpect(status().isUnauthorized())
                .andExpect(jsonPath("$.code").value(40100));

        mockMvc.perform(post("/api/activities")
                        .header(AUTH, bearer(STUDENT))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CLUB_1, "学生创建", 10)))
                .andExpect(status().isForbidden())
                .andExpect(jsonPath("$.code").value(40300));

        mockMvc.perform(get("/api/activities/manage"))
                .andExpect(status().isUnauthorized());
    }

    // ------------------------------------------------------------------ 业务规则

    @Test
    @DisplayName("名额不能改到已报名人数以下 → 409，且活动数据一动不动")
    void capacityCannotDropBelowJoined() throws Exception {
        mockMvc.perform(put("/api/activities/{id}", SEED_ACTIVITY_OF_CLUB_1)
                        .header(AUTH, bearer(CLUB_1_OWNER))
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(updateBody("把名额改成 2 试试", 2)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40909));

        assertEquals(60, jdbcTemplate.queryForObject(
                "SELECT capacity FROM activity WHERE id = ?", Integer.class, SEED_ACTIVITY_OF_CLUB_1));
    }

    @Test
    @DisplayName("删除已发布的活动 → 409，用户的历史报名记录不能被级联删掉")
    void publishedActivityCannotBeDeleted() throws Exception {
        Integer registrationsBefore = jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_registration WHERE activity_id = ?",
                Integer.class, SEED_ACTIVITY_OF_CLUB_1);

        mockMvc.perform(delete("/api/activities/{id}", SEED_ACTIVITY_OF_CLUB_1)
                        .header(AUTH, bearer(CLUB_1_OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40910));

        assertEquals(registrationsBefore, jdbcTemplate.queryForObject(
                "SELECT COUNT(*) FROM activity_registration WHERE activity_id = ?",
                Integer.class, SEED_ACTIVITY_OF_CLUB_1));
    }

    @Test
    @DisplayName("发布一个报名截止时间已过的草稿 → 40905，状态保持草稿")
    void publishExpiredDraftIsRejected() throws Exception {
        long draftId = RegistrationTestData.createActivity(
                jdbcTemplate, 10, LocalDateTime.now().minusDays(1), 0);
        createdActivityIdsByJdbc.add(draftId);

        mockMvc.perform(post("/api/activities/{id}/publish", draftId)
                        .header(AUTH, bearer(CLUB_1_OWNER)))
                .andExpect(status().isConflict())
                .andExpect(jsonPath("$.code").value(40905));

        assertEquals(0, statusInDb(draftId));
    }

    @Test
    @DisplayName("参数校验：名额为 0 / 颜色格式错 / 结束早于开始 / 缺社团 一律 400")
    void invalidRequestsAreRejectedWith400() throws Exception {
        String owner = bearer(CLUB_1_OWNER);

        mockMvc.perform(post("/api/activities").header(AUTH, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content(body(CLUB_1, "名额为 0", 0)))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        // 颜色不合法（DTO 上的正则校验）
        mockMvc.perform(post("/api/activities").header(AUTH, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clubId":%d,"title":"颜色写错","location":"体育馆","category":"文体活动",
                                 "startTime":"%s","endTime":"%s","signupDeadline":"%s",
                                 "capacity":10,"coverColor":"blue"}
                                """.formatted(CLUB_1, at(3), at(4), at(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));

        // 结束时间早于开始时间：上下文相关，Bean Validation 表达不了，由 Service 返回更精确的 40002
        mockMvc.perform(post("/api/activities").header(AUTH, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"clubId":%d,"title":"时间颠倒","location":"体育馆","category":"文体活动",
                                 "startTime":"%s","endTime":"%s","signupDeadline":"%s","capacity":10}
                                """.formatted(CLUB_1, at(3), at(1), at(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40002));

        // 缺 clubId：可以只给自己社团建，所以社团是必填项
        mockMvc.perform(post("/api/activities").header(AUTH, owner)
                        .contentType(MediaType.APPLICATION_JSON)
                        .content("""
                                {"title":"没有社团","location":"体育馆","category":"文体活动",
                                 "startTime":"%s","endTime":"%s","signupDeadline":"%s","capacity":10}
                                """.formatted(at(3), at(4), at(2))))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    @Test
    @DisplayName("管理端列表支持按状态过滤，且单页上限 50")
    void manageListFiltersByStatus() throws Exception {
        String owner = bearer(CLUB_1_OWNER);
        long id = create(owner, body(CLUB_1, "状态过滤用的草稿", 10));

        JsonNode drafts = manageList(owner, "?status=0&pageSize=50");
        assertTrue(drafts.path("records").size() > 0);
        assertEquals(0, drafts.path("records").get(0).path("status").asInt());
        assertNotNull(findById(drafts, id), "刚创建的草稿应该出现在 status=0 的列表里");

        mockMvc.perform(get("/api/activities/manage").header(AUTH, owner).param("pageSize", "51"))
                .andExpect(status().isBadRequest())
                .andExpect(jsonPath("$.code").value(40000));
    }

    private JsonNode findById(JsonNode page, long id) {
        for (JsonNode record : page.path("records")) {
            if (record.path("id").asLong() == id) {
                return record;
            }
        }
        return null;
    }
}
