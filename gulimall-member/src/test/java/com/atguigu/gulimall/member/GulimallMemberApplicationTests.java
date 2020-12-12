package com.atguigu.gulimall.member;

import org.apache.commons.codec.digest.DigestUtils;
import org.apache.commons.codec.digest.Md5Crypt;
import org.junit.Test;
import org.springframework.boot.test.context.SpringBootTest;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;

//@SpringBootTest
class GulimallMemberApplicationTests {

    @Test
    public void contextLoads() {
        //e10adc3949ba59abbe56e057f20f883e
        String s = DigestUtils.md5Hex("123456");
        System.out.println(s);

        //$1$T840IUrB$e/otTDzAftjiw.udSN8XW/
        String crypt = Md5Crypt.md5Crypt("123456".getBytes());
        System.out.println(crypt);

        BCryptPasswordEncoder passwordEncoder = new BCryptPasswordEncoder();
        //$2a$10$bX55oXSGJV2kHXn76khIsOC.jcRiolEvoDcIGG.Ie77pHShICIrSy
        String encode = passwordEncoder.encode("123456");
        System.out.println(encode);
    }



}
