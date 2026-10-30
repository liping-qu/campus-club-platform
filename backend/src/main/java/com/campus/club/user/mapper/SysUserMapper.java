package com.campus.club.user.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.club.user.entity.SysUser;
import org.apache.ibatis.annotations.Param;

/**
 * 用户数据访问。
 */
public interface SysUserMapper extends BaseMapper<SysUser> {

    /**
     * 按登录名查用户。走 {@code uk_user_username} 唯一索引。
     */
    SysUser selectByUsername(@Param("username") String username);
}
