package uk.collectionofcreations.coc.service;

import org.springframework.core.io.FileSystemResource;
import org.springframework.core.io.Resource;
import org.springframework.stereotype.Service;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.mapper.LogMapper;
import uk.collectionofcreations.coc.mapper.ProjectMapper;
import uk.collectionofcreations.coc.storage.StorageService;

import java.nio.file.Files;
import java.nio.file.Path;
import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 文件下载（仅登录）与项目运行资源（公开沙箱读取）。
 */
@Service
public class FileService {

    private final ProjectMapper projectMapper;
    private final StorageService storageService;
    private final LogMapper logMapper;

    public FileService(ProjectMapper projectMapper, StorageService storageService, LogMapper logMapper) {
        this.projectMapper = projectMapper;
        this.storageService = storageService;
        this.logMapper = logMapper;
    }

    /** 下载源码：必须登录（访客 401/403），并写 operation_log 审计 */
    public Map<String, Object> download(long projectFileId, String ip) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> file = projectMapper.findFileById(projectFileId);
        if (file == null) {
            throw BizException.notFound("文件不存在");
        }
        Path path = storageService.resolveStored(String.valueOf(file.get("storage_path")));
        if (path == null || !Files.exists(path)) {
            throw BizException.notFound("文件已不存在于服务器");
        }
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("admin_id", uid);
        log.put("action", "download_source");
        log.put("target_type", "project_file");
        log.put("target_id", projectFileId);
        log.put("detail", "下载源码：" + file.get("file_name"));
        log.put("ip", ip);
        logMapper.insertLog(log);

        Resource resource = new FileSystemResource(path);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("resource", resource);
        data.put("file_name", file.get("file_name"));
        data.put("file_size", file.get("file_size"));
        data.put("project_file_id", projectFileId);
        return data;
    }

    /** 运行目录：优先 build（打包产物），否则 source */
    public Path resolveRunRoot(long projectId) {
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        if (version == null) {
            throw BizException.notFound("项目还没有上传文件");
        }
        int versionNo = ((Number) version.get("version_no")).intValue();
        Path base = storageService.getRoot().resolve("projects").resolve(String.valueOf(projectId)).resolve("v" + versionNo);
        Path build = base.resolve("build");
        if (Files.isDirectory(build) && containsIndex(build)) {
            return build;
        }
        Path source = base.resolve("source");
        if (Files.isDirectory(source)) {
            return source;
        }
        throw BizException.notFound("项目运行目录不存在");
    }

    private boolean containsIndex(Path dir) {
        return Files.exists(dir.resolve("index.html"));
    }
}
