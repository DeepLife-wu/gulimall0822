package com.atguigu.gulimall.auth.controller;

import com.alibaba.fastjson.JSON;
import com.alibaba.fastjson.TypeReference;
import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.utils.HttpUtils;
import com.atguigu.common.utils.R;
import com.atguigu.common.vo.MemberResponseVo;
import com.atguigu.gulimall.auth.feign.MemberFeignService;
import com.atguigu.gulimall.auth.vo.SocialUser;
import com.google.common.collect.Maps;
import lombok.extern.slf4j.Slf4j;
import org.apache.http.HttpResponse;
import org.apache.http.util.EntityUtils;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Controller;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import javax.servlet.http.HttpSession;
import java.util.Map;

/**
 * 处理社交登录请求
 */
@Controller
@Slf4j
public class OAuth2Controller {
    @Autowired
    private MemberFeignService memberFeignService;

    @GetMapping("/oauth2.0/weibo/success")
    public String weibo(@RequestParam("code")String code, HttpSession session, HttpServletResponse servletResponse, HttpServletRequest request) throws Exception {
        //1.根据code换取access_token
        Map<String,String> query = Maps.newHashMap();
        Map<String,String> header = Maps.newHashMap();

        Map<String,String> map = Maps.newHashMap();
        /*map.put("client_id","2636917288");
        map.put("client_secret","6a263e9284c6c1a74a62eadacc1lb6e2");*/
        map.put("client_id","42133247");
        map.put("client_secret","83b7fdfa2a79d7585afa055aeab089f3");
        map.put("grant_type","authorization_code");
        map.put("redirect_uri","http://auth.gulimall.com/oauth2.0/weibo/success");
        map.put("code",code);
        HttpResponse response = HttpUtils.doPost("https://api.weibo.com", "/oauth2/access_token", "post", header, query, map);
        //2.处理
        if(200 == response.getStatusLine().getStatusCode()) {
            //获取token
            String json = EntityUtils.toString(response.getEntity());
            SocialUser socialUser = JSON.parseObject(json, SocialUser.class);
            //知道当前是哪个社交用户
            //1).当前用户如果是第一次进网站，自动注册进来（为当前社交用户生成一个会员信息账号，以后这个社交账号就对应指定的会员）
            //登录或者注册
            R oauth2Login = memberFeignService.oauth2Login(socialUser);
            if(oauth2Login.getCode() == 0) {
                MemberResponseVo data = oauth2Login.getData("data", new TypeReference<MemberResponseVo>() {
                });
                log.info("登录成功,用户信息：" + data);
                //TODO:1。默认发的令牌。session=xxxx 作用域：当前域：（解决子域session共享问题）
                //TODO:2。使用JSON的序列化方式来序列化对象数据到redis中
                session.setAttribute(AuthServerConstant.LOGIN_USER,data);
//                new Cookie("JSESSIONID");
//                servletResponse.addCookie();
                return "redirect:http://gulimall.com";
            } else {
                return "redirect:http://auth.gulimall.com/login.html";
            }
        } else {
            return "redirect:http://auth.gulimall.com/login.html";
        }
    }

}
