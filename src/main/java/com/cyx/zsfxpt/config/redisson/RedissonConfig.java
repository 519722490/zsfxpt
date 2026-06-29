//package com.cyx.zsfxpt.config.redisson;
//
//import org.redisson.Redisson;
//import org.redisson.api.RedissonClient;
//import org.redisson.config.Config;
//import org.redisson.config.SingleServerConfig;
//import org.springframework.boot.autoconfigure.data.redis.RedisProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.beans.factory.annotation.Value;
//
//@Configuration
//public class RedissonConfig {
//    @Value("${counter.rebuild.lock.watchdog-ms:30000}")
//    private long lockWatchdogMs;
//
//    @Bean
//    public RedissonClient redissonClient(RedisProperties redisProperties) {
//        Config config = new Config();
//        // 配置 Redisson 的锁看门狗超时，用于自动续约锁
//        config.setLockWatchdogTimeout(lockWatchdogMs);
//        String address = "redis://" + redisProperties.getHost() + ":" + redisProperties.getPort();//拼接redis的路径
//        SingleServerConfig single = config.useSingleServer().setAddress(address);//设置路径
//
//        if (redisProperties.getPassword() != null && !redisProperties.getPassword().isEmpty()) {
//            single.setPassword(redisProperties.getPassword());
//        }
//
//        // Spring Boot RedisProperties#getDatabase 返回的是原始 int（默认 0），无需判空
//        single.setDatabase(redisProperties.getDatabase());
//        return Redisson.create(config);//依旧是返回一个redisson客户端，注册为bean，可以随时使用
//    }
//}
//
