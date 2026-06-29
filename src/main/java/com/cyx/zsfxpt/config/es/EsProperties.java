//package com.cyx.zsfxpt.config.es;
//
//import lombok.Data;
//import org.springframework.beans.factory.annotation.Value;
//import org.springframework.boot.context.properties.ConfigurationProperties;
//
//import java.util.List;
//
//@Data
//@ConfigurationProperties(prefix = "spring.elasticsearch")//表示这个类用来装配置文件中的信息，但是不会启用
//public class EsProperties {
//    private List<String> uris;    // 支持多个 ES 节点：spring.elasticsearch.uris
//
//    // 由于 配置文件里 xpack.security.enabled: false，所以不需要账号密码了
//    private String username;      // spring.elasticsearch.username（可选）
//    private String password;      // spring.elasticsearch.password（可选）
//
//    // RAG 索引名来自 Spring AI 的配置
//    @Value("${spring.ai.vectorstore.elasticsearch.index-name:}")
//    private String index;         // e.g. zhiguang-ai-index
//
//    // 兼容旧代码：返回第一个 URI 作为 host
//    public String getHost() {
//        return (uris == null || uris.isEmpty()) ? null : uris.getFirst();
//    }
//}
