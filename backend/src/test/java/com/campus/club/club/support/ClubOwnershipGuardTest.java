package com.campus.club.club.support;

import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.mapper.ActivityMapper;
import com.campus.club.auth.AuthPrincipal;
import com.campus.club.common.BusinessException;
import com.campus.club.common.ErrorCode;
import com.campus.club.user.entity.UserRole;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.InjectMocks;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

import static org.junit.jupiter.api.Assertions.assertDoesNotThrow;
import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertThrows;
import static org.mockito.Mockito.when;

/**
 * 「这个活动归不归我管」的判断测试。
 * <p>
 * 一个 8 行的判断被六个入口复用，所以它的边界值得单独测：
 * 尤其「活动不存在」和「不归你管」必须是两个不同的码 —— 混成一个，
 * 拿 id 挨个试就能反推哪些活动存在。
 */
@ExtendWith(MockitoExtension.class)
class ClubOwnershipGuardTest {

    private static final long ACTIVITY_ID = 10L;
    private static final long OWNER_ID = 2L;
    private static final long OTHER_ADMIN_ID = 3L;
    private static final long PLATFORM_ADMIN_ID = 1L;

    @Mock
    private ActivityMapper activityMapper;

    @InjectMocks
    private ClubOwnershipGuard guard;

    private AuthPrincipal principal(long userId, String role) {
        return new AuthPrincipal(userId, "user" + userId, role);
    }

    @Test
    @DisplayName("本社团负责人可以管理自己的活动")
    void ownerCanManage() {
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(OWNER_ID);

        assertDoesNotThrow(() -> guard.assertCanManage(ACTIVITY_ID,
                principal(OWNER_ID, UserRole.CLUB_ADMIN)));
    }

    @Test
    @DisplayName("平台管理员可以管理任意社团的活动")
    void platformAdminCanManageAny() {
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(OWNER_ID);

        assertDoesNotThrow(() -> guard.assertCanManage(ACTIVITY_ID,
                principal(PLATFORM_ADMIN_ID, UserRole.PLATFORM_ADMIN)));
    }

    @Test
    @DisplayName("其他社团的负责人 → 403（横向越权）")
    void otherClubAdminIsForbidden() {
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(OWNER_ID);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertCanManage(ACTIVITY_ID, principal(OTHER_ADMIN_ID, UserRole.CLUB_ADMIN)));

        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());
    }

    @Test
    @DisplayName("活动不存在 → 404（不能报成 403，否则等于提供活动存在性探测）")
    void missingActivityReturnsNotFound() {
        when(activityMapper.selectClubOwnerId(99L)).thenReturn(null);
        when(activityMapper.selectById(99L)).thenReturn(null);

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertCanManage(99L, principal(OWNER_ID, UserRole.CLUB_ADMIN)));

        assertEquals(ErrorCode.ACTIVITY_NOT_FOUND, ex.getErrorCode());
    }

    @Test
    @DisplayName("社团没有负责人时，只有平台管理员能操作")
    void clubWithoutOwnerOnlyAllowsPlatformAdmin() {
        when(activityMapper.selectClubOwnerId(ACTIVITY_ID)).thenReturn(null);
        when(activityMapper.selectById(ACTIVITY_ID)).thenReturn(new Activity());

        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertCanManage(ACTIVITY_ID, principal(OWNER_ID, UserRole.CLUB_ADMIN)));
        assertEquals(ErrorCode.FORBIDDEN, ex.getErrorCode());

        assertDoesNotThrow(() -> guard.assertCanManage(ACTIVITY_ID,
                principal(PLATFORM_ADMIN_ID, UserRole.PLATFORM_ADMIN)));
    }

    @Test
    @DisplayName("没有登录主体 → 401（防御性检查，正常路径到不了这里）")
    void nullPrincipalIsUnauthorized() {
        BusinessException ex = assertThrows(BusinessException.class,
                () -> guard.assertCanManage(ACTIVITY_ID, null));

        assertEquals(ErrorCode.UNAUTHORIZED, ex.getErrorCode());
    }
}
