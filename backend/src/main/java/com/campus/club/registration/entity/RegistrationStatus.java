package com.campus.club.registration.entity;

/**
 * 报名记录状态。
 * <p>
 * 只有两种状态，不做「审核中」之类中间态：校园活动的报名是即时的。
 * 需要审核的是社团入社申请（club_membership），不是活动报名。
 */
public final class RegistrationStatus {

    private RegistrationStatus() {
    }

    /** 已报名，占用一个名额 */
    public static final int REGISTERED = 1;

    /** 已取消，不占名额，但保留记录用于重新报名时复用 */
    public static final int CANCELLED = 2;

    public static boolean isRegistered(Integer status) {
        return status != null && status == REGISTERED;
    }

    public static boolean isCancelled(Integer status) {
        return status != null && status == CANCELLED;
    }
}
