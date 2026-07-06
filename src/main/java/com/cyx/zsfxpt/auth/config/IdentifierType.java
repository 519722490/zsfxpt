package com.cyx.zsfxpt.auth.config;

//枚举类，用于判断用户是 手机号登录 还是 邮箱登录
public enum IdentifierType {
    PHONE,
    EMAIL;

    public static IdentifierType fromString(String value) {
        if (value == null) {
            throw new IllegalArgumentException("identifier type required");
        }
        return switch (value.toLowerCase()) {
            case "phone", "mobile" -> PHONE;
            case "email" -> EMAIL;
            default -> throw new IllegalArgumentException("Unsupported identifier type: " + value);
        };
    }
}
