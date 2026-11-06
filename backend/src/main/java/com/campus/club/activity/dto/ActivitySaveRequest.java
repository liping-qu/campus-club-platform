package com.campus.club.activity.dto;

import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.NotBlank;
import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Pattern;
import jakarta.validation.constraints.Size;
import lombok.Data;

import java.time.LocalDateTime;

/**
 * 活动可编辑字段（新建与修改共用）。
 *
 * <h3>为什么这里没有 {@code joinedCount} 和 {@code status}</h3>
 * 前者是并发控制用的冗余计数，只能由报名/取消报名的事务内条件更新维护；
 * 后者是状态机，只能通过 publish / unpublish 两个动作改变。
 * 一旦允许它们出现在请求体里，就等于把「把已报名人数改成 0」和
 * 「把草稿直接改成已结束」这两个后门开给了调用方。
 *
 * <h3>为什么不用 {@code @Future} 校验开始时间</h3>
 * 修改一个已经开始的「已发布」活动（比如更正地点）是合法操作，
 * 那时 {@code startTime} 就在过去。真正的约束是「新建时开始时间要在未来」
 * 和「结束时间晚于开始时间」，它们依赖上下文，放在 Service 里判。
 */
@Data
public class ActivitySaveRequest {

    @NotBlank(message = "活动标题不能为空")
    @Size(max = 100, message = "标题长度不能超过 100")
    private String title;

    @Size(max = 2000, message = "活动详情不能超过 2000 字")
    private String description;

    @NotBlank(message = "活动地点不能为空")
    @Size(max = 120, message = "地点长度不能超过 120")
    private String location;

    @NotBlank(message = "活动分类不能为空")
    @Size(max = 40, message = "分类长度不能超过 40")
    private String category;

    @NotNull(message = "开始时间不能为空")
    private LocalDateTime startTime;

    @NotNull(message = "结束时间不能为空")
    private LocalDateTime endTime;

    @NotNull(message = "报名截止时间不能为空")
    private LocalDateTime signupDeadline;

    @NotNull(message = "人数上限不能为空")
    @Min(value = 1, message = "人数上限至少为 1")
    @Max(value = 10000, message = "人数上限不能超过 10000")
    private Integer capacity;

    @Pattern(regexp = "^#[0-9a-fA-F]{6}$", message = "封面颜色必须是 #RRGGBB 格式")
    private String coverColor;

    @Size(max = 1000, message = "活动规则不能超过 1000 字")
    private String rules;
}
