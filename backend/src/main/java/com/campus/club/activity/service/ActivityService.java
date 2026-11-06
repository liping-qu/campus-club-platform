package com.campus.club.activity.service;

import com.campus.club.activity.dto.ActivityCreateRequest;
import com.campus.club.activity.dto.ActivityQuery;
import com.campus.club.activity.dto.ActivityUpdateRequest;
import com.campus.club.activity.vo.ActivityDetailVO;
import com.campus.club.activity.vo.ActivityListVO;
import com.campus.club.common.PageResult;

/**
 * 活动服务：读模型 + 写模型。
 *
 * <h3>为什么写操作也要放在这个接口里</h3>
 * 写完之后必须让缓存失效，而缓存的失效时机（事务提交后）只有实现类知道。
 * 如果拆成独立的 {@code ActivityCommandService}，缓存失效就会变成两个类之间的约定，
 * 新加一个写方法时很容易忘记 —— 而忘记的后果是「改了活动但页面十分钟不变」。
 */
public interface ActivityService {

    /**
     * 分页查询活动（对外，只含已发布）。
     */
    PageResult<ActivityListVO> pageActivities(ActivityQuery query);

    /**
     * 查询活动详情，不存在时抛出业务异常。
     */
    ActivityDetailVO getActivityDetail(Long id);

    /**
     * 管理端列表：当前用户可管理的活动，包含草稿与已下架。
     *
     * @param status 可选的状态过滤（0 草稿 / 1 已发布 / 2 已下架 / 3 已结束），null 不过滤
     */
    PageResult<ActivityDetailVO> pageManageable(int pageNo, int pageSize, Integer status);

    /**
     * 管理端单条详情：草稿、已下架都能读，用于编辑页回填。
     * <p>
     * 与 {@link #getActivityDetail} 的差别只有两点：不套状态过滤、不走公开缓存
     * （管理端要看到刚改完的最新状态，而草稿本来也不该进公开详情缓存）。
     * 归属校验与写接口共用同一套规则：不存在 404，存在但不归你管 403。
     */
    ActivityDetailVO getManageableDetail(Long id);

    /**
     * 新建活动，初始状态为草稿。
     * <p>
     * 只有「自己负责的社团」和平台管理员能建，普通学生 403。
     */
    ActivityDetailVO createActivity(ActivityCreateRequest request);

    /**
     * 修改活动（整体覆盖可编辑字段）。
     * <p>
     * 已下架、已结束的活动不允许修改；名额不能改到已报名人数以下。
     */
    ActivityDetailVO updateActivity(Long id, ActivityUpdateRequest request);

    /** 发布：草稿或已下架 → 已发布。报名截止时间已过则拒绝。 */
    ActivityDetailVO publishActivity(Long id);

    /** 下架：已发布 → 已下架。已报名的记录保留。 */
    ActivityDetailVO unpublishActivity(Long id);

    /** 删除：只允许删草稿。已发布的只能下架。 */
    void deleteActivity(Long id);
}
