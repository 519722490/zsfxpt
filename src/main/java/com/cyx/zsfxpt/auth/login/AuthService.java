package com.cyx.zsfxpt.auth.login;

import com.cyx.zsfxpt.auth.config.AuthProperties;
import com.cyx.zsfxpt.auth.config.ClientInfo;
import com.cyx.zsfxpt.auth.config.IdentifierType;
import com.cyx.zsfxpt.auth.config.IdentifierValidator;
import com.cyx.zsfxpt.auth.jwt.JwtService;
import com.cyx.zsfxpt.auth.jwt.RefreshTokenStore;
import com.cyx.zsfxpt.auth.jwt.TokenPair;
import com.cyx.zsfxpt.auth.login.DTO.*;
import com.cyx.zsfxpt.auth.login.VO.AuthResponse;
import com.cyx.zsfxpt.auth.login.VO.AuthUserResponse;
import com.cyx.zsfxpt.auth.login.VO.SendCodeResponse;
import com.cyx.zsfxpt.auth.login.VO.TokenResponse;
import com.cyx.zsfxpt.auth.loginlog.LoginLogService;
import com.cyx.zsfxpt.auth.verification.*;
import com.cyx.zsfxpt.config.Exception.BusinessException;
import com.cyx.zsfxpt.config.Exception.ErrorCode;
import com.cyx.zsfxpt.user.entity.User;
import com.cyx.zsfxpt.user.service.UserService;
import lombok.RequiredArgsConstructor;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.Jwt;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.time.Duration;
import java.time.Instant;
import java.util.Locale;
import java.util.Objects;
import java.util.Optional;
import java.util.UUID;
import org.springframework.security.oauth2.jwt.JwtException;

/**
 * 认证业务服务。
 * <p>
 * 职责：发送验证码、注册、登录、刷新令牌、登出、重置密码、查询当前用户信息。
 * 安全策略：
 * - 账号格式校验（手机号/邮箱）；
 * - 验证码状态检查（过期/错误/尝试超限）；
 * - 密码复杂度校验（长度与字符类型）；
 * - Refresh Token 白名单存储与轮换，登出/重置密码后失效旧令牌；
 * 审计：记录注册/登录成功与失败，包含渠道、IP、UA。
 * 令牌：签发 RS256 的 Access/Refresh JWT，携带 uid、token_type、jti。
 * 依赖：UserService、VerificationService、PasswordEncoder、JwtService、RefreshTokenStore、LoginLogService、AuthProperties。
 */
@Service
@RequiredArgsConstructor
public class AuthService {

    private final UserService userService;//查询、创建用户以及更新密码
    private final VerificationService verificationService;//发送和校验验证码
    private final PasswordEncoder passwordEncoder;//生成密码哈希并校验密码是否正确
    private final JwtService jwtService;//签发和解析JWT令牌
    private final RefreshTokenStore refreshTokenStore;//管理刷新令牌白名单
    private final LoginLogService loginLogService;//记录注册和登录日志
    private final AuthProperties authProperties;//读取认证模块的配置

    /**
     * 发送验证码并返回过期信息。
     * <p>
     * 注册场景要求标识不存在；登录/重置密码场景要求标识存在。
     *
     * @param request 请求体，包含：标识类型与值、场景。
     * @return 响应体，包含目标标识、场景与验证码过期秒数。
     * @throws BusinessException 当标识格式错误或存在性不符合场景要求时抛出。
     */
    public SendCodeResponse sendCode(SendCodeRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());//检查手机号或邮箱格式
        String normalized = normalizeIdentifier(request.identifierType(), request.identifier());//统一账号格式，方便查询和存储
        boolean exists = identifierExists(request.identifierType(), normalized);//查询该账号是否已经注册
        if (request.scene() == VerificationScene.REGISTER && exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);//注册时账号不能已经存在
        }
        if ((request.scene() == VerificationScene.LOGIN || request.scene() == VerificationScene.RESET_PASSWORD) && !exists) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);//登录和重置密码要求账号已经存在
        }
        SendCodeResult result = verificationService.sendCode(request.scene(), normalized);//生成、保存并发送验证码
        return new SendCodeResponse(result.identifier(), result.scene(), result.expireSeconds());//转换成接口响应对象
    }

    /**
     * 注册用户并签发令牌。
     * <p>
     * 验证标识与验证码，创建用户（可选设置密码），记录审计，签发令牌对并保存刷新令牌白名单。
     *
     * @param request    注册请求，包含：标识类型与值、验证码、可选密码、是否同意协议。
     * @param clientInfo 客户端信息（IP/UA），用于登录审计。
     * @return 认证响应，包含用户信息与令牌对。
     * @throws BusinessException 当未同意协议、标识冲突、验证码失败、密码不合规时抛出。
     */
    public AuthResponse register(RegisterRequest request, ClientInfo clientInfo) {
        if (!request.agreeTerms()) {
            throw new BusinessException(ErrorCode.TERMS_NOT_ACCEPTED);//不同意服务条款不能注册
        }
        validateIdentifier(request.identifierType(), request.identifier());//检查手机号或邮箱格式
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());//得到统一格式的账号
        if (identifierExists(request.identifierType(), identifier)) {
            throw new BusinessException(ErrorCode.IDENTIFIER_EXISTS);//防止同一账号重复注册
        }
        ensureVerificationSuccess(verificationService.verify(VerificationScene.REGISTER, identifier, request.code()));//校验注册验证码

        //根据手机号或邮箱组装一个新用户
        User user = User.builder()
                .phone(request.identifierType() == IdentifierType.PHONE ? identifier : null)//手机号注册时保存手机号
                .email(request.identifierType() == IdentifierType.EMAIL ? identifier : null)//邮箱注册时保存邮箱
                .nickname(generateNickname())//生成默认昵称
                .avatar("https://static.zhiguang.cn/default-avatar.png")//设置默认头像
                .bio(null)//个人简介默认留空
                .tagsJson("[]")//用户标签默认是空数组
                .build();//完成用户对象创建

        if (StringUtils.hasText(request.password())) {
            validatePassword(request.password());//填写了密码就检查密码强度
            user.setPasswordHash(passwordEncoder.encode(request.password().trim()));//只保存不可逆的密码哈希
        }

        userService.createUser(user);//把新用户写入数据库
        TokenPair tokenPair = jwtService.issueTokenPair(user);//注册成功后直接签发访问令牌和刷新令牌
        storeRefreshToken(user.getId(), tokenPair);//把刷新令牌编号加入白名单
        loginLogService.record(user.getId(), identifier, "REGISTER", clientInfo.ip(), clientInfo.userAgent(), "SUCCESS");//记录注册成功日志

        return new AuthResponse(mapUser(user), mapToken(tokenPair));//返回用户资料和令牌
    }

    /**
     * 登录并签发令牌。
     * <p>
     * 支持密码或验证码通道；成功后记录审计，签发令牌对并保存刷新令牌白名单。
     *
     * @param request    登录请求，包含：标识类型与值、密码或验证码（二选一）。
     * @param clientInfo 客户端信息（IP/UA），用于登录审计。
     * @return 认证响应，包含用户信息与令牌对。
     * @throws BusinessException 当用户不存在、凭证错误或请求不合法时抛出。
     */
    public AuthResponse login(LoginRequest request, ClientInfo clientInfo) {
        validateIdentifier(request.identifierType(), request.identifier());//检查手机号或邮箱格式
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());//得到统一格式的账号
        Optional<User> userOptional = findUserByIdentifier(request.identifierType(), identifier);//按账号类型查询用户
        if (userOptional.isEmpty()) {
            throw new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND);//账号不存在时终止登录
        }
        User user = userOptional.get();//取出已经查到的用户
        String channel;//记录本次使用密码还是验证码登录
        if (StringUtils.hasText(request.password())) {
            channel = "PASSWORD";//优先走密码登录
            if (!StringUtils.hasText(user.getPasswordHash()) || !passwordEncoder.matches(request.password(), user.getPasswordHash())) {
                loginLogService.record(user.getId(), identifier, channel, clientInfo.ip(), clientInfo.userAgent(), "FAILED");//记录密码登录失败
                throw new BusinessException(ErrorCode.INVALID_CREDENTIALS);//密码未设置或密码不匹配
            }
        } else if (StringUtils.hasText(request.code())) {
            channel = "CODE";//没有密码时尝试走验证码登录
            ensureVerificationSuccess(verificationService.verify(VerificationScene.LOGIN, identifier, request.code()));//校验登录验证码
        } else {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "请提供验证码或密码");//两种登录凭证至少提供一种
        }
        TokenPair tokenPair = jwtService.issueTokenPair(user);//登录成功后生成一对新令牌
        storeRefreshToken(user.getId(), tokenPair);//把刷新令牌编号加入白名单,允许这个刷新令牌以后换取新令牌
        loginLogService.record(user.getId(), identifier, channel, clientInfo.ip(), clientInfo.userAgent(), "SUCCESS");//记录登录成功日志
        return new AuthResponse(mapUser(user), mapToken(tokenPair));//返回用户资料和令牌
    }

    /**
     * 使用刷新令牌获取新的令牌对。
     * <p>
     * 校验刷新令牌类型与白名单有效性，签发新令牌后撤销旧刷新令牌并存储新令牌。
     *
     * @param request 刷新请求，包含：refreshToken。
     * @return 新的令牌响应。
     * @throws BusinessException 当刷新令牌无效或用户不存在时抛出。
     */
    public TokenResponse refresh(TokenRefreshRequest request) {
        Jwt jwt = decodeRefreshToken(request.refreshToken());//解析并验证刷新令牌的签名和有效期

        if (!Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);//禁止拿访问令牌调用刷新接口
        }

        long userId = jwtService.extractUserId(jwt);//取出令牌所属的用户ID
        String tokenId = jwtService.extractTokenId(jwt);//取出刷新令牌id

        if (!refreshTokenStore.isTokenValid(userId, tokenId)) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);//白名单中不存在说明令牌已失效
        }

        User user = findUserById(userId).orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));//确认用户仍然存在
        TokenPair tokenPair = jwtService.issueTokenPair(user);//生成新的访问令牌和刷新令牌
        refreshTokenStore.revokeToken(userId, tokenId);//将旧的刷新令牌移除
        storeRefreshToken(userId, tokenPair);//将新刷新令牌加入白名单

        return mapToken(tokenPair);//返回轮换后的新令牌对
    }

    /**
     * 登出：撤销指定刷新令牌。
     *
     * @param refreshToken 刷新令牌字符串；若解析为合法刷新令牌则撤销其白名单记录。
     */
    public void logout(String refreshToken) {
        //登出接口不因无效令牌报错，能解析成功时才尝试撤销
        decodeRefreshTokenSafely(refreshToken).ifPresent(jwt -> {
            if (Objects.equals("refresh", jwtService.extractTokenType(jwt))) {
                long userId = jwtService.extractUserId(jwt);//取出用户ID
                String tokenId = jwtService.extractTokenId(jwt);//取出刷新令牌编号
                refreshTokenStore.revokeToken(userId, tokenId);//从白名单删除，完成登出
            }
        });
    }

    /**
     * 使用验证码重置密码并使刷新令牌失效。
     *
     * @param request 重置请求，包含：标识类型与值、验证码、新密码。
     * @throws BusinessException 当标识不存在、验证码失败或密码策略不满足时抛出。
     */
    public void resetPassword(PasswordResetRequest request) {
        validateIdentifier(request.identifierType(), request.identifier());//检查手机号或邮箱格式
        validatePassword(request.newPassword());//检查新密码是否符合安全策略
        String identifier = normalizeIdentifier(request.identifierType(), request.identifier());//得到统一格式的账号
        User user = findUserByIdentifier(request.identifierType(), identifier)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));//找到需要重置密码的用户
        ensureVerificationSuccess(verificationService.verify(VerificationScene.RESET_PASSWORD, identifier, request.code()));//用验证码确认身份
        user.setPasswordHash(passwordEncoder.encode(request.newPassword().trim()));//生成新密码摘要
        userService.updatePassword(user);//把新密码摘要写入数据库
        refreshTokenStore.revokeAll(user.getId());//让该用户以前的所有刷新令牌失效
    }

    /**
     * 查询用户概要信息。
     *
     * @param userId 用户 ID。
     * @return 用户概要响应。
     * @throws BusinessException 当用户不存在时抛出。
     */
    public AuthUserResponse me(long userId) {
        User user = findUserById(userId)
                .orElseThrow(() -> new BusinessException(ErrorCode.IDENTIFIER_NOT_FOUND));//根据登录用户ID查询资料
        return mapUser(user);//只返回允许暴露给客户端的字段
    }

    /**
     * 保证验证码校验成功，否则按状态抛出对应业务异常。
     *
     * @param result 验证码校验结果。
     */
    private void ensureVerificationSuccess(VerificationCheckResult result) {
        if (result.isSuccess()) {
            return;//验证码正确时直接结束检查
        }
        VerificationCodeStatus status = result.status();//读取验证码失败的具体原因
        if (status == VerificationCodeStatus.NOT_FOUND || status == VerificationCodeStatus.EXPIRED) {
            throw new BusinessException(ErrorCode.VERIFICATION_NOT_FOUND);//验证码不存在或已经过期
        }
        if (status == VerificationCodeStatus.MISMATCH) {
            throw new BusinessException(ErrorCode.VERIFICATION_MISMATCH);//用户输入的验证码不正确
        }
        if (status == VerificationCodeStatus.TOO_MANY_ATTEMPTS) {
            throw new BusinessException(ErrorCode.VERIFICATION_TOO_MANY_ATTEMPTS);//错误次数超过限制
        }
        throw new BusinessException(ErrorCode.BAD_REQUEST, "验证码校验失败");//兜底处理未知状态
    }

    /**
     * 校验标识（手机号/邮箱）的格式。
     *
     * @param type       标识类型：PHONE 或 EMAIL。
     * @param identifier 标识值。
     * @throws BusinessException 当格式不合法时抛出。
     */
    private void validateIdentifier(IdentifierType type, String identifier) {
        if (type == IdentifierType.PHONE && !IdentifierValidator.isValidPhone(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "手机号格式错误");//手机号没有通过格式校验
        }
        if (type == IdentifierType.EMAIL && !IdentifierValidator.isValidEmail(identifier)) {
            throw new BusinessException(ErrorCode.BAD_REQUEST, "邮箱格式错误");//邮箱没有通过格式校验
        }
    }

    /**
     * 校验密码策略：非空、最小长度、必须包含字母和数字。
     *
     * @param password 明文密码。
     * @throws BusinessException 当密码不满足策略时抛出。
     */
    private void validatePassword(String password) {
        if (!StringUtils.hasText(password)) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码不能为空");//拒绝空密码或全空格密码
        }
        String trimmed = password.trim();//去掉密码两端多余空格
        if (trimmed.length() < authProperties.getPassword().getMinLength()) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码长度至少" + authProperties.getPassword().getMinLength() + "位");//检查配置要求的最小长度
        }
        boolean hasLetter = trimmed.chars().anyMatch(Character::isLetter);//检查是否至少包含一个字母
        boolean hasDigit = trimmed.chars().anyMatch(Character::isDigit);//检查是否至少包含一个数字
        if (!hasLetter || !hasDigit) {
            throw new BusinessException(ErrorCode.PASSWORD_POLICY_VIOLATION, "密码需包含字母和数字");//字母和数字必须同时存在
        }
    }

    /**
     * 判断标识是否已存在。
     *
     * @param type       标识类型：PHONE 或 EMAIL。
     * @param identifier 标识值（需为标准化格式）。
     * @return 是否存在。
     */
    private boolean identifierExists(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.existsByPhone(identifier);//手机号账号就按手机号查询
            case EMAIL -> userService.existsByEmail(identifier);//邮箱账号就按邮箱查询
        };
    }

    /**
     * 根据标识查找用户。
     *
     * @param type       标识类型：PHONE 或 EMAIL。
     * @param identifier 标识值（需为标准化格式）。
     * @return 用户 Optional。
     */
    private Optional<User> findUserByIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> userService.findByPhone(identifier);//按手机号查找用户
            case EMAIL -> userService.findByEmail(identifier);//按邮箱查找用户
        };
    }

    /**
     * 根据 ID 查找用户。
     *
     * @param userId 用户 ID。
     * @return 用户 Optional。
     */
    private Optional<User> findUserById(long userId) {
        return userService.findById(userId);//把具体的数据库查询交给用户服务
    }

    /**
     * 标准化标识文本：手机号去空格、邮箱转小写并去空格。
     *
     * @param type       标识类型：PHONE 或 EMAIL。
     * @param identifier 原始标识文本。
     * @return 标准化后的标识文本。
     */
    private String normalizeIdentifier(IdentifierType type, String identifier) {
        return switch (type) {
            case PHONE -> identifier.trim();//手机号只去掉两端空格
            case EMAIL -> identifier.trim().toLowerCase(Locale.ROOT);//邮箱去空格并统一转成小写
        };
    }

    /**
     * 存储刷新令牌白名单记录。
     *
     * @param userId    用户 ID。
     * @param tokenPair 令牌对（含刷新令牌 ID 与过期时间）。
     */
    private void storeRefreshToken(Long userId, TokenPair tokenPair) {
        Duration ttl = Duration.between(Instant.now(), tokenPair.refreshTokenExpiresAt());//计算刷新令牌还剩多久过期
        if (ttl.isNegative()) {
            ttl = Duration.ZERO;//已经过期时避免传入负数有效期
        }
        refreshTokenStore.storeToken(userId, tokenPair.refreshTokenId(), ttl);//按相同有效期写入刷新令牌白名单
    }

    /**
     * 映射用户实体到响应对象。
     *
     * @param user 用户实体。
     * @return 用户响应。
     */
    private AuthUserResponse mapUser(User user) {
        //只挑选允许返回给客户端的用户字段，避免直接暴露数据库实体
        return new AuthUserResponse(
                user.getId(),
                user.getNickname(),
                user.getAvatar(),
                user.getPhone(),
                user.getZgId(),
                user.getBirthday(),
                user.getSchool(),
                user.getBio(),
                user.getGender(),
                user.getTagsJson()
        );
    }

    /**
     * 映射令牌对到响应对象。
     *
     * @param tokenPair 令牌对。
     * @return 令牌响应。
     */
    private TokenResponse mapToken(TokenPair tokenPair) {
        return new TokenResponse(tokenPair.accessToken(), tokenPair.accessTokenExpiresAt(), tokenPair.refreshToken(), tokenPair.refreshTokenExpiresAt());//去掉只供服务端使用的刷新令牌ID
    }

    /**
     * 生成默认昵称。
     *
     * @return 随机昵称字符串。
     */
    private String generateNickname() {
        return "知光用户" + UUID.randomUUID().toString().substring(0, 8);//取UUID前8位拼成默认昵称
    }

    /**
     * 解码刷新令牌，失败时抛业务异常。
     *
     * @param refreshToken 刷新令牌字符串。
     * @return 解析得到的 JWT。
     * @throws BusinessException 当刷新令牌无法解析时抛出。
     */
    private Jwt decodeRefreshToken(String refreshToken) {
        try {
            return jwtService.decode(refreshToken);//校验签名、有效期并解析令牌内容
        } catch (JwtException ex) {
            throw new BusinessException(ErrorCode.REFRESH_TOKEN_INVALID);//把框架异常转换成统一业务错误
        }
    }

    /**
     * 安全解码刷新令牌，失败时返回空 Optional。
     *
     * @param refreshToken 刷新令牌字符串。
     * @return 成功时返回 JWT，失败时返回 Optional.empty()。
     */
    private Optional<Jwt> decodeRefreshTokenSafely(String refreshToken) {
        try {
            return Optional.of(jwtService.decode(refreshToken));//解析成功就用Optional包装JWT
        } catch (JwtException ex) {
            return Optional.empty();//解析失败返回空，不让登出接口报错
        }
    }
}
