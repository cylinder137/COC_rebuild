package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.mapper.CategoryMapper;
import uk.collectionofcreations.coc.mapper.PasswordResetMapper;

import java.util.List;
import java.util.Map;

/** 分类 / 协议版本（公共只读） */
@RestController
@RequestMapping("/api/v1")
public class MetaController {

    private final CategoryMapper categoryMapper;
    private final PasswordResetMapper passwordResetMapper;

    public MetaController(CategoryMapper categoryMapper, PasswordResetMapper passwordResetMapper) {
        this.categoryMapper = categoryMapper;
        this.passwordResetMapper = passwordResetMapper;
    }

    @GetMapping("/categories")
    public ApiResponse<List<Map<String, Object>>> categories() {
        // 分类表几乎不变：内存缓存 5 分钟，避免每次首页加载都查库
        Object cached = uk.collectionofcreations.coc.service.HotCache.get("meta:categories");
        if (cached instanceof List) {
            @SuppressWarnings("unchecked")
            List<Map<String, Object>> hit = (List<Map<String, Object>>) cached;
            return ApiResponse.ok(hit);
        }
        List<Map<String, Object>> list = categoryMapper.listEnabled();
        uk.collectionofcreations.coc.service.HotCache.put("meta:categories", list, 300_000L);
        return ApiResponse.ok(list);
    }

    /** 当前生效的用户协议（供注册页展示，管理员需按版本号重新勾选） */
    @GetMapping("/agreements/current")
    public ApiResponse<Map<String, Object>> currentAgreement() {
        Object cached = uk.collectionofcreations.coc.service.HotCache.get("meta:agreement");
        if (cached instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> hit = (Map<String, Object>) cached;
            return ApiResponse.ok(hit);
        }
        Map<String, Object> data = passwordResetMapper.findCurrentAgreement();
        if (data != null) {
            uk.collectionofcreations.coc.service.HotCache.put("meta:agreement", data, 300_000L);
        }
        return ApiResponse.ok(data);
    }
}
