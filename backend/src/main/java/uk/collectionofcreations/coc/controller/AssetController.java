package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.DeleteMapping;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.PutMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.service.AssetService;

import java.util.Map;

/**
 * 素材库：替代原 MC 结构板块。
 * <p>上传走「/assets/upload/init → /uploads/{id}/parts → /assets/upload/{id}/complete」，
 * 与项目源码共用分片协议；直链由 nginx 的 {@code /media/} 直接静态输出。
 */
@RestController
@RequestMapping("/api/v1/assets")
public class AssetController {

    private final AssetService assetService;

    public AssetController(AssetService assetService) {
        this.assetService = assetService;
    }

    /** 素材广场（访客可读，只出公开素材） */
    @GetMapping
    public ApiResponse<Map<String, Object>> list(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(assetService.listPublic(query));
    }

    /** 我的素材（含未公开） */
    @GetMapping("/mine")
    public ApiResponse<Map<String, Object>> mine(@RequestParam Map<String, Object> query) {
        return ApiResponse.ok(assetService.listMine(query));
    }

    /** 素材详情（未公开的只有作者/管理员可见） */
    @GetMapping("/{asset_id}")
    public ApiResponse<Map<String, Object>> detail(@PathVariable("asset_id") long assetId) {
        return ApiResponse.ok(assetService.detail(assetId));
    }

    /** 初始化素材分片上传 */
    @PostMapping("/upload/init")
    public ApiResponse<Map<String, Object>> init(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(assetService.initUpload(body));
    }

    /** 完成素材上传：合并分片 → 落盘 assets/{id}/ → 登记 */
    @PostMapping("/upload/{upload_session_id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable("upload_session_id") long sessionId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(assetService.completeUpload(sessionId,
                body == null ? new java.util.LinkedHashMap<>() : body));
    }

    /** 改名 / 换系列 / 改公开性 */
    @PutMapping("/{asset_id}")
    public ApiResponse<Map<String, Object>> update(@PathVariable("asset_id") long assetId,
                                                   @RequestBody Map<String, Object> body) {
        return ApiResponse.ok(assetService.update(assetId, body));
    }

    /** 删除（软删，磁盘文件保留，避免打断已引用它的 HTML） */
    @DeleteMapping("/{asset_id}")
    public ApiResponse<Map<String, Object>> delete(@PathVariable("asset_id") long assetId) {
        return ApiResponse.ok(assetService.delete(assetId));
    }
}
