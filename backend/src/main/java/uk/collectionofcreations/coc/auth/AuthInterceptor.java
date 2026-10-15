package uk.collectionofcreations.coc.auth;

import com.fasterxml.jackson.databind.ObjectMapper;
import io.jsonwebtoken.Claims;
import jakarta.servlet.http.HttpServletRequest;
import jakarta.servlet.http.HttpServletResponse;
import org.springframework.stereotype.Component;
import org.springframework.util.AntPathMatcher;
import org.springframework.web.servlet.HandlerInterceptor;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.mapper.UserMapper;

import java.nio.charset.StandardCharsets;
import java.util.List;
import java.util.Map;

/**
 * 认证 / 鉴权拦截器。
 * <p>1) 任何请求都尝试解析 token（没有就是匿名访客，不写库、无记录）；
 * 2) token 有效时从库里取最新用户信息（校验 token_version、状态）；
 * 3) 按「路径 + 方法」白名单强制登录（访客 401）与管理员校验（非管理员 403）。
 */
@Component
public class AuthInterceptor implements HandlerInterceptor {

    /** 必须登录（METHOD:ANT 模式） */
    private static final List<String> LOGIN_REQUIRED = List.of(
            "POST:/api/v1/auth/change-password",
            "GET:/api/v1/auth/me",
            "POST:/api/v1/comments",
            "POST:/api/v1/comments/*/like",
            "POST:/api/v1/projects",
            "PUT:/api/v1/projects/*",
            "POST:/api/v1/projects/*/submit",
            "POST:/api/v1/projects/*/like",
            "POST:/api/v1/projects/*/favorite",
            "POST:/api/v1/projects/*/build/retry",
            "POST:/api/v1/users/*/follow",
            "GET:/api/v1/reports/**",
            "POST:/api/v1/reports/**",
            "GET:/api/v1/uploads/**",
            "POST:/api/v1/uploads/**",
            "GET:/api/v1/files/**",
            "GET:/api/v1/im/**",
            "POST:/api/v1/im/**",
            "GET:/api/v1/users/me",
            "GET:/api/v1/users/me/**"
    );

    private static final AntPathMatcher MATCHER = new AntPathMatcher();

    private final JwtUtil jwtUtil;
    private final UserMapper userMapper;
    private final ObjectMapper objectMapper;

    public AuthInterceptor(JwtUtil jwtUtil, UserMapper userMapper, ObjectMapper objectMapper) {
        this.jwtUtil = jwtUtil;
        this.userMapper = userMapper;
        this.objectMapper = objectMapper;
    }

    @Override
    public boolean preHandle(HttpServletRequest request, HttpServletResponse response, Object handler) throws Exception {
        if ("OPTIONS".equalsIgnoreCase(request.getMethod())) {
            return true;
        }
        String path = request.getRequestURI();
        String method = request.getMethod();

        // ---- 1. 解析 token（可选） ----
        String token = extractToken(request);
        if (token != null) {
            Claims claims = jwtUtil.parse(token);
            if (claims != null) {
                long uid = Long.parseLong(claims.getSubject());
                int tv = claims.get("tv") == null ? -1 : ((Number) claims.get("tv")).intValue();
                Map<String, Object> user = userMapper.findAuthById(uid);
                if (user != null) {
                    int realTv = ((Number) user.get("token_version")).intValue();
                    int status = ((Number) user.get("status")).intValue();
                    if (realTv == tv && status != 3 && status != 2) {
                        AuthContext.set(user);
                    }
                }
            }
        }

        // ---- 2. 管理端：必须管理员 ----
        if (MATCHER.match("/api/v1/admin/**", path)) {
            if (!AuthContext.isLoggedIn()) {
                return reject(response, 401, "请先登录");
            }
            if (!AuthContext.isAdmin()) {
                return reject(response, 403, "需要管理员权限");
            }
            return true;
        }

        // ---- 3. 需要登录的普通接口 ----
        String key = method + ":" + path;
        for (String pattern : LOGIN_REQUIRED) {
            if (MATCHER.match(pattern, key)) {
                if (!AuthContext.isLoggedIn()) {
                    return reject(response, 401, "请先登录后再操作");
                }
                return true;
            }
        }
        return true;
    }

    @Override
    public void afterCompletion(HttpServletRequest request, HttpServletResponse response, Object handler, Exception ex) {
        AuthContext.clear();
    }

    private String extractToken(HttpServletRequest request) {
        String auth = request.getHeader("Authorization");
        if (auth != null && auth.startsWith("Bearer ")) {
            return auth.substring(7).trim();
        }
        String tk = request.getHeader("token");
        if (tk == null || tk.isBlank()) {
            tk = request.getParameter("token");
        }
        return (tk == null || tk.isBlank()) ? null : tk.trim();
    }

    private boolean reject(HttpServletResponse response, int httpStatus, String message) throws Exception {
        response.setStatus(httpStatus);
        response.setCharacterEncoding(StandardCharsets.UTF_8.name());
        response.setContentType("application/json;charset=UTF-8");
        response.getWriter().write(objectMapper.writeValueAsString(ApiResponse.error(httpStatus, message)));
        return false;
    }
}
