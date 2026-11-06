package com.campus.club.activity.dto;

import jakarta.validation.constraints.NotNull;
import jakarta.validation.constraints.Positive;
import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 新建活动。
 * <p>
 * 只有新建能指定所属社团。修改接口刻意没有这个字段 ——
 * 「把这个活动挪到别的社团」会让已有的报名记录和权限判断同时失效：
 * 报名人以为自己报的是 A 社团的活动，名单却出现在 B 社团负责人手里。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityCreateRequest extends ActivitySaveRequest {

    @NotNull(message = "所属社团不能为空")
    @Positive(message = "社团 ID 必须为正整数")
    private Long clubId;
}
