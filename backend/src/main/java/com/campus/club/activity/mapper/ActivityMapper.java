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

    /**
     * 管理端单条详情：不过滤状态，草稿与已下架也能读到。
     * <p>
     * 存在的理由很具体：编辑页要回填一个草稿，而 {@link #selectActivityDetail}
     * 硬编码了 {@code status = 1}，草稿在那里是 404。若让编辑页退化成
     * 「去管理端列表里翻这一条」，活动超过一页（50 条）之后就翻不到了。
     * <p>
     * 这里刻意<b>不做</b>归属过滤：SQL 只负责取数，越权判断统一在 Service 层，
     * 否则「活动不存在」和「不归你管」都会被压成同一个空结果，404 与 403 的语义就串了。
     */
    ActivityDetailVO selectManageDetail(@Param("id") Long id);

    /**
     * 原子占用一个名额。
     * <p>
     * 「是否还有名额」「是否已截止」「活动是否可报名」三个判断全部写在 WHERE 里，
     * 由 InnoDB 行锁保证同一条记录上的并发更新串行执行，因此不会出现
     * 「两个线程都查到还剩 1 个名额，然后各自 +1」的超卖。
     * <p>
     * 调用方必须检查返回值：1 表示占位成功，0 表示条件不满足，不能默认成功。
     *
     * @return 受影响行数
     */
    int occupySlot(@Param("activityId") Long activityId);

    /**
     * 释放一个名额。
     * <p>
     * {@code joined_count > 0} 是防御条件：即使上游逻辑出错导致重复释放，
     * 数据库也不会把计数减成负数（表上还有 CHECK 约束兜底）。
     *
     * @return 受影响行数
     */
    int releaseSlot(@Param("activityId") Long activityId);

    /**
     * 报名场景下的活动状态快照，用于在占位失败后解释具体原因。
     * <p>
     * 不返回长文本字段，避免为了一次判断把 description 拉出来。
     * <p>
     * 注意：这是普通快照读。在 REPEATABLE READ 下，同一事务内重复调用拿到的是
     * 事务开始时的版本，因此它只适合做「进入事务时的前置判断」，
     * 不能用来解释 UPDATE 失败的原因 —— 那需要 {@link #selectForRegistrationLatest}。
     */
    Activity selectForRegistration(@Param("activityId") Long activityId);

    /**
     * 读取活动的最新状态，并对该行加排他锁（{@code FOR UPDATE}）。
     * <p>
     * 用途有两个，都发生在已经持有该行锁之后，因此不会引入新的等待：
     * <ol>
     *   <li>占位失败后解释原因 —— 普通 SELECT 会读到旧版本，据此解释必然张冠李戴；</li>
     *   <li>占位成功后回读真实计数 —— 事务内自增过的值用普通 SELECT 也读不到。</li>
     * </ol>
     */
    Activity selectForRegistrationLatest(@Param("activityId") Long activityId);

    /**
     * 查活动所属社团的负责人。
     * <p>
     * 用于数据级权限判断：角色是 CLUB_ADMIN 只说明「是某个社团的负责人」，
     * 还要确认这个活动的社团正好归他管，否则就是横向越权（看/改别人的社团数据）。
     * <p>
     * 查不到活动时返回 null。
     */
    Long selectClubOwnerId(@Param("activityId") Long activityId);

    /**
     * 管理端列表：某个负责人旗下社团的全部活动，**包含草稿与已下架**。
     * <p>
     * 与 {@link #selectActivityPage} 的区别不只是权限：
     * 那个查询硬编码了 {@code status = 1}（对外只展示已发布），
     * 拿它做管理列表，运营会看不到自己刚存下的草稿，以为创建失败。
     *
     * @param ownerId 社团负责人；传 null 表示平台管理员，看全部
     * @param status  可选的状态过滤，null 表示不过滤
     */
    IPage<ActivityDetailVO> selectManagePage(IPage<ActivityDetailVO> page,
                                             @Param("ownerId") Long ownerId,
                                             @Param("status") Integer status);

    /**
     * 整体覆盖可编辑字段。
     * <p>
     * 刻意不使用 {@code updateById}：那会按「非空才更新」处理，
     * 于是「把简介清空」这个合法操作永远生效不了（传 null 等于不更新）。
     * 这条语句显式列出每个可写列，写 null 就是清空。
     * <p>
     * 两个 WHERE 条件都是并发防护：
     * <ul>
     *   <li>{@code status IN (0,1)}：改到一半活动被下架/结束了，这次修改就作废；</li>
     *   <li>{@code joined_count <= capacity}：改小名额的同时有人报名，直接放弃这次修改，
     *       而不是让数据库的 CHECK 约束抛一个用户看不懂的异常。</li>
     * </ul>
     *
     * @return 受影响行数，0 表示条件不满足，调用方必须重新读取原因
     */
    int updateEditable(@Param("a") Activity activity);

    /**
     * 条件改状态：只有当前状态等于 {@code expectedStatus} 才改。
     * <p>
     * 两个管理员同时点「发布」和「删除」，不加这个条件就会出现
     * 「删除赢了、但状态被改成已发布」的中间态。
     */
    int changeStatus(@Param("id") Long id,
                     @Param("expectedStatus") int expectedStatus,
                     @Param("newStatus") int newStatus);

    /**
     * 只允许删除草稿。
     * <p>
     * 已发布的活动不能物理删除：`activity_registration` 上是
     * {@code ON DELETE CASCADE}，删掉活动会连带删掉所有报名记录。
     * 用户的历史报名记录不该因为运营点错一次按钮就消失，所以已发布的只能下架。
     */
    int deleteDraft(@Param("id") Long id);
}
