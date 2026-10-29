-- =============================================================
-- 校园社团管理平台 - 表结构
-- MySQL 8.0.16+ （依赖 CHECK 约束与 utf8mb4_0900_ai_ci）
--
-- 设计原则：
-- 1. 人可读的展示状态（报名中/已满员/已结束）不落库，由时间与计数推导。
-- 2. 报名、签到、收藏三张关联表都用「唯一约束」而不是「业务代码判断」来防重。
-- 3. activity.joined_count 是冗余计数，只能由事务内的条件更新维护。
-- 4. 外键用于保证引用完整性；高频写路径的并发控制依赖行锁与条件更新，
--    不依赖外键锁。
-- =============================================================

SET NAMES utf8mb4;
SET FOREIGN_KEY_CHECKS = 0;

DROP TABLE IF EXISTS notification;
DROP TABLE IF EXISTS activity_favorite;
DROP TABLE IF EXISTS activity_attendance;
DROP TABLE IF EXISTS activity_registration;
DROP TABLE IF EXISTS activity;
DROP TABLE IF EXISTS club_membership;
DROP TABLE IF EXISTS club;
DROP TABLE IF EXISTS sys_user;

SET FOREIGN_KEY_CHECKS = 1;

-- -------------------------------------------------------------
-- 用户
-- -------------------------------------------------------------
CREATE TABLE sys_user
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT COMMENT '主键',
    username      VARCHAR(50)     NOT NULL COMMENT '登录名',
    password_hash VARCHAR(100)    NOT NULL COMMENT 'BCrypt 哈希，禁止存明文',
    nickname      VARCHAR(50)     NOT NULL COMMENT '昵称',
    student_no    VARCHAR(30)              DEFAULT NULL COMMENT '学号',
    college       VARCHAR(60)              DEFAULT NULL COMMENT '学院',
    grade         VARCHAR(20)              DEFAULT NULL COMMENT '年级',
    phone         VARCHAR(30)              DEFAULT NULL COMMENT '联系方式',
    email         VARCHAR(100)             DEFAULT NULL COMMENT '邮箱',
    avatar_url    VARCHAR(255)             DEFAULT NULL COMMENT '头像地址',
    role          VARCHAR(20)     NOT NULL DEFAULT 'STUDENT' COMMENT 'STUDENT / CLUB_ADMIN / PLATFORM_ADMIN',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常，0 禁用',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_user_username (username),
    UNIQUE KEY uk_user_student_no (student_no),
    KEY idx_user_role_status (role, status)
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='用户';

-- -------------------------------------------------------------
-- 社团
-- -------------------------------------------------------------
CREATE TABLE club
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    name          VARCHAR(80)     NOT NULL COMMENT '社团名称',
    category      VARCHAR(40)     NOT NULL COMMENT '分类',
    description   VARCHAR(500)             DEFAULT NULL COMMENT '简介',
    owner_user_id BIGINT UNSIGNED          DEFAULT NULL COMMENT '负责人用户 ID',
    contact       VARCHAR(100)             DEFAULT NULL COMMENT '联系方式',
    color         VARCHAR(20)              DEFAULT NULL COMMENT '主题色',
    member_count  INT             NOT NULL DEFAULT 0 COMMENT '冗余计数：有效成员数',
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 正常，0 已解散',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_club_name (name),
    KEY idx_club_category_status (category, status),
    KEY idx_club_owner (owner_user_id),
    CONSTRAINT fk_club_owner FOREIGN KEY (owner_user_id) REFERENCES sys_user (id)
        ON DELETE SET NULL ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='社团';

-- -------------------------------------------------------------
-- 社团成员关系（含加入申请）
-- 一个用户在一个社团只有一条记录：退出后重新加入复用该行并重置状态，
-- 这样既保留历史，又不会出现多条并行成员关系。
-- -------------------------------------------------------------
CREATE TABLE club_membership
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    club_id     BIGINT UNSIGNED NOT NULL,
    user_id     BIGINT UNSIGNED NOT NULL,
    member_role VARCHAR(20)     NOT NULL DEFAULT 'MEMBER' COMMENT 'PRESIDENT / OFFICER / MEMBER',
    status      TINYINT         NOT NULL DEFAULT 0 COMMENT '0 待审核，1 已加入，2 已拒绝，3 已退出',
    applied_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    reviewed_at DATETIME                 DEFAULT NULL,
    reviewed_by BIGINT UNSIGNED          DEFAULT NULL,
    PRIMARY KEY (id),
    UNIQUE KEY uk_membership_club_user (club_id, user_id),
    KEY idx_membership_user_status (user_id, status),
    KEY idx_membership_club_status (club_id, status),
    CONSTRAINT fk_membership_club FOREIGN KEY (club_id) REFERENCES club (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_membership_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='社团成员与加入申请';

-- -------------------------------------------------------------
-- 活动
-- joined_count 为冗余计数，与 activity_registration 的有效记录数保持等式：
--   activity.joined_count = COUNT(registration WHERE status = 1)
-- 该等式由「报名 + 计数在同一事务内条件更新」保证，并提供校验 SQL 复核。
-- -------------------------------------------------------------
CREATE TABLE activity
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    club_id         BIGINT UNSIGNED NOT NULL COMMENT '所属社团',
    title           VARCHAR(100)    NOT NULL COMMENT '标题',
    description     TEXT                     DEFAULT NULL COMMENT '详情',
    location        VARCHAR(120)    NOT NULL COMMENT '地点',
    category        VARCHAR(40)     NOT NULL COMMENT '分类',
    start_time      DATETIME        NOT NULL COMMENT '开始时间',
    end_time        DATETIME        NOT NULL COMMENT '结束时间',
    signup_deadline DATETIME        NOT NULL COMMENT '报名截止时间',
    capacity        INT             NOT NULL COMMENT '人数上限',
    joined_count    INT             NOT NULL DEFAULT 0 COMMENT '已报名人数（冗余计数，仅事务内维护）',
    status          TINYINT         NOT NULL DEFAULT 1 COMMENT '0 草稿，1 已发布，2 已下架，3 已结束',
    cover_color     VARCHAR(20)              DEFAULT NULL COMMENT '封面主题色',
    rules           VARCHAR(1000)            DEFAULT NULL COMMENT '规则，换行分隔',
    created_by      BIGINT UNSIGNED NOT NULL COMMENT '创建人',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    KEY idx_activity_status_start (status, start_time),
    KEY idx_activity_category_status (category, status),
    KEY idx_activity_club (club_id),
    KEY idx_activity_creator (created_by),
    -- 最后一道防线：即使应用层逻辑被改坏，数据库也不允许出现负人数或超卖
    CONSTRAINT ck_activity_capacity CHECK (capacity > 0),
    CONSTRAINT ck_activity_joined CHECK (joined_count >= 0 AND joined_count <= capacity),
    CONSTRAINT ck_activity_time CHECK (end_time > start_time AND signup_deadline <= start_time),
    CONSTRAINT fk_activity_club FOREIGN KEY (club_id) REFERENCES club (id)
        ON DELETE RESTRICT ON UPDATE CASCADE,
    CONSTRAINT fk_activity_creator FOREIGN KEY (created_by) REFERENCES sys_user (id)
        ON DELETE RESTRICT ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='活动';

-- -------------------------------------------------------------
-- 活动报名
-- uk_registration_activity_user：同一用户在同一活动永远只有一行。
-- 取消报名是把 status 改成 2，重新报名是复用该行改回 1，
-- 因此「同一用户最多一条有效报名」是结构保证，不依赖业务判断。
-- -------------------------------------------------------------
CREATE TABLE activity_registration
(
    id            BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_id   BIGINT UNSIGNED NOT NULL,
    user_id       BIGINT UNSIGNED NOT NULL,
    status        TINYINT         NOT NULL DEFAULT 1 COMMENT '1 已报名，2 已取消',
    registered_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '最近一次报名成功时间',
    cancelled_at  DATETIME                 DEFAULT NULL COMMENT '取消时间',
    created_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    updated_at    DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_registration_activity_user (activity_id, user_id),
    KEY idx_registration_user_status (user_id, status),
    KEY idx_registration_activity_status (activity_id, status),
    CONSTRAINT fk_registration_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_registration_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='活动报名';

-- -------------------------------------------------------------
-- 活动签到
-- 唯一约束保证同一用户在同一活动最多一条签到记录。
-- -------------------------------------------------------------
CREATE TABLE activity_attendance
(
    id              BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    activity_id     BIGINT UNSIGNED NOT NULL,
    user_id         BIGINT UNSIGNED NOT NULL,
    checked_in_at   DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    volunteer_hours DECIMAL(4, 1)   NOT NULL DEFAULT 0.0 COMMENT '本次计入的志愿时长',
    method          VARCHAR(20)     NOT NULL DEFAULT 'MANUAL' COMMENT 'MANUAL / QR_CODE',
    created_at      DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_attendance_activity_user (activity_id, user_id),
    KEY idx_attendance_user (user_id, checked_in_at),
    CONSTRAINT fk_attendance_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_attendance_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='活动签到';

-- -------------------------------------------------------------
-- 活动收藏
-- -------------------------------------------------------------
CREATE TABLE activity_favorite
(
    id          BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id     BIGINT UNSIGNED NOT NULL,
    activity_id BIGINT UNSIGNED NOT NULL,
    created_at  DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    UNIQUE KEY uk_favorite_user_activity (user_id, activity_id),
    KEY idx_favorite_activity (activity_id),
    CONSTRAINT fk_favorite_activity FOREIGN KEY (activity_id) REFERENCES activity (id)
        ON DELETE CASCADE ON UPDATE CASCADE,
    CONSTRAINT fk_favorite_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='活动收藏';

-- -------------------------------------------------------------
-- 站内通知
-- -------------------------------------------------------------
CREATE TABLE notification
(
    id         BIGINT UNSIGNED NOT NULL AUTO_INCREMENT,
    user_id    BIGINT UNSIGNED NOT NULL COMMENT '接收人',
    title      VARCHAR(100)    NOT NULL,
    content    VARCHAR(500)    NOT NULL,
    type       VARCHAR(20)     NOT NULL DEFAULT 'SYSTEM' COMMENT 'REGISTRATION / ATTENDANCE / CLUB / SYSTEM',
    is_read    TINYINT         NOT NULL DEFAULT 0 COMMENT '0 未读，1 已读',
    read_at    DATETIME                 DEFAULT NULL,
    created_at DATETIME        NOT NULL DEFAULT CURRENT_TIMESTAMP,
    PRIMARY KEY (id),
    -- 覆盖「查我的未读通知，按时间倒序」这一条最高频查询
    KEY idx_notification_user_read (user_id, is_read, created_at),
    CONSTRAINT fk_notification_user FOREIGN KEY (user_id) REFERENCES sys_user (id)
        ON DELETE CASCADE ON UPDATE CASCADE
) ENGINE = InnoDB
  DEFAULT CHARSET = utf8mb4
  COLLATE = utf8mb4_0900_ai_ci COMMENT ='站内通知';
