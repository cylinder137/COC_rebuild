package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.service.AuthService;
import uk.collectionofcreations.coc.service.SystemService;

import java.util.Map;

/**
 * 认证 + 系统：健康检查、开发者申请、登录、改密、找回申请。
 */
@RestController
@RequestMapping("/api/v1")
public class AuthController {

    private final AuthService authService;
    private final SystemService systemService;

    public AuthController(AuthService authService, SystemService systemService) {
        this.authService = authService;
        this.systemService = systemService;
    }

    @GetMapping("/system/health")
    public ApiResponse<Map<String, Object>> health() {
        return ApiResponse.ok(systemService.health());
    }

    /** 开发者申请（无需登录） */
    @PostMapping("/auth/dev-apply")
    public ApiResponse<Map<String, Object>> devApply(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.devApply(body));
    }

    /** 登录（仅管理员审核通过的开发者可登录） */
    @PostMapping("/auth/login")
    public ApiResponse<Map<String, Object>> login(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.login(body));
    }

    /** 当前登录用户 */
    @GetMapping("/auth/me")
    public ApiResponse<Map<String, Object>> me() {
        return ApiResponse.ok(authService.me());
    }

    /** 修改密码（临时密码首登强制改），返回新 token */
    @PostMapping("/auth/change-password")
    public ApiResponse<Map<String, Object>> changePassword(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.changePassword(body));
    }

    /** 忘记密码 → 提交找回申请 */
    @PostMapping("/auth/password/forgot")
    public ApiResponse<Map<String, Object>> forgot(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(authService.forgotPassword(body));
    }

    /** 退出登录（JWT 无状态，仅前端清 token；此处提供占位接口） */
    @PostMapping("/auth/logout")
    public ApiResponse<Map<String, Object>> logout() {
        return ApiResponse.ok();
    }
}
