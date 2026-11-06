package com.campus.club.activity.dto;

import lombok.Data;
import lombok.EqualsAndHashCode;

/**
 * 修改活动。
 * <p>
 * 与新建共用同一组字段，但**没有 clubId**：活动归属社团在创建后就固定了，
 * 原因见 {@link ActivityCreateRequest}。
 * <p>
 * 这里用整体覆盖语义（PUT）而不是「传哪个改哪个」（PATCH）：
 * 活动表单本来就是一个整体提交的页面，PATCH 会让「字段没传」和
 * 「字段被清空」变得难以区分，前端很容易一次误操作把地点清成 null。
 */
@Data
@EqualsAndHashCode(callSuper = true)
public class ActivityUpdateRequest extends ActivitySaveRequest {
}
