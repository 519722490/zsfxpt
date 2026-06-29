//package com.cyx.zsfxpt.config.Cache;
//
//import com.github.benmanes.caffeine.cache.Caffeine;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//
//import java.time.Duration;
//
///**
// * Caffeine 本地缓存配置。
// *
// * <p>用于在应用进程内缓存分页结果，降低数据库与下游服务压力。</p>
// */
//@Configuration
//public class CacheConfig {
//    /**
//     * 公共信息流（广场/推荐）分页缓存。
//     *
//     * <p>键通常由分页游标、页大小、过滤条件等组合而成；值为一页的 {@link FeedPageResponse}。</p>
//     */
//    @Bean("feedPublicCache")
//    public Cache<String, FeedPageResponse> feedPublicCache(CacheProperties props) {
//        return Caffeine.newBuilder()
//                .maximumSize(props.getL2().getPublicCfg().getMaxSize())//读取公共信息的最大容量
//                .expireAfterWrite(Duration.ofSeconds(props.getL2().getPublicCfg().getTtlSeconds()))//读取公共信息的本地缓存存活时间
//                .build();
//    }
//
//    /**
//     * 我的信息流（个人主页/我的发布等）分页缓存。
//     *
//     * <p>键通常包含用户标识与分页参数；TTL 与容量由配置项控制。</p>
//     */
//    @Bean("feedMineCache")
//    public Cache<String, FeedPageResponse> feedMineCache(CacheProperties props) {
//        return Caffeine.newBuilder()
//                .maximumSize(props.getL2().getMineCfg().getMaxSize())//读取个人信息的最大容量
//                .expireAfterWrite(Duration.ofSeconds(props.getL2().getMineCfg().getTtlSeconds()))//读取个人信息的本地缓存存活时间
//                .build();
//    }
//}
