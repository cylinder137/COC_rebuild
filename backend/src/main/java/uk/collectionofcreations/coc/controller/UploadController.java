package uk.collectionofcreations.coc.controller;

import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.PostMapping;
import org.springframework.web.bind.annotation.RequestBody;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RequestParam;
import org.springframework.web.bind.annotation.RestController;
import org.springframework.web.multipart.MultipartFile;
import uk.collectionofcreations.coc.common.ApiResponse;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.service.UploadService;

import java.util.Map;

/**
 * 分片上传：init（sha256 秒传）→ parts（乱序/续传）→ complete（校验落盘）。
 */
@RestController
@RequestMapping("/api/v1/uploads")
public class UploadController {

    private final UploadService uploadService;

    public UploadController(UploadService uploadService) {
        this.uploadService = uploadService;
    }

    /** 初始化上传会话 */
    @PostMapping("/init")
    public ApiResponse<Map<String, Object>> init(@RequestBody Map<String, Object> body) {
        return ApiResponse.ok(uploadService.init(body));
    }

    /** 上传分片（multipart：part_number + chunk），支持乱序与续传 */
    @PostMapping("/{upload_session_id}/parts")
    public ApiResponse<Map<String, Object>> parts(@PathVariable("upload_session_id") long sessionId,
                                                 @RequestParam(value = "part_number", required = false) String partNumber,
                                                 @RequestParam(value = "chunk", required = false) MultipartFile chunk) {
        if (partNumber == null) {
            throw BizException.badRequest("缺少 part_number");
        }
        int no;
        try {
            no = Integer.parseInt(partNumber.trim());
        } catch (NumberFormatException e) {
            throw BizException.badRequest("part_number 必须是数字");
        }
        return ApiResponse.ok(uploadService.savePart(sessionId, no, chunk));
    }

    /** 查询进度（断点续传） */
    @GetMapping("/{upload_session_id}")
    public ApiResponse<Map<String, Object>> info(@PathVariable("upload_session_id") long sessionId) {
        return ApiResponse.ok(uploadService.sessionInfo(sessionId));
    }

    /** 完成上传：校验 sha256 → 落盘 → 建 project_version / project_file */
    @PostMapping("/{upload_session_id}/complete")
    public ApiResponse<Map<String, Object>> complete(@PathVariable("upload_session_id") long sessionId,
                                                     @RequestBody(required = false) Map<String, Object> body) {
        return ApiResponse.ok(uploadService.complete(sessionId, body == null ? new java.util.LinkedHashMap<>() : body));
    }
}
