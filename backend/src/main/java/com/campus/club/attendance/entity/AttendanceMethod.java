package com.campus.club.attendance.entity;

/**
 * 签到方式。
 * <p>
 * 用常量而不是枚举，是因为它只落库、不参与业务判断：
 * 三种方式走的是同一条写库路径，区别只在「谁发起的」——
 * 以后真要按方式做统计（比如扫码签到占比），再升级成枚举也不迟。
 */
public final class AttendanceMethod {

    private AttendanceMethod() {
    }

    /** 本人签到 */
    public static final String MANUAL = "MANUAL";

    /** 社团负责人代签（补录） */
    public static final String ADMIN = "ADMIN";

    /** 扫码签到（字段已预留，接口尚未实现） */
    public static final String QR_CODE = "QR_CODE";
}
