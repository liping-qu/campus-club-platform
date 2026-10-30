package com.campus.club.user.vo;

import com.campus.club.user.entity.SysUser;
import lombok.Data;

/**
 * 对外暴露的用户信息。
 * <p>
 * 字段是「挑出来」而不是「从实体排除」：新增数据库字段时不会顺着实体自动泄漏出去，
 * 尤其是 {@code passwordHash} —— 它根本不在这个类里。
 */
@Data
public class UserVO {

    private Long id;
    private String username;
    private String nickname;
    private String role;
    private String roleText;
    private String studentNo;
    private String college;
    private String grade;
    private String avatarUrl;

    public static UserVO from(SysUser user) {
        UserVO vo = new UserVO();
        vo.setId(user.getId());
        vo.setUsername(user.getUsername());
        vo.setNickname(user.getNickname());
        vo.setRole(user.getRole());
        vo.setRoleText(roleText(user.getRole()));
        vo.setStudentNo(user.getStudentNo());
        vo.setCollege(user.getCollege());
        vo.setGrade(user.getGrade());
        vo.setAvatarUrl(user.getAvatarUrl());
        return vo;
    }

    private static String roleText(String role) {
        if (role == null) {
            return "";
        }
        return switch (role) {
            case "PLATFORM_ADMIN" -> "平台管理员";
            case "CLUB_ADMIN" -> "社团负责人";
            default -> "学生";
        };
    }
}
