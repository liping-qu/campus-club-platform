package com.campus.club.club.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.campus.club.club.entity.Club;

/**
 * 社团数据访问。
 * <p>
 * 目前只用 {@link BaseMapper#selectById} 回答两个问题：社团是否存在、它归谁管。
 * 没有自定义 SQL —— 管理活动时需要的社团信息就这两项，写一条
 * {@code SELECT * FROM club WHERE id = ?} 已经够了。
 */
public interface ClubMapper extends BaseMapper<Club> {
}
