package com.campus.club.user.entity;

/**
 * 用户角色。
 * <p>
 * 只有三种，不做细粒度权限点：当前的业务复杂度用不到 RBAC 的完整模型，
 * 硬套「角色-权限-资源」三张表只会让代码变难读。
 * 什么时候真的需要细粒度权限，什么时候再拆。
 */
public final class UserRole {

    private UserRole() {
    }

    /** 普通学生：可以报名、收藏、签到 */
    public static final String STUDENT = "STUDENT";

    /** 社团负责人：管理本社团的活动、审核入社申请 */
    public static final String CLUB_ADMIN = "CLUB_ADMIN";

    /** 平台管理员：全校数据可见可管 */
    public static final String PLATFORM_ADMIN = "PLATFORM_ADMIN";

    public static boolean isAdmin(String role) {
        return CLUB_ADMIN.equals(role) || PLATFORM_ADMIN.equals(role);
    }
}
