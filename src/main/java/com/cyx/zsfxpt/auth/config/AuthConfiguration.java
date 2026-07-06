package com.cyx.zsfxpt.auth.config;
//OAuth2 Resource Server：用于校验 Bearer JWT（无状态 API 鉴权）
//Spring Security：认证/鉴权框架基础
import com.nimbusds.jose.jwk.JWKSet;
import com.nimbusds.jose.jwk.RSAKey;
import com.nimbusds.jose.jwk.source.ImmutableJWKSet;
import com.nimbusds.jose.jwk.source.JWKSource;
import com.nimbusds.jose.proc.SecurityContext;
import lombok.RequiredArgsConstructor;
import org.springframework.boot.context.properties.EnableConfigurationProperties;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.security.crypto.password.PasswordEncoder;
import org.springframework.security.oauth2.jwt.JwtDecoder;
import org.springframework.security.oauth2.jwt.JwtEncoder;
import org.springframework.security.oauth2.jwt.NimbusJwtDecoder;
import org.springframework.security.oauth2.jwt.NimbusJwtEncoder;

import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;

//这个类就是提供了一个密码明文加密器，一个jwt编码器，和一个jwt解码器，都是调的库，没啥看的
/**
 * 认证相关 Bean 配置。
 * <p>
 * - `PasswordEncoder`：根据配置的 BCrypt 强度创建；
 * - `JwtEncoder/Decoder`：读取配置中的 RSA 私钥/公钥并构造 Nimbus 实现；
 * - JWK 使用 `keyId` 标识，供下游验证与密钥轮换。
 */
@Configuration
@EnableConfigurationProperties(AuthProperties.class)
@RequiredArgsConstructor
public class AuthConfiguration {

    private final AuthProperties properties;

    /**
     * 创建密码编码器（BCrypt）。
     *
     * @return 使用配置的强度构造的 {@link PasswordEncoder}。
     */
    @Bean
    public PasswordEncoder passwordEncoder() {
        //getBcryptStrength获取密码哈希强度（BCrypt cost）
        //BCrypt 是哈希算法，通常不能反解。
        return new BCryptPasswordEncoder(properties.getPassword().getBcryptStrength());
    }

    /**
     * 创建 JWT 编码器。
     *
     * <p>读取 RSA 私钥/公钥并构造 JWK，使用 Nimbus 实现生成 {@link JwtEncoder}。</p>
     *
     * @return 基于 RSA JWK 的 {@link JwtEncoder}。
     */
    @Bean
    public JwtEncoder jwtEncoder() {
        AuthProperties.Jwt jwtProps = properties.getJwt();//获取jwt相关的配置类
        RSAPrivateKey privateKey = PemUtils.readPrivateKey(jwtProps.getPrivateKey());//读取私钥
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());//读取公钥
        //JWK 就是把密钥用 JSON 格式描述出来。包含私钥公钥以及kid密钥编号。
        RSAKey jwk = new RSAKey.Builder(publicKey)//设置RSA的公钥
                .privateKey(privateKey)//设置私钥
                .keyID(jwtProps.getKeyId())//JWK 密钥标识（kid），用于下游校验与轮换
                .build();
        JWKSource<SecurityContext> jwkSource = new ImmutableJWKSet<>(new JWKSet(jwk));
        return new NimbusJwtEncoder(jwkSource);
        //Nimbus 是一个 Java JWT/JWK/JOSE 相关的成熟库。
        //Spring 定义 JwtEncoder/JwtDecoder 接口
        //Nimbus 提供具体实现
    }

    /**
     * 创建 JWT 解码器。
     *
     * <p>读取 RSA 公钥并构造基于 Nimbus 的 {@link JwtDecoder}。</p>
     *
     * @return 基于 RSA 公钥的 {@link JwtDecoder}。
     */
    @Bean
    public JwtDecoder jwtDecoder() {
        AuthProperties.Jwt jwtProps = properties.getJwt();
        RSAPublicKey publicKey = PemUtils.readPublicKey(jwtProps.getPublicKey());
        return NimbusJwtDecoder.withPublicKey(publicKey).build();
    }
}