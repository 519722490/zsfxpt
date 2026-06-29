//package com.cyx.zsfxpt.config.es;
//
//import co.elastic.clients.elasticsearch.ElasticsearchClient;
//import co.elastic.clients.json.jackson.JacksonJsonpMapper;
//import co.elastic.clients.transport.rest_client.RestClientTransport;
//import lombok.RequiredArgsConstructor;
//import org.apache.http.auth.AuthScope;
//import org.apache.http.auth.UsernamePasswordCredentials;
//import org.apache.http.impl.client.BasicCredentialsProvider;
//import org.elasticsearch.client.RestClient;
//import org.elasticsearch.client.RestClientBuilder;
//import org.springframework.boot.context.properties.EnableConfigurationProperties;
//import org.springframework.context.annotation.Bean;
//import org.springframework.context.annotation.Configuration;
//import org.springframework.util.StringUtils;
//
//@Configuration//Spring 启动时会扫描这个类，看看里面有没有要创建的 Bean。
//@EnableConfigurationProperties(EsProperties.class)//启用那个配置类，让他真的去加载配置信息，然后注册为bean
//@RequiredArgsConstructor//这是Lombok的构造函数自动生成注解，所以底下的EsProperties不需要@Autowired，只能自动注入final修饰的
//public class ElasticsearchConfig {
//
//    private final EsProperties props;
//
//    @Bean
//    public ElasticsearchClient elasticsearchClient() {
//        //账号密码提供器，用来封装账号密码
//        BasicCredentialsProvider creds = new BasicCredentialsProvider();
//
//        if (StringUtils.hasText(props.getUsername())) {
//            creds.setCredentials(AuthScope.ANY,
//                    new UsernamePasswordCredentials(props.getUsername(), props.getPassword()));
//        }
//
//
//        RestClientBuilder builder = RestClient.builder(org.apache.http.HttpHost.create(props.getHost()))//字符串地址url转换成 HTTP 主机对象
//                .setHttpClientConfigCallback(httpClientBuilder -> httpClientBuilder//设置 HTTP 客户端的配置回调。大白话说：在真正创建 HTTP 客户端之前，给它加一些配置。
//                        .setDefaultCredentialsProvider(creds));//把前面创建好的账号密码提供器交给 HTTP 客户端。以后 HTTP 客户端请求 ES 时，就会自动带上认证信息。
//
//        RestClient restClient = builder.build();
//        RestClientTransport transport = new RestClientTransport(restClient, new JacksonJsonpMapper());//1. 用 restClient 负责发送 HTTP 请求2. 用 JacksonJsonpMapper 负责 JSON 序列化和反序列化
//
//        return new ElasticsearchClient(transport);//返回一个ES 客户端，注册为bean，以后直接用它连接es
//    }
//}
