package com.atguigu.gulimall.thirdparty.component;

import com.atguigu.gulimall.thirdparty.util.HttpUtils;
import com.google.common.collect.Maps;
import lombok.Data;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.boot.context.properties.ConfigurationProperties;
import org.springframework.stereotype.Component;

import java.util.Map;

@ConfigurationProperties(prefix = "spring.cloud.alicloud.sms")
@Data
@Component
public class SmsComponent {

    private String host;
    private String path;
    private String skin;
    private String sign;
    private String appcode;

    public void sendSmsCode(String phone,String code) {
        String method = "GET";
        Map<String,String> headers = Maps.newHashMap();
        headers.put("Authorization","APPCODE " + appcode);
        Map<String,String> query = Maps.newHashMap();
        query.put("code",code);
        query.put("phone",phone);
//        query.put("sign","175622");

        query.put("skin",skin);
        query.put("sign",sign);

        try {
            HttpResponse response = HttpUtils.doGet(host, path, method, headers, query);
            System.out.println(EntityUtils.toString(response.getEntity()));
        } catch (Exception e) {
            e.printStackTrace();
        }

    }
}







