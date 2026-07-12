package com.cyx.zsfxpt.auth.loginlog;

import lombok.AllArgsConstructor;
import lombok.Builder;
import lombok.Data;
import lombok.NoArgsConstructor;

import java.time.Instant;

@Data
@Builder
@NoArgsConstructor
@AllArgsConstructor
public class LoginLog {

    private Long id;
    private Long userId;//用户ID
    private String identifier;//手机号、邮箱
    private String channel;//登录渠道:密码登录、验证码登录
    private String ip;//登录IP地址
    private String userAgent;//浏览器或客户端User-Agent,用于识别设备和客户端信息
    private String status;//登录状态
    private Instant createdAt;//创建时间
}