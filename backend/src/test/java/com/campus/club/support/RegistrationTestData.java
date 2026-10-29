package com.campus.club.support;

import org.springframework.jdbc.core.JdbcTemplate;
import org.springframework.jdbc.support.GeneratedKeyHolder;
import org.springframework.jdbc.support.KeyHolder;

import java.sql.PreparedStatement;
import java.sql.Statement;
import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.List;
import java.util.UUID;

/**
 * 报名相关测试的数据夹具。
 * <p>
 * 并发测试必须能自己造数据：种子数据只有 8 个用户，撑不起「40 人抢 5 个名额」。
 * 所有方法都返回主键，用完必须调用 {@link #cleanup} 清掉，
 * 否则残留数据会让活动列表的断言（total = 5）失效。
 */
public final class RegistrationTestData {

    /** 种子数据里的社团与创建者，直接复用，避免再依赖外键顺序 */
    private static final long SEED_CLUB_ID = 1L;
    private static final long SEED_CREATOR_ID = 2L;

    private RegistrationTestData() {
    }

    /**
     * 造一个可报名（已发布、截止时间在未来）的活动。
     */
    public static long createOpenActivity(JdbcTemplate jdbc, int capacity) {
        return createActivity(jdbc, capacity, LocalDateTime.now().plusDays(2), 1);
    }

    public static long createActivity(JdbcTemplate jdbc, int capacity,
                                      LocalDateTime signupDeadline, int status) {
        LocalDateTime start = LocalDateTime.now().plusDays(3);
        LocalDateTime end = LocalDateTime.now().plusDays(4);
        KeyHolder keyHolder = new GeneratedKeyHolder();
        jdbc.update(connection -> {
            PreparedStatement ps = connection.prepareStatement(
                    "INSERT INTO activity (club_id, title, description, location, category, "
                            + "start_time, end_time, signup_deadline, capacity, joined_count, status, "
                            + "cover_color, rules, created_by) "
                            + "VALUES (?, ?, ?, ?, ?, ?, ?, ?, ?, 0, ?, ?, ?, ?)",
                    Statement.RETURN_GENERATED_KEYS);
            ps.setLong(1, SEED_CLUB_ID);
            ps.setString(2, "并发测试活动-" + UUID.randomUUID().toString().substring(0, 8));
            ps.setString(3, "仅用于自动化测试");
            ps.setString(4, "测试地点");
            ps.setString(5, "公益志愿");
            ps.setObject(6, start);
            ps.setObject(7, end);
            ps.setObject(8, signupDeadline);
            ps.setInt(9, capacity);
            ps.setInt(10, status);
            ps.setString(11, "#c8d9ff");
            ps.setString(12, "测试规则");
            ps.setLong(13, SEED_CREATOR_ID);
            return ps;
        }, keyHolder);
        return keyHolder.getKey().longValue();
    }

    /**
     * 批量造用户。并发抢名额需要「多个不同的人」，否则测的是重复报名而不是超卖。
     */
    public static List<Long> createUsers(JdbcTemplate jdbc, int count) {
        List<Long> ids = new ArrayList<>(count);
        for (int i = 0; i < count; i++) {
            String suffix = UUID.randomUUID().toString().replace("-", "").substring(0, 10);
            KeyHolder keyHolder = new GeneratedKeyHolder();
            jdbc.update(connection -> {
                PreparedStatement ps = connection.prepareStatement(
                        "INSERT INTO sys_user (username, password_hash, nickname, role, status) "
                                + "VALUES (?, ?, ?, 'STUDENT', 1)",
                        Statement.RETURN_GENERATED_KEYS);
                ps.setString(1, "t_" + suffix);
                ps.setString(2, "$2a$10$Ef85syL4SInZAlefEG3p7.E52EQNC1wSzD/k6MkNIaKTi9lbdaEmK");
                ps.setString(3, "测试用户" + suffix);
                return ps;
            }, keyHolder);
            ids.add(keyHolder.getKey().longValue());
        }
        return ids;
    }

    public static int joinedCount(JdbcTemplate jdbc, long activityId) {
        Integer value = jdbc.queryForObject(
                "SELECT joined_count FROM activity WHERE id = ?", Integer.class, activityId);
        return value == null ? 0 : value;
    }

    public static int activeRegistrations(JdbcTemplate jdbc, long activityId) {
        Integer value = jdbc.queryForObject(
                "SELECT COUNT(*) FROM activity_registration WHERE activity_id = ? AND status = 1",
                Integer.class, activityId);
        return value == null ? 0 : value;
    }

    /**
     * 清理顺序：报名记录随活动级联删除（ON DELETE CASCADE），
     * 用户删除同样会级联删掉其报名记录，所以两步都做只是为了稳妥。
     */
    public static void cleanup(JdbcTemplate jdbc, Long activityId, List<Long> userIds) {
        if (activityId != null) {
            jdbc.update("DELETE FROM activity WHERE id = ?", activityId);
        }
        if (userIds != null) {
            for (Long userId : userIds) {
                jdbc.update("DELETE FROM sys_user WHERE id = ?", userId);
            }
        }
    }
}
