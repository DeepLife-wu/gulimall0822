package com.atguigu.gulimall.thirdparty;

import com.aliyun.oss.OSSClient;
import lombok.ToString;
import org.junit.jupiter.api.Test;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.boot.test.context.SpringBootTest;

import java.io.FileInputStream;
import java.io.FileNotFoundException;
import java.io.InputStream;

@SpringBootTest
class GulimallThirdPartyApplicationTests {

    @Autowired
    OSSClient ossClient;

    @Test
    void contextLoads() throws FileNotFoundException {
        // 上传文件流。
        InputStream inputStream = new FileInputStream("e:/temp/timg.jpg");
        ossClient.putObject("gulimall-hello", "timg.jpg", inputStream);

        // 关闭OSSClient。
        ossClient.shutdown();
        System.out.println("上传完成");
    }

    @Test
    public void sendSms() {
        
    }

}
