package com.atguigu.gulimall.ssoserver.controller;

import org.springframework.data.redis.core.StringRedisTemplate;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.*;

import javax.annotation.Resource;
import javax.servlet.http.Cookie;
import javax.servlet.http.HttpServletResponse;
import java.util.UUID;

@Controller
public class LoginController {

    @Resource
    private StringRedisTemplate stringRedisTemplate;

    @GetMapping("/userInfo")
    @ResponseBody
    public String userInfo(@RequestParam("token") String token) {
        String s = stringRedisTemplate.opsForValue().get(token);
        return s;
    }

    @GetMapping("/login.html")
    public String loginPage(@RequestParam("redirect_url")String url, Model model,
                            @CookieValue(value = "sso_token",required = false) String sso_token) {
        if(!StringUtils.isEmpty(sso_token)) {
            //说明之前有人登录过，浏览器留下了痕迹
            return "redirect:" + url + "?token=" + sso_token;
        }
        model.addAttribute("url",url);
        return "login";
    }

    @PostMapping("/doLogin")
    public String doLogin(@RequestParam("username") String username, @RequestParam("password") String password,
                          @RequestParam("url") String url, Model model,
                          HttpServletResponse response) {
        //登录成功跳转
        if(!StringUtils.isEmpty(username) && !StringUtils.isEmpty(password)) {
            //登录成功
            String uuid= UUID.randomUUID().toString().replace("-","");
            stringRedisTemplate.opsForValue().set(uuid,username);
            Cookie sso_token = new Cookie("sso_token", uuid);
            response.addCookie(sso_token);
            return "redirect:" + url + "?token=" + uuid;
        }
        model.addAttribute("url",url);
        return "login";
    }

}
