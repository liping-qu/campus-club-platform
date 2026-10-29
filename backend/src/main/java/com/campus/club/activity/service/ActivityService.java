package com.campus.club.activity.service;

import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.PageResult;

/**
 * 活动读模型服务。
 */
public interface ActivityService {

    /**
     * 分页查询活动。
     */
    PageResult<ActivityListVO> pageActivities(ActivityQuery query);

    /**
     * 查询活动详情，不存在时抛出业务异常。
     */
    ActivityDetailVO getActivityDetail(Long id);
}
