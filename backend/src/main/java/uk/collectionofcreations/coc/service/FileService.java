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
    /** 运行目录：优先 build（打包产物），否则 source。含发布 / 审核门禁（2026-10-17 修复 L-1） */
    public Path resolveRunRoot(long projectId) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        if (version == null) {
            throw BizException.notFound("项目还没有上传文件");
        }
        requireRunnable(project, version);
        int versionNo = ((Number) version.get("version_no")).intValue();
        Path base = storageService.getRoot().resolve("projects").resolve(String.valueOf(projectId)).resolve("v" + versionNo);
        Path build = base.resolve("build");
        if (Files.isDirectory(build) && resolveRunEntry(build) != null) {
            return build;
        }
        Path source = base.resolve("source");
        if (Files.isDirectory(source)) {
            return source;
        }
        throw BizException.notFound("项目运行目录不存在");
    }

    /**
     * 运行门禁：
     * <ul>
     *   <li>作者本人 / 管理员：任何状态都能预览（含未过审），便于自查；</li>
     *   <li>其他人：项目必须未删除且「已发布(2)」，并且<b>最新版本已过审</b>。</li>
     * </ul>
     * 「最新版本已过审」判定：该版本存在 approve 审核记录（按最新一条判定）；若无任何审核记录
     * （历史 / 种子数据），则以「已发布」兜底放行，避免误伤既有作品。
     */
    private void requireRunnable(Map<String, Object> project, Map<String, Object> version) {
        Long me = AuthContext.userId();
        long authorId = ((Number) project.get("author_id")).longValue();
        boolean owner = me != null && me.longValue() == authorId;
        boolean admin = AuthContext.isAdmin();
        if (owner || admin) {
            return;
        }
        int status = ((Number) project.get("status")).intValue();
        int deleted = ((Number) project.get("is_deleted")).intValue();
        if (deleted == 1 || status != 2) {
            throw BizException.notFound("项目不存在或尚未发布");
        }
        if (!versionApproved(version)) {
            throw BizException.notFound("该项目的新版本正在审核中，暂不可运行");
        }
    }

    private boolean versionApproved(Map<String, Object> version) {
        Object versionId = version.get("project_version_id");
        if (versionId == null) {
            return true;
        }
        java.util.List<Map<String, Object>> reviews = projectMapper.listReviews(((Number) versionId).longValue());
        if (reviews == null || reviews.isEmpty()) {
            return true; // 历史 / 种子数据：没有审核记录，按已发布放行
        }
        for (Map<String, Object> r : reviews) {
            String action = String.valueOf(r.get("action"));
            if ("approve".equalsIgnoreCase(action)) {
                return true;
            }
            if ("down".equalsIgnoreCase(action) || "reject".equalsIgnoreCase(action)) {
                return false;
            }
        }
        return false;
    }

    /**
     * 运行入口：优先 index.html，否则取目录下第一个 .html / .htm。
     * <p>用户上传的单文件 HTML 常常不叫 index.html（如「星球 - 副本.html」），
     * 而目录默认文档只认 index.html，所以在这里统一解析出入口名。
     *
     * @return 入口文件名（相对运行根目录）；没有 HTML 时返回 null
     */
    public String resolveRunEntry(Path dir) {
        if (dir == null || !Files.isDirectory(dir)) {
            return null;
        }
        Path index = dir.resolve("index.html");
        if (Files.exists(index) && !Files.isDirectory(index)) {
            return "index.html";
        }
        try (var stream = Files.list(dir)) {
            return stream.filter(Files::isRegularFile)
                    .map(p -> p.getFileName().toString())
                    .filter(n -> {
                        String lower = n.toLowerCase();
                        return lower.endsWith(".html") || lower.endsWith(".htm");
                    })
                    .sorted()
                    .findFirst()
                    .orElse(null);
        } catch (Exception e) {
            return null;
        }
    }
}
