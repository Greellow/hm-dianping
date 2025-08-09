package com.hmdp.utils;

import org.springframework.web.servlet.HandlerInterceptor;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;

public class LoginInterceptor implements HandlerInterceptor {

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        // 1. 直接判断 ThreadLocal 中是否有用户
        if (UserHolder.getUser() == null) {
            // 没有，说明用户未登录，需要拦截
            response.setStatus(401); // 设置 401 未授权状态码
            // 拦截
            return false;
        }
        // 有用户，则放行
        return true;
    }
}