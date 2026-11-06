package com.campus.club.club.entity;

import com.baomidou.mybatisplus.annotation.IdType;
import com.baomidou.mybatisplus.annotation.TableId;
import com.baomidou.mybatisplus.annotation.TableName;
import lombok.Data;

/**
 * 社团（表 {@code club}）—— 只映射管理权限判断真正用到的字段。
 * <p>
 * 没有把 {@code member_count}、{@code description} 之类的字段一起映射进来，
 * 是因为这里唯一的需求是回答「这个社团归谁管」「它叫什么名字」。
 * 映射用不到的列只会让人误以为它们被维护着。
 */
@Data
@TableName("club")
public class Club {

    @TableId(type = IdType.AUTO)
    private Long id;

    private String name;

    /** 负责人用户 ID。可能为 NULL（负责人账号被删除时外键置空） */
    private Long ownerUserId;

    /** 1 正常，0 已解散 */
    private Integer status;
}
