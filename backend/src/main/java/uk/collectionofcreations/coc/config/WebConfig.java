package uk.collectionofcreations.coc.config;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.context.annotation.Configuration;
import org.springframework.web.servlet.config.annotation.InterceptorRegistry;
import org.springframework.web.servlet.config.annotation.ResourceHandlerRegistry;
import org.springframework.web.servlet.config.annotation.WebMvcConfigurer;
import uk.collectionofcreations.coc.auth.AuthInterceptor;

import java.nio.file.Path;

/**
 * Web 配置：认证拦截器 + 封面/头像静态资源映射。
 *
 * 2026-10-25：CORS 已从本类移除，统一收敛到网关（nginx snippets/coc-cors.conf）。
 * 原因：两处都配会让同一个响应带两套 Access-Control-Allow-Origin，浏览器直接判 CORS 失败；
 *       且线上调白名单只需改网关并重载 nginx，不用重新打包 jar。
 * 副作用：绕过 nginx 直连 8090 时不再有任何 CORS 头，这是更严格的默认值，
 *       经网关的流量不受影响。
 */
@Configuration
public class WebConfig implements WebMvcConfigurer {

    private final AuthInterceptor authInterceptor;
    private final String storageRoot;

    public WebConfig(AuthInterceptor authInterceptor,
                     @Value("${coc.storage.root}") String storageRoot) {
        this.authInterceptor = authInterceptor;
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
}
