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
        return ApiResponse.ok(categoryMapper.listEnabled());
    }

    /** 当前生效的用户协议（供注册页展示，管理员需按版本号重新勾选） */
    @GetMapping("/agreements/current")
    public ApiResponse<Map<String, Object>> currentAgreement() {
        return ApiResponse.ok(passwordResetMapper.findCurrentAgreement());
    }
}
