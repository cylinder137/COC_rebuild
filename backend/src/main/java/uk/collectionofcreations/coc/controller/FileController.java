package uk.collectionofcreations.coc.controller;

import jakarta.servlet.http.HttpServletRequest;
import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.http.HttpHeaders;
import org.springframework.http.MediaType;
import org.springframework.http.ResponseEntity;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.PathVariable;
import org.springframework.web.bind.annotation.RequestMapping;
import org.springframework.web.bind.annotation.RestController;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.service.FileService;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.util.Map;

/**
 * 文件：下载（仅登录 + 审计）与项目运行资源（公开只读，供沙箱 iframe）。
 */
@RestController
@RequestMapping("/api/v1")
public class FileController {

    private final FileService fileService;

    public FileController(FileService fileService) {
        this.fileService = fileService;
    }

    /** 下载源码：仅登录（访客 401），写 operation_log 审计 */
    @GetMapping("/files/{project_file_id}/content")
    public ResponseEntity<Resource> download(@PathVariable("project_file_id") long projectFileId,
                                             HttpServletRequest request) {
        Map<String, Object> data = fileService.download(projectFileId, clientIp(request));
        Resource resource = (Resource) data.get("resource");
        String fileName = String.valueOf(data.get("file_name"));
        long size = 0L;
        try {
            size = resource.contentLength();
        } catch (Exception ignored) {
            // 长度取不到就不带 Content-Length
        }
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_DISPOSITION,
                        "attachment; filename*=UTF-8''" + URLEncoder.encode(fileName, StandardCharsets.UTF_8))
                .header(HttpHeaders.CONTENT_TYPE, MediaType.APPLICATION_OCTET_STREAM_VALUE)
                .contentLength(size)
                .body(resource);
    }

    /**
     * 沙箱运行：公开只读，直接返回项目 source / build 目录下的静态资源。
     * <p>例：GET /api/v1/run/12/（默认 index.html）、/api/v1/run/12/asset/a.png
     */
    @GetMapping({"/run/{project_id}", "/run/{project_id}/", "/run/{project_id}/**"})
    public ResponseEntity<Resource> run(@PathVariable("project_id") long projectId, HttpServletRequest request) {
        Path root = fileService.resolveRunRoot(projectId);
        String uri = request.getRequestURI();
        String marker = "/run/" + projectId;
        int idx = uri.indexOf(marker);
        String rest = idx < 0 ? "" : uri.substring(idx + marker.length());
        while (rest.startsWith("/")) {
            rest = rest.substring(1);
        }
        if (rest.isEmpty()) {
            // 目录默认文档：优先 index.html，否则目录下第一个 html（兼容用户上传的任意文件名）
            String entry = fileService.resolveRunEntry(root);
            if (entry == null) {
                throw BizException.notFound("该项目暂无可在线运行的 HTML 入口");
            }
            rest = entry;
        }
        // 防盗链目录穿越
        if (rest.contains("..")) {
            throw BizException.badRequest("非法路径");
        }
        Path target = root.resolve(rest).normalize();
        if (!target.startsWith(root)) {
            throw BizException.forbidden("非法路径");
        }
        if (Files.isDirectory(target)) {
            String entry = fileService.resolveRunEntry(target);
            if (entry == null) {
                throw BizException.notFound("运行资源不存在: " + rest);
            }
            target = target.resolve(entry).normalize();
        }
        if (!Files.exists(target) || Files.isDirectory(target)) {
            throw BizException.notFound("运行资源不存在: " + rest);
        }
        Resource resource = new FileSystemResource(target);
        String contentType = guessContentType(target);
        return ResponseEntity.ok()
                .header(HttpHeaders.CONTENT_TYPE, contentType)
                .header("X-Content-Type-Options", "nosniff")
                .body(resource);
    }

    private String guessContentType(Path path) {
        try {
            String probed = Files.probeContentType(path);
            if (probed != null) {
                return probed;
            }
        } catch (Exception ignored) {
            // 回退
        }
        String name = path.getFileName().toString().toLowerCase();
        if (name.endsWith(".html") || name.endsWith(".htm")) {
            return "text/html;charset=UTF-8";
        }
        if (name.endsWith(".js") || name.endsWith(".mjs")) {
            return "application/javascript;charset=UTF-8";
        }
        if (name.endsWith(".css")) {
            return "text/css;charset=UTF-8";
        }
        if (name.endsWith(".json")) {
            return "application/json;charset=UTF-8";
        }
        if (name.endsWith(".svg")) {
            return "image/svg+xml";
        }
        if (name.endsWith(".wasm")) {
            return "application/wasm";
        }
        return MediaType.APPLICATION_OCTET_STREAM_VALUE;
    }

    static String clientIp(HttpServletRequest request) {
        String xff = request.getHeader("X-Forwarded-For");
        if (xff != null && !xff.isBlank()) {
            int comma = xff.indexOf(',');
            return (comma > 0 ? xff.substring(0, comma) : xff).trim();
        }
        return request.getRemoteAddr();
    }
}
