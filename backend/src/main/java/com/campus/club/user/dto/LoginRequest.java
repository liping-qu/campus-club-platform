package com.campus.club.user.dto;

import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 登录入参。
 * <p>
 * 只做「非空 + 长度」这类格式校验，不校验「密码是否包含特殊字符」——
 * 那是注册时的规则，登录时拒绝只会让老用户登不进来。
 */
@Data
public class LoginRequest {

    @NotBlank(message = "用户名不能为空")
    @Size(max = 50, message = "用户名长度不能超过 50")
    private String username;

    @NotBlank(message = "密码不能为空")
    @Size(max = 64, message = "密码长度不能超过 64")
    private String password;
}
