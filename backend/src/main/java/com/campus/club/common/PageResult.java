package com.campus.club.common;

import lombok.Getter;

import java.util.List;

/**
 * 分页结果。
 * <p>
 * 不直接返回 MyBatis-Plus 的 IPage，避免把持久层对象泄漏到接口契约里。
 */
@Getter
public class PageResult<T> {

    private final List<T> records;
    private final long total;
    private final int page;
    private final int size;
    private final long pages;

    public PageResult(List<T> records, long total, int page, int size) {
        this.records = records;
        this.total = total;
        this.page = page;
        this.size = size;
        this.pages = size <= 0 ? 0 : (total + size - 1) / size;
    }

    public static <T> PageResult<T> of(List<T> records, long total, int page, int size) {
        return new PageResult<>(records, total, page, size);
    }
}
