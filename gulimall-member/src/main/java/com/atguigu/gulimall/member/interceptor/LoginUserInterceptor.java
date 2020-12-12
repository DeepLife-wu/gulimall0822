package com.atguigu.gulimall.member.interceptor;

import com.atguigu.common.constant.AuthServerConstant;
import com.atguigu.common.vo.MemberResponseVo;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

@Component
public class LoginUserInterceptor implements HandlerInterceptor {

    public static ThreadLocal<MemberResponseVo> loginUser = new ThreadLocal<>();

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // /order/order/status/23423489723492734
        String uri = request.getRequestURI();
        //内部feign服务调用无需登录
        boolean match = new AntPathMatcher().match("/member/**", uri);
        if(match) {
            return true;
        }

        MemberResponseVo attribute = (MemberResponseVo)request.getSession().getAttribute(AuthServerConstant.LOGIN_USER);
        if(attribute != null) {
            loginUser.set(attribute);
            return true;
        } else {
            //没登录去登录
            request.getSession().setAttribute("msg","请先进行登录");
            response.sendRedirect("http://auth.gulimall.com/login.html");
            return false;
        }
    }
}
