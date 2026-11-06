package com.campus.club.club.vo;

import lombok.Data;

/**
 * 「我能管理的社团」选项。
 * <p>
 * 只给 id 和名称：前端的新建活动表单需要一个下拉框来选 clubId，
 * 而社团的分类、简介、成员数在这个场景里一个都用不到。
 * 少传字段就是少一处将来会过期的数据。
 */
@Data
public class ClubOptionVO {

    private Long id;
    private String name;
}
