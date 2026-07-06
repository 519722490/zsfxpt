package com.cyx.zsfxpt.auth.config;

import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.config.Customizer;
import org.springframework.security.config.annotation.method.configuration.EnableMethodSecurity;
import org.springframework.security.config.annotation.web.builders.HttpSecurity;
import org.springframework.security.config.annotation.web.configuration.EnableWebSecurity;
import org.springframework.security.config.annotation.web.configurers.AbstractHttpConfigurer;
import org.springframework.security.config.http.SessionCreationPolicy;
import org.springframework.security.web.SecurityFilterChain;
import org.springframework.web.cors.CorsConfiguration;
import org.springframework.web.cors.CorsConfigurationSource;
import org.springframework.web.cors.UrlBasedCorsConfigurationSource;

import java.util.List;

/**
 * Spring Security 安全配置。
 * <p>
 * - 关闭 CSRF（后端纯 API，使用 JWT 无会话）；
 * - 启用 CORS，当前允许所有来源（后续需替换白名单）；
 * - 无状态会话；
 * - 公开认证相关接口与健康检查，其余接口需鉴权；
 * - 资源服务器启用 JWT 校验。
 */
@Configuration
@EnableWebSecurity//让 Spring Security 接管 HTTP 请求的安全检查。
@EnableMethodSecurity//开启方法级别权限控制。@PreAuthorize("hasRole('ADMIN')")代表，只有 ADMIN 角色才能调用这个方法。
public class SecurityConfig {

    /**
     * 配置 Spring Security 过滤链。
     *
     * <p>主要包含：</p>
     * - 关闭 CSRF；
     * - 启用 CORS；
     * - 使用无状态会话策略；
     * - 公开认证接口与健康检查，其余接口需鉴权；
     * - 启用资源服务器的 JWT 校验。
     *
     * @param http Spring 的 {@link HttpSecurity} 构建器。
     * @return 构建完成的 {@link SecurityFilterChain}。
     * @throws Exception 构建过滤链过程中可能抛出的异常。
     */
    @Bean
    //SecurityFilterChain 可以理解成：一条安全过滤链。用户请求接口时，不是直接到 Controller，而是先经过 Spring Security 的一堆过滤器.
    public SecurityFilterChain securityFilterChain(HttpSecurity http) throws Exception {
        http
                .csrf(AbstractHttpConfigurer::disable)//关闭 CSRF，CSRF 主要是防传统 Cookie + Session 网站被跨站攻击。本项目使用jwt，不需要
                .cors(Customizer.withDefaults())//启用 CORS，后端允许前端跨域访问接口。
                //设置为无状态会话：传统登录：登录成功 -> 服务端保存 Session -> 浏览器保存 Cookie，本项目是jwt不需要会话有状态
                .sessionManagement(session -> session.sessionCreationPolicy(SessionCreationPolicy.STATELESS))
                .authorizeHttpRequests(auth -> auth
                        //健康检查接口放行。通常给服务器、Docker、监控系统检查本后端服务是否正常。
                        .requestMatchers("/actuator/health", "/actuator/info").permitAll()
                        // 公开内容：首页 Feed 不需要登录，首页内容列表不用登录也能看
                        .requestMatchers("/api/v1/knowposts/feed").permitAll()
                        // 知文详情（公开已发布内容，非公开由服务层校验）文章详情 GET 请求放行。看公开文章详情不用登录
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/knowposts/detail/*").permitAll()
                        // 知文详情页 RAG 问答（SSE 流式输出）允许匿名访问
                        .requestMatchers(org.springframework.http.HttpMethod.GET, "/api/v1/knowposts/*/qa/stream").permitAll()
                        .requestMatchers(
                                "/api/v1/auth/send-code",
                                "/api/v1/auth/register",
                                "/api/v1/auth/login",
                                "/api/v1/auth/token/refresh",
                                "/api/v1/auth/logout",
                                "/api/v1/auth/password/reset"
                        ).permitAll()
                        //上面没放行的所有接口，都必须登录。
                        .anyRequest().authenticated()
                )
                //把当前服务配置成 OAuth2 Resource Server，并使用 JWT 认证。OAuth2 Resource Server 大白话就是：资源服务器，也就是“保护接口资源的后端服务”。
                //注册完这个服务之后的流程就变成了：
                //前端请求接口
                //  ↓
                //请求头带 Authorization: Bearer JWT
                //  ↓
                //Spring Security 过滤链拦截
                //  ↓
                //OAuth2 Resource Server 提取 JWT
                //  ↓
                //JwtDecoder 用 RSA 公钥验签（是OAuth2 Resource Server自动找的JwtDecoder的Bean）
                //  ↓
                //验证通过：放行到 Controller
                //  ↓
                //验证失败：返回 401
                .oauth2ResourceServer(oauth -> oauth.jwt(Customizer.withDefaults()));
        return http.build();
    }

    /**
     * 定义并提供 CORS 配置源。
     *
     * <p>当前允许所有来源（后续建议替换为产品白名单），允许常见方法与请求头，且不携带凭证。</p>
     *
     * @return {@link CorsConfigurationSource}，用于为所有路径注册 CORS 规则。
     */
    @Bean
    public CorsConfigurationSource corsConfigurationSource() {
        CorsConfiguration configuration = new CorsConfiguration();
        //任何前端域名都可以调你的后端。
        configuration.setAllowedOrigins(List.of("*")); // TODO replace with product whitelist
        configuration.setAllowedMethods(List.of("GET", "POST", "PUT", "DELETE", "OPTIONS"));//允许这些请求方法
        configuration.setAllowedHeaders(List.of("Authorization", "Content-Type", "X-Requested-With"));//允许前端带这些请求头。
        configuration.setAllowCredentials(false);//表示不允许携带凭证。也就是不让浏览器跨域带 Cookie。
        UrlBasedCorsConfigurationSource source = new UrlBasedCorsConfigurationSource();
        source.registerCorsConfiguration("/**", configuration);//上面的跨域规则对所有接口生效。
        return source;
    }
}
