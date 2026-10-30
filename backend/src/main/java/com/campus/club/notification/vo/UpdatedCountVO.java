package com.campus.club.notification.vo;

/**
 * 「批量操作影响条数」。
 * <p>
 * 单独建类型而不是复用 {@link UnreadCountVO}：
 * 两者语义不同（一个是当前未读数，一个是本次更新了几条），
 * 共用一个字段名迟早会有人在错误的场景里读错它。
 */
public record UpdatedCountVO(int updated) {
}
