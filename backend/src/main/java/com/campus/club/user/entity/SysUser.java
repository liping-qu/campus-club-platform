package com.campus.club.user.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 用户实体，对应表 {@code sys_user}。
 * <p>
 * {@code passwordHash} 只允许在登录校验时读取，<b>任何 VO 都不得包含该字段</b>；
 * 返回给前端的用户信息用 {@code UserVO}，字段在这里显式挑过一遍。
 */
@Data
@TableName("sys_user")
public class SysUser {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String username;

    /** BCrypt 哈希，禁止明文，禁止出现在响应体里 */
    private String passwordHash;

    private String nickname;

    private String studentNo;

    private String college;

    private String grade;

    private String phone;

    private String email;

    private String avatarUrl;

    /** 见 {@link UserRole} */
    private String role;

    /** 1 正常，0 禁用 */
    private Integer status;

    private LocalDateTime createdAt;

    private LocalDateTime updatedAt;

    public boolean isEnabled() {
        return status != null && status == 1;
    }
}
