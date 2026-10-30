package com.campus.club.notification.vo;

/**
 * 未读数。
 * <p>
 * 不用裸 {@code Long} 直接返回：{@code data} 是对象的话以后可以加
 * {@code byType}、{@code lastReadAt} 这些字段而不破坏契约，
 * 而 {@code data: 3} 想扩展就只能改结构。
 */
public record UnreadCountVO(long unread) {
}
