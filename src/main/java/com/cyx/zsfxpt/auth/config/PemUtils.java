package com.cyx.zsfxpt.auth.config;

import org.springframework.core.io.Resource;

import java.io.IOException;
import java.io.InputStream;
import java.nio.charset.StandardCharsets;
import java.security.GeneralSecurityException;
import java.security.KeyFactory;
import java.security.interfaces.RSAPrivateKey;
import java.security.interfaces.RSAPublicKey;
import java.security.spec.PKCS8EncodedKeySpec;
import java.security.spec.X509EncodedKeySpec;
import java.util.Base64;

/**
 * PEM 密钥读取工具。
 * <p>
 * 支持从 `Resource` 读取 PKCS#8 私钥与 X.509 公钥，去除头尾与空白后进行 Base64 解码，
 * 生成 `RSAPrivateKey` 与 `RSAPublicKey`。用于 JWT 的 RS256 编解码配置。
 */
public final class PemUtils {

    //下面这个就是一个标识，用字符串匹配来定位公钥和私钥的开始和结束位置
    private static final String PRIVATE_BEGIN = "-----BEGIN PRIVATE KEY-----";
    private static final String PRIVATE_END = "-----END PRIVATE KEY-----";
    private static final String PUBLIC_BEGIN = "-----BEGIN PUBLIC KEY-----";
    private static final String PUBLIC_END = "-----END PUBLIC KEY-----";

    private PemUtils() {
    }

    /**
     * 从 PEM 资源读取 RSA 私钥（PKCS#8 格式）。
     *
     * @param resource Spring {@link org.springframework.core.io.Resource}，指向私钥 PEM 文件。
     * @return 解析得到的 {@link RSAPrivateKey}。
     * @throws IllegalStateException 当读取或解析失败时抛出。
     */
    //Resource 是 Spring 对“文件/资源”的统一封装，让你不用关心它到底来自 classpath、本地磁盘还是网络，都可以用同一套方式读取。
    public static RSAPrivateKey readPrivateKey(Resource resource) {
        try {
            // 先把 private.pem 这个资源文件完整读成字符串。
            String pem = readResource(resource);
            // PEM 文件不是纯密钥内容，它外面有 BEGIN/END 标记和换行。
            // 这里把头、尾、空白字符都去掉，只留下中间真正的 Base64 密钥数据。
            String keyData = pem.replace(PRIVATE_BEGIN, "")
                    .replace(PRIVATE_END, "")
                    .replaceAll("\\s", "");//掐头去尾去除完整密钥
            // PEM 中间的密钥内容是 Base64 文本，这里把它解码成原始二进制字节。
            byte[] keyBytes = Base64.getDecoder().decode(keyData);
            // 私钥文件使用 PKCS#8 格式，所以要用 PKCS8EncodedKeySpec 告诉 Java 这串字节是什么格式。
            PKCS8EncodedKeySpec spec = new PKCS8EncodedKeySpec(keyBytes);
            // 获取 RSA 的密钥工厂，后面由它把二进制密钥材料还原成 Java 私钥对象。
            KeyFactory kf = KeyFactory.getInstance("RSA");
            // 根据 PKCS#8 私钥规格生成真正可用的 RSA 私钥对象，JWT 签名时会用它。
            return (RSAPrivateKey) kf.generatePrivate(spec);
        } catch (IOException | GeneralSecurityException ex) {
            // 读取文件失败、Base64/密钥格式不对、RSA 解析失败，都会统一包装成运行时异常抛出去。
            throw new IllegalStateException("Failed to read RSA private key", ex);
        }
    }

    /**
     * 从 PEM 资源读取 RSA 公钥（X.509 格式）。
     *
     * @param resource Spring {@link org.springframework.core.io.Resource}，指向公钥 PEM 文件。
     * @return 解析得到的 {@link RSAPublicKey}。
     * @throws IllegalStateException 当读取或解析失败时抛出。
     */
    public static RSAPublicKey readPublicKey(Resource resource) {
        try {
            String pem = readResource(resource);
            String keyData = pem.replace(PUBLIC_BEGIN, "")
                    .replace(PUBLIC_END, "")
                    .replaceAll("\\s", "");
            byte[] keyBytes = Base64.getDecoder().decode(keyData);
            X509EncodedKeySpec spec = new X509EncodedKeySpec(keyBytes);
            KeyFactory kf = KeyFactory.getInstance("RSA");
            return (RSAPublicKey) kf.generatePublic(spec);
        } catch (IOException | GeneralSecurityException ex) {
            throw new IllegalStateException("Failed to read RSA public key", ex);
        }
    }

    /**
     * 读取给定资源的文本内容。
     *
     * @param resource 待读取的资源。
     * @return 使用 UTF-8 解码的文本内容。
     * @throws IOException 发生 I/O 错误时抛出。
     */
    private static String readResource(Resource resource) throws IOException {
        try (InputStream is = resource.getInputStream()) {
            return new String(is.readAllBytes(), StandardCharsets.UTF_8);
        }
    }
}
