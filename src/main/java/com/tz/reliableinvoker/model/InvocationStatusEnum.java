package com.tz.reliableinvoker.model;

/**
 * 调用记录状态枚举
 *
 * @author tuanzuo use AI
 * @time 2026-06-08 17:00:00
 * @version 1.0.0-SNAPSHOT
 */
public enum InvocationStatusEnum {

    /** 待执行 */
    PENDING(0),

    /** 执行中 */
    EXECUTING(1),

    /** 成功 */
    SUCCESS(2),

    /** 失败 */
    FAILED(3);

    private final int code;

    InvocationStatusEnum(int code) {
        this.code = code;
    }

    public int getCode() {
        return code;
    }

    /**
     * 根据code值获取对应的枚举
     *
     * @param code 状态码
     * @return 对应的枚举值
     * @throws IllegalArgumentException 如果code值无效
     */
    public static InvocationStatusEnum fromCode(int code) {
        for (InvocationStatusEnum s : values()) {
            if (s.code == code) {
                return s;
            }
        }
        throw new IllegalArgumentException("未知状态: " + code);
    }
}
