package uk.collectionofcreations.coc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.CorsRegistry;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.collectionofcreations.coc.auth.AuthInterceptor;

import java.nio.file.Path;
import java.util.List;

/**
 * Web 配置：跨域（前端 8085 / 8086）+ 认证拦截器 + 封面/头像静态资源映射。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final List<String> allowedOrigins;
    private final String storageRoot;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${coc.cors.allowed-origins}") String allowedOrigins,
                     @Value("${coc.storage.root}") String storageRoot) {
        this.authInterceptor = authInterceptor;
        this.allowedOrigins = java.util.Arrays.stream(allowedOrigins.split(","))
                .map(String::trim)
                .filter(s -> !s.isEmpty())
                .toList();
        this.storageRoot = storageRoot;
    }

    @Override
    public void addInterceptors(InterceptorRegistry registry) {
        registry.addInterceptor(authInterceptor).addPathPatterns("/api/v1/**");
    }

    /**
     * 封面 / 头像直出：前端用 `:8090/covers/xxx.png`、`:8090/avatars/xxx.png` 取图。
     * 只读映射到存储根下对应目录；图不含隐私且要允许匿名/沙箱页面加载，故不挂鉴权。
     */
    @Override
    public void addResourceHandlers(ResourceHandlerRegistry registry) {
        registry.addResourceHandler("/covers/**").addResourceLocations(toFileLocation("covers"));
        registry.addResourceHandler("/avatars/**").addResourceLocations(toFileLocation("avatars"));
    }

    private String toFileLocation(String sub) {
        String base = storageRoot.replace('\\', '/');
        if (!base.endsWith("/")) {
            base = base + "/";
        }
        return Path.of(base + sub + "/").toUri().toString();
    }

    @Override
    public void addCorsMappings(CorsRegistry registry) {
        registry.addMapping("/**")
                .allowedOriginPatterns(allowedOrigins.toArray(new String[0]))
                .allowedMethods("GET", "POST", "PUT", "DELETE", "OPTIONS", "PATCH")
                .allowedHeaders("*")
                .exposedHeaders("Content-Disposition")
                .allowCredentials(true)
                .maxAge(3600);
    }
}
