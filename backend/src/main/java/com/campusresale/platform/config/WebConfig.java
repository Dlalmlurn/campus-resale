// 文件功能：配置 API CORS、CSRF 拦截器和注解式权限拦截器。
package com.campusresale.platform.config;

import com.campusresale.platform.security.AuthorizationInterceptor;
import com.campusresale.platform.security.OriginCsrfInterceptor;
import java.util.List;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;

@Configuration
public class WebConfig implements WebMvcConfigurer {

    /**
     * 项目配置对象：这里主要读取允许跨域访问 API 的前端来源。
     */
    private final CampusResaleProperties properties;

    /**
     * CSRF 来源校验拦截器：负责限制写请求只能来自允许的前端来源。
     */
    private final OriginCsrfInterceptor originCsrfInterceptor;

    /**
     * 权限拦截器：负责识别 @RequireLogin、@RequireRole 和 @RequireTradeEligible。
     */
    private final AuthorizationInterceptor authorizationInterceptor;

    /**
     * 构造 Web 配置，Spring 自动注入配置对象和两个拦截器。
     */
    public WebConfig(
            CampusResaleProperties properties,
            OriginCsrfInterceptor originCsrfInterceptor,
            AuthorizationInterceptor authorizationInterceptor
    ) {
        this.properties = properties;
        this.originCsrfInterceptor = originCsrfInterceptor;
        this.authorizationInterceptor = authorizationInterceptor;
    }

    /**
     * 配置浏览器跨域访问规则，允许前端携带 Cookie 调用后端 /api/**。
     */
    @Override
    public void addCorsMappings(CorsRegistry registry) {
        // allowedOrigins 来自 application.yml，默认包含本地 Vite 和前端容器端口。
        List<String> allowedOrigins = properties.cors().allowedOrigins();
        registry.addMapping("/api/**")
                .allowedOrigins(allowedOrigins.toArray(String[]::new))
                .allowedMethods("GET", "POST", "PUT", "PATCH", "DELETE", "OPTIONS")
                .allowedHeaders("*")
                .allowCredentials(true);
    }

    /**
     * 注册 MVC 拦截器；顺序很重要：先做 CSRF 来源校验，再做登录、角色和交易资格校验。
     */
    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(originCsrfInterceptor)
                .addPathPatterns("/api/**");
        registry.addInterceptor(authorizationInterceptor)
                .addPathPatterns("/api/**");
    }
}
