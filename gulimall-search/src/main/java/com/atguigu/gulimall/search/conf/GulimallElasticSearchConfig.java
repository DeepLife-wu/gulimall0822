package com.atguigu.gulimall.search.conf;

import org.apache.http.HttpHost;
import org.elasticsearch.client.*;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

/**
 * 1.导入依赖
 * 2.编写配置
 */
@Configuration
public class GulimallElasticSearchConfig {

    public static final RequestOptions COMMON_OPTIONS;
    static {
        RequestOptions.Builder builder = RequestOptions.DEFAULT.toBuilder();
//        builder.addHeader("Authorization", "Bearer " + TOKEN);
//        builder.setHttpAsyncResponseConsumerFactory(
//                new HttpAsyncResponseConsumerFactory
//                        .HeapBufferedResponseConsumerFactory(30 * 1024 * 1024 * 1024));
        COMMON_OPTIONS = builder.build();
    }

    @Bean
    public RestHighLevelClient esRestClient() {
        /*,RestHighLevelClient client = new RestHighLevelClient(RestClient.builder(
                new HttpHost("192.168.56.10", 9200, "http")
                new HttpHost("localhost", 9201, "9201")));*/
        RestClientBuilder builder = RestClient.builder(new HttpHost("192.168.56.10",9200,"http"));
        RestHighLevelClient client = new RestHighLevelClient(builder);
        return client;
    }

}
