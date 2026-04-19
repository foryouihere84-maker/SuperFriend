package com.superfriend.superfriend.config;

import lombok.extern.slf4j.Slf4j;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.ViewControllerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

import javax.servlet.http.HttpServletRequest;
import javax.servlet.http.HttpServletResponse;
import org.springframework.web.servlet.HandlerInterceptor;
import org.springframework.web.servlet.ModelAndView;

@Slf4j
@Configuration
public class WebConfig implements WebMvcConfigurer {

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(new LoggingInterceptor())
                .addPathPatterns("/**")
                .excludePathPatterns("/api/health", "/error", "/actuator/**", "/favicon.ico");
    }

    /**
     * 配置静态资源处理器
     * 将 ui/dist 目录下的静态文件映射到根路径
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        // 映射静态资源（CSS, JS, images 等）
        registry.addResourceHandler("/**")
                .addResourceLocations("classpath:/static/")
                .addResourceLocations("file:ui/dist/")
                .resourceChain(true);
    }

    /**
     * 配置视图控制器，处理前端路由
     * 将所有非 API 请求重定向到 index.html，让 Vue Router 处理路由
     */
    @Override
    public void addViewControllers(ViewControllerRegistry registry) {
        // 根路径重定向到 chat（默认首页）
        registry.addRedirectViewController("/", "/chat");
        
        // 其他所有非 API 路径都转发到 index.html
        // 这样 Vue Router 可以正确处理 /login, /chat, /mcp 等路由
        registry.addViewController("/{path:[^\\\\.]*}")
                .setViewName("forward:/index.html");
        
        registry.addViewController("/{paths:[^\\\\.]*}/**")
                .setViewName("forward:/index.html");
    }

    public static class LoggingInterceptor implements HandlerInterceptor {
        @Override
        public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) {
            return true;
        }

        @Override
        public void postHandle(HttpServletRequest request, HttpServletResponse response, Object handler, 
                                ModelAndView modelAndView) {
        }

        @Override
        public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
            if (ex != null) {
                log.error("请求处理异常：{} {}", request.getRequestURI(), ex.getMessage());
            }
        }
    }
}
