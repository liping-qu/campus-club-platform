package com.campus.club.activity.service;

import com.baomidou.mybatisplus.extension.plugins.pagination.Page;
import com.campus.club.activity.dto.ActivityCreateRequest;
import com.campus.club.activity.dto.ActivityUpdateRequest;
import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.entity.ActivityStatus;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.activity.service.impl.ActivityServiceImpl;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.auth.CurrentUserContext;
import com.campus.club.cache.ActivityCache;
import com.campus.club.club.entity.Club;
import com.campus.club.club.mapper.ClubMapper;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.common.PageResult;
import com.campus.club.user.entity.UserRole;
import org.junit.jupiter.api.AfterEach;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.ArgumentCaptor;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import java.time.LocalDateTime;
import java.util.List;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.ArgumentMatchers.any;
import static org.mockito.ArgumentMatchers.anyInt;
import static org.mockito.ArgumentMatchers.anyLong;
import static org.mockito.ArgumentMatchers.eq;
import static org.mockito.ArgumentMatchers.isNull;
import static org.mockito.Mockito.never;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

/**
 * 活动写模型的单元测试。
 *
 * <p>写接口的风险集中在三处，这里逐一钉住：
 * <ol>
 *   <li><b>横向越权</b>：角色是社团负责人 ≠ 能改别人社团的活动；</li>
 *   <li><b>状态机</b>：草稿/已发布/已下架/已结束之间只有合法的边，非法调用必须是冲突而不是静默成功；</li>
 *   <li><b>不变量</b>：名额不能改到已报名人数以下，请求体不能碰 status 与 joinedCount。</li>
 * </ol>
 *
 * <p>缓存被 mock 成「关闭」状态，只验证失效动作被调用；真实的缓存行为在 ActivityCacheTest 里测。
 */
@ExtendWith(MockitoExtension.class)
class ActivityAdminServiceTest {

    private static final long CLUB_1 = 1L;
    private static final long CLUB_2 = 2L;
    private static final long PLATFORM_ADMIN = 1L;
    private static final long OWNER_1 = 2L;
    private static final long OWNER_2 = 3L;

    @Mock
    private ActivityMapper activityMapper;

    @Mock
    private ClubMapper clubMapper;

    @Mock
    private ActivityCache activityCache;

    @InjectMocks
    private ActivityServiceImpl activityService;

    @AfterEach
    void tearDown() {
        // ThreadLocal 不清会把「上一条用例的身份」带给下一条，测试之间互相污染
        CurrentUserContext.clear();
    }

    // ------------------------------------------------------------------ 夹具

    private void loginAs(long userId, String role) {
        CurrentUserContext.set(new AuthPrincipal(userId, "user" + userId, role));
    }

    private Club club(long id, Long ownerUserId) {
        Club club = new Club();
        club.setId(id);
        club.setName("社团" + id);
        club.setOwnerUserId(ownerUserId);
        club.setStatus(1);
        return club;
    }

    private Activity activity(long id, long clubId, int status, int joined) {
        Activity activity = new Activity();
        activity.setId(id);
        activity.setClubId(clubId);
        activity.setTitle("原标题");
        activity.setLocation("原地点");
        activity.setCategory("公益志愿");
        activity.setCapacity(10);
        activity.setJoinedCount(joined);
        activity.setStatus(status);
        activity.setStartTime(LocalDateTime.now().plusDays(3));
        activity.setEndTime(LocalDateTime.now().plusDays(4));
        activity.setSignupDeadline(LocalDateTime.now().plusDays(2));
        activity.setCreatedBy(OWNER_1);
        return activity;
    }

    private ActivityCreateRequest createRequest(long clubId) {
        ActivityCreateRequest request = new ActivityCreateRequest();
        request.setClubId(clubId);
        fill(request);
        return request;
    }

    private ActivityUpdateRequest updateRequest(int capacity) {
        ActivityUpdateRequest request = new ActivityUpdateRequest();
        fill(request);
        request.setCapacity(capacity);
        return request;
    }

    private void fill(com.campus.club.activity.dto.ActivitySaveRequest request) {
        request.setTitle("  新活动标题  ");
        request.setDescription("活动详情");
        request.setLocation("  大学生活动中心  ");
        request.setCategory("公益志愿");
        request.setStartTime(LocalDateTime.now().plusDays(3));
        request.setEndTime(LocalDateTime.now().plusDays(4));
        request.setSignupDeadline(LocalDateTime.now().plusDays(2));
        request.setCapacity(30);
        request.setCoverColor("#c8d9ff");
        request.setRules("规则一\n规则二");
    }

    // ------------------------------------------------------------------ 新建

    @Test
    @DisplayName("新建活动：强制草稿状态、计数归零、创建人取当前登录用户，并推进列表缓存版本")
    void createForcesDraftAndZeroCount() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.insert(any(Activity.class))).thenAnswer(invocation -> {
            invocation.<Activity>getArgument(0).setId(100L);
            return 1;
        });

        ActivityDetailVO created = activityService.createActivity(createRequest(CLUB_1));

        ArgumentCaptor<Activity> captor = ArgumentCaptor.forClass(Activity.class);
        verify(activityMapper).insert(captor.capture());
        Activity saved = captor.getValue();
        assertEquals(ActivityStatus.DRAFT, saved.getStatus());
        assertEquals(0, saved.getJoinedCount());
        assertEquals(OWNER_1, saved.getCreatedBy());
        assertEquals(CLUB_1, saved.getClubId());
        // 入参两端的空格被裁掉：否则「 大学生活动中心 」和「大学生活动中心」会成为两条不同的数据
        assertEquals("新活动标题", saved.getTitle());
        assertEquals("大学生活动中心", saved.getLocation());
        assertEquals("DRAFT", created.getPhase());

        // 新建的活动还没被任何人查过详情，没有详情缓存可删；但列表快照里缺了它
        verify(activityCache).evictList();
        verify(activityCache, never()).evict(anyLong());
    }

    @Test
    @DisplayName("新建活动：给别的社团建 → 403，且不写库")
    void createForOtherClubIsForbidden() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(clubMapper.selectById(CLUB_2)).thenReturn(club(CLUB_2, OWNER_2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.createActivity(createRequest(CLUB_2)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(activityMapper, never()).insert(any(Activity.class));
    }

    @Test
    @DisplayName("新建活动：社团负责人可以给任意社团建，不受归属限制")
    void platformAdminCanCreateForAnyClub() {
        loginAs(PLATFORM_ADMIN, UserRole.PLATFORM_ADMIN);
        when(clubMapper.selectById(CLUB_2)).thenReturn(club(CLUB_2, OWNER_2));
        when(activityMapper.insert(any(Activity.class))).thenAnswer(invocation -> {
            invocation.<Activity>getArgument(0).setId(101L);
            return 1;
        });

        ActivityDetailVO created = activityService.createActivity(createRequest(CLUB_2));

        assertEquals(CLUB_2, created.getClubId());
        assertEquals(ActivityStatus.DRAFT, created.getStatus());
        assertEquals(PLATFORM_ADMIN, created.getCreatedBy());
    }

    @Test
    @DisplayName("新建活动：开始时间在过去 → 40002，不落库")
    void createWithPastStartTimeIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        ActivityCreateRequest request = createRequest(CLUB_1);
        request.setStartTime(LocalDateTime.now().minusDays(1));
        request.setEndTime(LocalDateTime.now().plusDays(1));
        request.setSignupDeadline(LocalDateTime.now().minusDays(2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.createActivity(request));

        assertEquals(ErrorCode.INVALID_TIME_RANGE, ex.getErrorCode());
        verify(activityMapper, never()).insert(any(Activity.class));
    }

    @Test
    @DisplayName("结束时间早于开始时间 → 40002")
    void createWithEndBeforeStartIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        ActivityCreateRequest request = createRequest(CLUB_1);
        request.setEndTime(request.getStartTime().minusHours(1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.createActivity(request));

        assertEquals(ErrorCode.INVALID_TIME_RANGE, ex.getErrorCode());
    }

    @Test
    @DisplayName("报名截止晚于活动开始 → 40003")
    void createWithDeadlineAfterStartIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        ActivityCreateRequest request = createRequest(CLUB_1);
        request.setSignupDeadline(request.getStartTime().plusMinutes(30));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.createActivity(request));

        assertEquals(ErrorCode.SIGNUP_DEADLINE_AFTER_START, ex.getErrorCode());
    }

    // ------------------------------------------------------------------ 修改

    @Test
    @DisplayName("修改活动：更新成功后失效详情缓存，并返回修改后的值")
    void updateEvictsCacheAfterSuccess() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        Activity existing = activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 3);
        when(activityMapper.selectById(10L)).thenReturn(existing);
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.updateEditable(any(Activity.class))).thenReturn(1);

        ActivityDetailVO updated = activityService.updateActivity(10L, updateRequest(30));

        assertEquals("新活动标题", updated.getTitle());
        assertEquals(30, updated.getCapacity());
        // 已报名人数是报名事务维护的，改活动不能顺手改掉它
        assertEquals(3, updated.getJoinedCount());
        verify(activityCache).evict(10L);
    }

    @Test
    @DisplayName("修改活动：名额改到已报名人数以下 → 40909，且不发出 UPDATE")
    void updateCapacityBelowJoinedIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 3));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.updateActivity(10L, updateRequest(2)));

        assertEquals(ErrorCode.CAPACITY_BELOW_JOINED, ex.getErrorCode());
        verify(activityMapper, never()).updateEditable(any(Activity.class));
        verify(activityCache, never()).evict(anyLong());
    }

    @Test
    @DisplayName("修改活动：条件更新影响 0 行时重新读库解释真实原因（名额被并发报名顶掉）")
    void updateExplainsConditionalUpdateFailure() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L))
                .thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 3))
                // 第二次读：另一个请求刚把 5 个人写进了报名表
                .thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 8));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.updateEditable(any(Activity.class))).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.updateActivity(10L, updateRequest(5)));

        assertEquals(ErrorCode.CAPACITY_BELOW_JOINED, ex.getErrorCode());
        verify(activityCache, never()).evict(anyLong());
    }

    @Test
    @DisplayName("修改活动：已下架的活动不可修改 → 40910")
    void updateCancelledActivityIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.CANCELLED, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.updateActivity(10L, updateRequest(30)));

        assertEquals(ErrorCode.ACTIVITY_STATUS_CONFLICT, ex.getErrorCode());
        verify(activityMapper, never()).updateEditable(any(Activity.class));
    }

    @Test
    @DisplayName("修改活动：改别人社团的活动 → 403")
    void updateOtherClubActivityIsForbidden() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(20L)).thenReturn(activity(20L, CLUB_2, ActivityStatus.PUBLISHED, 0));
        when(clubMapper.selectById(CLUB_2)).thenReturn(club(CLUB_2, OWNER_2));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.updateActivity(20L, updateRequest(30)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
        verify(activityMapper, never()).updateEditable(any(Activity.class));
    }

    @Test
    @DisplayName("活动不存在 → 40401（而不是 403：先查存在性，再判权限）")
    void updateMissingActivityReturnsNotFound() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(404L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.updateActivity(404L, updateRequest(30)));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND, ex.getErrorCode());
    }

    // ------------------------------------------------------------------ 发布 / 下架

    @Test
    @DisplayName("发布：草稿 → 已发布，条件更新带上预期状态，并清掉「不存在」缓存")
    void publishDraftMovesToPublished() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.DRAFT, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.changeStatus(10L, ActivityStatus.DRAFT, ActivityStatus.PUBLISHED)).thenReturn(1);

        ActivityDetailVO published = activityService.publishActivity(10L);

        assertEquals(ActivityStatus.PUBLISHED, published.getStatus());
        // 发布前详情查询会把这个 id 缓存成「不存在」，不失效的话发布后仍会 404
        verify(activityCache).evict(10L);
    }

    @Test
    @DisplayName("发布：报名截止时间已过 → 40905，且不改状态")
    void publishWithExpiredDeadlineIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        Activity draft = activity(10L, CLUB_1, ActivityStatus.DRAFT, 0);
        draft.setSignupDeadline(LocalDateTime.now().minusHours(1));
        when(activityMapper.selectById(10L)).thenReturn(draft);
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.publishActivity(10L));

        assertEquals(ErrorCode.SIGNUP_CLOSED, ex.getErrorCode());
        verify(activityMapper, never()).changeStatus(anyLong(), anyInt(), anyInt());
    }

    @Test
    @DisplayName("发布：已发布的活动再发一次 → 40910")
    void publishAlreadyPublishedIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.publishActivity(10L));

        assertEquals(ErrorCode.ACTIVITY_STATUS_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("下架：已发布 → 已下架，phase 变成「已下架」而不是「已结束」")
    void unpublishMovesToCancelled() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.changeStatus(10L, ActivityStatus.PUBLISHED, ActivityStatus.CANCELLED)).thenReturn(1);

        ActivityDetailVO cancelled = activityService.unpublishActivity(10L);

        assertEquals(ActivityStatus.CANCELLED, cancelled.getStatus());
        assertEquals("CANCELLED", cancelled.getPhase());
        assertEquals("已下架", cancelled.getPhaseText());
    }

    @Test
    @DisplayName("下架：草稿不能下架 → 40910")
    void unpublishDraftIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.DRAFT, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.unpublishActivity(10L));

        assertEquals(ErrorCode.ACTIVITY_STATUS_CONFLICT, ex.getErrorCode());
    }

    @Test
    @DisplayName("下架：条件更新影响 0 行（并发下已被别人改过）→ 40910，不会当成成功")
    void unpublishLosingRaceIsConflict() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.changeStatus(anyLong(), anyInt(), anyInt())).thenReturn(0);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.unpublishActivity(10L));

        assertEquals(ErrorCode.ACTIVITY_STATUS_CONFLICT, ex.getErrorCode());
    }

    // ------------------------------------------------------------------ 删除

    @Test
    @DisplayName("删除：草稿可以删，DELETE 语句自带 status = 0 条件")
    void deleteDraftIsAllowed() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.DRAFT, 0));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));
        when(activityMapper.deleteDraft(10L)).thenReturn(1);

        activityService.deleteActivity(10L);

        verify(activityMapper).deleteDraft(10L);
        verify(activityCache).evict(10L);
    }

    @Test
    @DisplayName("删除：已发布的活动只能下架 → 40910，且不发出 DELETE")
    void deletePublishedIsRejected() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        when(activityMapper.selectById(10L)).thenReturn(activity(10L, CLUB_1, ActivityStatus.PUBLISHED, 3));
        when(clubMapper.selectById(CLUB_1)).thenReturn(club(CLUB_1, OWNER_1));

        BusinessException ex = assertThrows(BusinessException.class,
                () -> activityService.deleteActivity(10L));

        assertEquals(ErrorCode.ACTIVITY_STATUS_CONFLICT, ex.getErrorCode());
        // 物理删除会顺着 ON DELETE CASCADE 抹掉用户的报名历史，这是绝对不能发生的
        verify(activityMapper, never()).deleteDraft(anyLong());
    }

    // ------------------------------------------------------------------ 管理端列表

    @Test
    @DisplayName("管理端列表：社团负责人只查自己社团（ownerId 进 SQL，而不是查完再过滤）")
    void managePageFiltersByOwner() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        Page<ActivityDetailVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(activityMapper.selectManagePage(any(), eq(OWNER_1), any())).thenReturn(page);

        PageResult<ActivityDetailVO> result = activityService.pageManageable(1, 10, null);

        assertEquals(0, result.getTotal());
        verify(activityMapper).selectManagePage(any(), eq(OWNER_1), isNull());
    }

    @Test
    @DisplayName("管理端列表：平台管理员 ownerId 传 null，表示不过滤社团")
    void managePageForPlatformAdminHasNoOwnerFilter() {
        loginAs(PLATFORM_ADMIN, UserRole.PLATFORM_ADMIN);
        Page<ActivityDetailVO> page = new Page<>(1, 10);
        page.setRecords(List.of());
        page.setTotal(0);
        when(activityMapper.selectManagePage(any(), isNull(), eq(ActivityStatus.DRAFT))).thenReturn(page);

        activityService.pageManageable(1, 10, ActivityStatus.DRAFT);

        verify(activityMapper).selectManagePage(any(), isNull(), eq(ActivityStatus.DRAFT));
    }

    @Test
    @DisplayName("管理端列表：单页条数被收敛到 50，防止一次拉全表")
    void managePageSizeIsCapped() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        Page<ActivityDetailVO> page = new Page<>(1, 50);
        page.setRecords(List.of());
        page.setTotal(0);
        when(activityMapper.selectManagePage(any(), any(), any())).thenReturn(page);

        PageResult<ActivityDetailVO> result = activityService.pageManageable(1, 5000, null);

        assertEquals(50, result.getSize());
    }

    @Test
    @DisplayName("管理端列表：记录带上推导出的阶段与规则列表，不返回原始 rules 文本")
    void managePageEnrichesRecords() {
        loginAs(OWNER_1, UserRole.CLUB_ADMIN);
        ActivityDetailVO draft = new ActivityDetailVO();
        draft.setId(10L);
        draft.setStatus(ActivityStatus.DRAFT);
        draft.setCapacity(10);
        draft.setJoinedCount(0);
        draft.setStartTime(LocalDateTime.now().plusDays(3));
        draft.setEndTime(LocalDateTime.now().plusDays(4));
        draft.setSignupDeadline(LocalDateTime.now().plusDays(2));
        draft.setRules("规则一\n规则二\n");

        Page<ActivityDetailVO> page = new Page<>(1, 10);
        page.setRecords(List.of(draft));
        page.setTotal(1);
        when(activityMapper.selectManagePage(any(), any(), any())).thenReturn(page);

        ActivityDetailVO vo = activityService.pageManageable(1, 10, null).getRecords().get(0);

        assertEquals("DRAFT", vo.getPhase());
        assertEquals(List.of("规则一", "规则二"), vo.getRuleList());
        assertNull(vo.getRules());
    }
}
