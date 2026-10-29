package com.campus.club.activity.dto;

import com.campus.club.activity.entity.ActivityPhase;
import jakarta.validation.constraints.Max;
import jakarta.validation.constraints.Min;
import jakarta.validation.constraints.Size;
import lombok.Data;

/**
 * 活动列表查询参数。
 * <p>
 * 这里的 page / size 上限不是「防君子」的装饰：分页插件另有 maxLimit 兜底，
 * 但更早地在入口拦住非法值，可以让错误信息更明确。
 */
@Data
public class ActivityQuery {

    @Min(value = 1, message = "页码不能小于 1")
    private Integer page = 1;

    @Min(value = 1, message = "每页条数不能小于 1")
    @Max(value = 100, message = "每页条数不能超过 100")
    private Integer size = 10;

    @Size(max = 50, message = "关键词长度不能超过 50")
    private String keyword;

    @Size(max = 40, message = "分类长度不能超过 40")
    private String category;

    /** 展示阶段筛选，默认全部 */
    private ActivityPhase phase = ActivityPhase.ALL;

    public String normalizedKeyword() {
        if (keyword == null) {
            return null;
        }
        String trimmed = keyword.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public String normalizedCategory() {
        if (category == null) {
            return null;
        }
        String trimmed = category.trim();
        return trimmed.isEmpty() ? null : trimmed;
    }

    public int safePage() {
        return page == null || page < 1 ? 1 : page;
    }

    public int safeSize() {
        if (size == null || size < 1) {
            return 10;
        }
        return Math.min(size, 100);
    }
}
