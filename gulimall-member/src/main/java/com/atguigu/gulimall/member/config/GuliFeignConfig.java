package com.atguigu.gulimall.member.config;

import feign.RequestInterceptor;
import feign.RequestTemplate;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.context.request.RequestContextHolder;
import org.springframework.web.context.request.ServletRequestAttributes;

import javax.servlet.http.HttpServletRequest;

@Configuration
public class GuliFeignConfig {

    @Bean
    public RequestInterceptor requestInterceptor() {
        return new RequestInterceptor() {
            @Override
            public void apply(RequestTemplate template) {
                ServletRequestAttributes requestAttributes = (ServletRequestAttributes)RequestContextHolder.getRequestAttributes();
                if(requestAttributes != null) {
                    HttpServletRequest request = requestAttributes.getRequest();
                    if(request != null) {
                        //同步请求头数据，主要是cookie
                        String cookie = request.getHeader("Cookie");
                        //将当前环境的cookie 设置到新的feign请求头里
                        template.header("Cookie",cookie);
                    }
                }
            }
        };
    }

}
