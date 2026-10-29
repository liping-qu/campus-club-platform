package com.campus.club.activity.vo;

import com.fasterxml.jackson.annotation.JsonIgnore;
import lombok.Data;
import lombok.EqualsAndHashCode;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 活动详情。
 * <p>
 * 比列表多出长文本字段，因此详情与列表分成两个查询，避免列表把 description / rules 一并拉出来。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityDetailVO extends ActivityListVO {

    private String description;

    /** 数据库中的原始规则文本（换行分隔），仅供 Mapper 映射使用，不出现在响应里 */
    @JsonIgnore
    private String rules;

    /** 由 rules 文本按行拆分，前端直接遍历渲染 */
    private List<String> ruleList;

    private Long createdBy;
    private String creatorName;
    private LocalDateTime createdAt;
}
