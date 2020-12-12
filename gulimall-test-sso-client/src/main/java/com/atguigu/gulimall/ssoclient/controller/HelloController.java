package com.atguigu.gulimall.ssoclient.controller;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.http.ResponseEntity;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.util.StringUtils;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.ResponseBody;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.client.RestTemplate;

import javax.servlet.http.HttpSession;
import java.util.ArrayList;
import java.util.List;

@Controller
public class HelloController {

    @Value("${sso.server.url}")
    String ssoServerUrl;

    /** 无需登录*/
    @GetMapping("/hello")
    @ResponseBody
    public String hello() {
        return "hello";
    }

    @GetMapping("/employees")
    public String employees(Model model, HttpSession session,
                            @RequestParam(value = "token",required = false) String token) {

        if(!StringUtils.isEmpty(token)) {
            RestTemplate restTemplate = new RestTemplate();
            ResponseEntity<String> entity = restTemplate.getForEntity("http://ssoserver.com:8080/userInfo?token=" + token, String.class);
            String body = entity.getBody();
            session.setAttribute("loginUser",body);
        }

        Object loginUser = session.getAttribute("loginUser");
        if(loginUser == null) {
            //没登录去服务中心登录
            //跳转过去以后，使用url上的查询参数 标识我们自己
            return "redirect:" + ssoServerUrl + "?redirect_url=http://client1.com:8081/employees";
        } else {
            List<String> emps = new ArrayList<>();
            emps.add("张三");
            emps.add("李四");
            model.addAttribute("emps",emps);
            return "list";
        }
    }

}
