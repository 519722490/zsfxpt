package com.cyx.zsfxpt.auth.login;

import com.cyx.zsfxpt.auth.config.ClientInfo;
import com.cyx.zsfxpt.auth.jwt.JwtService;
import com.cyx.zsfxpt.auth.login.DTO.*;
import com.cyx.zsfxpt.auth.login.VO.AuthResponse;
import com.cyx.zsfxpt.auth.login.VO.AuthUserResponse;
import com.cyx.zsfxpt.auth.login.VO.SendCodeResponse;
import com.cyx.zsfxpt.auth.login.VO.TokenResponse;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.validation.Valid;
import lombok.RequiredArgsConstructor;
import org.springframework.http.ResponseEntity;
import org.springframework.security.core.annotation.AuthenticationPrincipal;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.validation.annotation.Validated;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;

/**
 * 认证 API 控制器。
 * <p>
 * 暴露 REST 接口：发送验证码、注册、登录、刷新令牌、登出、重置密码、查询当前用户信息。
 * 集成：使用 Spring Security 的资源服务器能力，`/me` 通过 `@AuthenticationPrincipal Jwt` 提取用户。
 * 客户端信息：从请求头解析 IP 与 UA，用于审计登录日志。
 */
@RestController//声明REST控制器，方法返回值会直接转成JSON响应
@RequestMapping("/api/v1/auth")//给本类所有接口统一添加认证模块路径前缀
@RequiredArgsConstructor//为final字段生成构造器，供Spring完成依赖注入
@Validated//开启控制器方法的参数校验能力
public class AuthController {

    private final AuthService authService;//处理注册、登录和令牌等认证业务
    private final JwtService jwtService;//从JWT中提取当前用户信息

    /**
     * 发送短信/邮箱验证码。
     * <p>
     * 根据场景（注册、登录、重置密码）向指定标识（手机号或邮箱）发送一次性验证码。
     *
     * @param request 请求体，包含：
     *                - identifierType：标识类型，PHONE 或 EMAIL；
     *                - identifier：手机号或邮箱地址；
     *                - scene：验证码使用场景（REGISTER/LOGIN/RESET_PASSWORD）。
     * @return 响应体，包含目标标识、场景以及验证码过期秒数。
     */
    @PostMapping("/send-code")//接收POST /api/v1/auth/send-code
    public SendCodeResponse sendCode(@Valid @RequestBody SendCodeRequest request) {
        return authService.sendCode(request);//把经过校验的请求交给认证服务处理
    }

    /**
     * 注册新用户并自动登录。
     * <p>
     * 验证标识与验证码后创建用户，若提供密码则进行复杂度校验并保存密码哈希；成功后签发 Access/Refresh Token。
     *
     * @param request     请求体，包含：标识类型与值、验证码、可选密码、是否同意协议。
     * @param httpRequest 用于解析客户端信息（IP 与 User-Agent），记录审计日志。
     * @return 认证响应，包含用户信息与令牌对。
     */
    @PostMapping("/register")//接收POST /api/v1/auth/register
    public AuthResponse register(@Valid @RequestBody RegisterRequest request, HttpServletRequest httpRequest) {
        return authService.register(request, resolveClient(httpRequest));//同时传入注册数据和客户端信息
    }

    /**
     * 登录并获取令牌对。
     * <p>
     * 支持两种通道：密码登录或验证码登录；成功后签发 Access/Refresh Token。
     *
     * @param request     请求体，包含：标识类型与值、密码或验证码（二选一）。
     * @param httpRequest 用于解析客户端信息（IP 与 User-Agent），记录审计日志。
     * @return 认证响应，包含用户信息与令牌对。
     */
    @PostMapping("/login")//接收POST /api/v1/auth/login
    public AuthResponse login(@Valid @RequestBody LoginRequest request, HttpServletRequest httpRequest) {
        return authService.login(request, resolveClient(httpRequest));//同时传入登录数据和客户端信息
    }

    /**
     * 使用 Refresh Token 刷新令牌。
     * <p>
     * 校验刷新令牌的合法性与白名单状态，签发新的令牌对，并撤销旧刷新令牌。
     *
     * @param request 请求体，包含：refreshToken（刷新令牌）。
     * @return 新的令牌响应（accessToken/refreshToken 及其过期时间）。
     */
    @PostMapping("/token/refresh")//接收POST /api/v1/auth/token/refresh
    public TokenResponse refresh(@Valid @RequestBody TokenRefreshRequest request) {
        return authService.refresh(request);//校验旧刷新令牌并换取一对新令牌
    }

    /**
     * 登出并撤销刷新令牌。
     * <p>
     * 若提供的令牌为合法的 Refresh Token，则撤销其白名单记录；返回 204，无响应体。
     *
     * @param request 请求体，包含：refreshToken（欲撤销的刷新令牌）。
     * @return 空响应，HTTP 204 No Content。
     */
    @PostMapping("/logout")//接收POST /api/v1/auth/logout
    public ResponseEntity<Void> logout(@Valid @RequestBody LogoutRequest request) {
        authService.logout(request.refreshToken());//从白名单撤销指定刷新令牌
        return ResponseEntity.noContent().build();//返回HTTP 204，表示成功但没有响应体
    }

    /**
     * 使用验证码重置密码。
     * <p>
     * 验证标识与验证码后更新用户密码哈希，并撤销该用户所有刷新令牌以强制下线。
     *
     * @param request 请求体，包含：标识类型与值、验证码、新密码。
     * @return 空响应，HTTP 204 No Content。
     */
    @PostMapping("/password/reset")//接收POST /api/v1/auth/password/reset
    public ResponseEntity<Void> resetPassword(@Valid @RequestBody PasswordResetRequest request) {
        authService.resetPassword(request);//校验验证码、更新密码并撤销旧刷新令牌
        return ResponseEntity.noContent().build();//返回HTTP 204，表示成功但没有响应体
    }

    /**
     * 查询当前登录用户信息。
     * <p>
     * 基于 Spring Security 注入的 `Jwt` 令牌，提取用户 ID 并返回用户概要信息。
     *
     * @param jwt 当前请求绑定的 JWT 令牌（来自 `Authorization: Bearer`）。
     * @return 用户信息响应。
     */
    @GetMapping("/me")//接收GET /api/v1/auth/me，该接口需要登录
    public AuthUserResponse me(@AuthenticationPrincipal Jwt jwt) {
        long userId = jwtService.extractUserId(jwt);//从已通过认证的JWT中取出用户ID
        return authService.me(userId);//查询并返回当前用户资料
    }

    /**
     * 从请求中解析客户端信息。
     *
     * @param request HTTP 请求对象。
     * @return 客户端信息（IP 与 User-Agent）。
     */
    private ClientInfo resolveClient(HttpServletRequest request) {
        String ip = extractClientIp(request);//解析经过代理转发后的客户端IP
        String ua = request.getHeader("User-Agent");//读取浏览器或客户端的设备信息
        return new ClientInfo(ip, ua);//封装成登录审计需要的客户端信息
    }

    /**
     * 提取客户端 IP 地址。
     * <p>
     * 优先使用代理头：`X-Forwarded-For`（取第一个）、`X-Real-IP`；否则回退到 `request.getRemoteAddr()`。
     *
     * @param request HTTP 请求对象。
     * @return 客户端 IP。
     */
    private String extractClientIp(HttpServletRequest request) {
        String forwarded = request.getHeader("X-Forwarded-For");//代理链可能把多个IP写在这个请求头中
        if (forwarded != null && !forwarded.isBlank()) {
            return forwarded.split(",")[0].trim();//第一个通常是最初发起请求的客户端IP
        }
        String realIp = request.getHeader("X-Real-IP");//尝试读取代理提供的真实客户端IP
        if (realIp != null && !realIp.isBlank()) {
            return realIp.trim();//去掉请求头两端可能存在的空格
        }
        return request.getRemoteAddr();//没有代理请求头时使用直接连接者的IP
    }
}
