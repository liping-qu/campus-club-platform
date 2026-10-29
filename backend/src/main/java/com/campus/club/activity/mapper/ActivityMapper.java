package com.campus.club.activity.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.baomidou.mybatisplus.core.metadata.IPage;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.entity.Activity;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import org.apache.ibatis.annotations.Param;

/**
 * 活动数据访问。
 * <p>
 * 只负责取数，不做任何业务判断。
 */
public interface ActivityMapper extends BaseMapper<Activity> {

    /**
     * 分页查询活动列表。
     *
     * @param page  分页参数
     * @param query 过滤条件（keyword / category 已由 Service 归一化）
     * @param phase 展示阶段，取值 ALL / OPEN / UPCOMING / FULL / FINISHED
     */
    IPage<ActivityListVO> selectActivityPage(IPage<ActivityListVO> page,
                                             @Param("q") ActivityQuery query,
                                             @Param("phase") String phase);

    /**
     * 查询活动详情，仅返回已发布状态。
     */
    ActivityDetailVO selectActivityDetail(@Param("id") Long id);
}
