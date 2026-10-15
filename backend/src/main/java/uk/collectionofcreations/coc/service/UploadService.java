package uk.collectionofcreations.coc.service;

import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.web.multipart.MultipartFile;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.client.PackerClient;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.ProjectMapper;
import uk.collectionofcreations.coc.mapper.UploadMapper;
import uk.collectionofcreations.coc.storage.StorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.Collections;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 分片上传：init（sha256 秒传）→ parts（乱序/续传）→ complete（校验 + 落盘 + 建 project_file）。
 * <p>scratch 类型在 complete 时触发 8091 打包服务；服务不可用时降级为 build_task status=0 待处理。
 */
@Service
public class UploadService {

    private static final Logger log = LoggerFactory.getLogger(UploadService.class);

    private final UploadMapper uploadMapper;
    private final ProjectMapper projectMapper;
    private final StorageService storageService;
    private final PackerClient packerClient;
    private final ObjectMapper objectMapper;
    private final int defaultChunkSize;

    public UploadService(UploadMapper uploadMapper, ProjectMapper projectMapper, StorageService storageService,
                         PackerClient packerClient, ObjectMapper objectMapper,
                         @Value("${coc.upload.default-chunk-size:8388608}") int defaultChunkSize) {
        this.uploadMapper = uploadMapper;
        this.projectMapper = projectMapper;
        this.storageService = storageService;
        this.packerClient = packerClient;
        this.objectMapper = objectMapper;
        this.defaultChunkSize = defaultChunkSize;
    }

    /** 初始化上传会话：sha256 命中已有源码文件则秒传 */
    @Transactional
    public Map<String, Object> init(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        String fileName = Params.requireStr(body, "file_name", "文件名");
        Long fileSize = Params.lng(body, "file_size");
        if (fileSize == null || fileSize < 0) {
            throw BizException.badRequest("file_size 不正确");
        }
        String sha256 = Params.str(body, "sha256");
        if (sha256 != null && !sha256.matches("[0-9a-fA-F]{64}")) {
            throw BizException.badRequest("sha256 格式不正确（64 位十六进制）");
        }
        Long projectId = Params.lng(body, "project_id");
        if (projectId != null) {
            requireAuthorProject(projectId, uid);
        }
        int chunkSize = Params.integer(body, "chunk_size", defaultChunkSize);
        if (chunkSize < 64 * 1024 || chunkSize > 96 * 1024 * 1024) {
            chunkSize = defaultChunkSize;
        }
        int totalChunks = fileSize == 0 ? 1 : (int) Math.ceil((double) fileSize / chunkSize);

        Map<String, Object> instantFile = sha256 == null ? null : projectMapper.findFileBySha256(sha256);
        Map<String, Object> session = new LinkedHashMap<>();
        session.put("user_id", uid);
        session.put("project_id", projectId);
        session.put("file_name", fileName);
        session.put("file_size", fileSize);
        session.put("sha256", sha256);
        session.put("chunk_size", chunkSize);
        session.put("total_chunks", totalChunks);
        session.put("uploaded_chunks", "[]");
        session.put("status", 0);
        session.put("storage_path", null);
        session.put("instant_ref_project_file_id", instantFile == null ? null : instantFile.get("project_file_id"));
        uploadMapper.insertSession(session);
        long sessionId = ((Number) session.get("upload_session_id")).longValue();

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upload_session_id", sessionId);
        data.put("chunk_size", chunkSize);
        data.put("total_chunks", totalChunks);
        data.put("uploaded_chunks", Collections.emptyList());
        data.put("instant", instantFile != null);
        data.put("project_file_id", instantFile == null ? null : instantFile.get("project_file_id"));
        if (instantFile != null) {
            data.put("message", "命中秒传（sha256 已存在同内容文件），可直接调用 complete 完成登记，无需上传分片");
        }
        return data;
    }

    /** 上传单个分片（支持乱序与断点续传） */
    @Transactional
    public Map<String, Object> savePart(long sessionId, int partNumber, MultipartFile chunk) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> session = requireSession(sessionId, uid);
        if (((Number) session.get("status")).intValue() != 0) {
            throw BizException.badRequest("该上传会话已结束");
        }
        int total = ((Number) session.get("total_chunks")).intValue();
        if (partNumber < 1 || partNumber > total) {
            throw BizException.badRequest("part_number 超出范围（1-" + total + "）");
        }
        if (chunk == null || chunk.isEmpty()) {
            throw BizException.badRequest("分片内容为空");
        }
        try {
            Path dir = storageService.stagingDir(sessionId);
            Path tmp = dir.resolve("part-" + partNumber + ".tmp");
            Path target = dir.resolve("part-" + partNumber);
            chunk.transferTo(tmp.toFile());
            Files.move(tmp, target, StandardCopyOption.REPLACE_EXISTING);
        } catch (IOException e) {
            throw new BizException(500, "分片保存失败: " + e.getMessage(), 500);
        }
        List<Integer> uploaded = parseChunks(session.get("uploaded_chunks"));
        if (!uploaded.contains(partNumber)) {
            uploaded.add(partNumber);
        }
        Collections.sort(uploaded);
        persistChunks(sessionId, uploaded);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upload_session_id", sessionId);
        data.put("uploaded_chunks", uploaded);
        data.put("total_chunks", total);
        return data;
    }

    /** 查询会话进度（断点续传用） */
    public Map<String, Object> sessionInfo(long sessionId) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> session = requireSession(sessionId, uid);
        List<Integer> uploaded = parseChunks(session.get("uploaded_chunks"));
        int total = ((Number) session.get("total_chunks")).intValue();
        List<Integer> missing = new ArrayList<>();
        for (int i = 1; i <= total; i++) {
            if (!uploaded.contains(i)) {
                missing.add(i);
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upload_session_id", sessionId);
        data.put("file_name", session.get("file_name"));
        data.put("file_size", session.get("file_size"));
        data.put("sha256", session.get("sha256"));
        data.put("chunk_size", session.get("chunk_size"));
        data.put("total_chunks", total);
        data.put("uploaded_chunks", uploaded);
        data.put("missing_chunks", missing);
        data.put("status", session.get("status"));
        data.put("instant", session.get("instant_ref_project_file_id") != null);
        return data;
    }

    /**
     * 完成上传：校验 sha256 → 落盘 source 目录 → 建 project_version / project_file。
     * <p>body 可带 project_id（推荐）/ file_type / changelog。带 project_id 才能登记到项目版本。
     */
    @Transactional
    public Map<String, Object> complete(long sessionId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> session = requireSession(sessionId, uid);
        if (((Number) session.get("status")).intValue() == 1) {
            throw BizException.badRequest("该上传会话已完成，无需重复提交");
        }
        String fileName = String.valueOf(session.get("file_name"));
        Long projectId = Params.lng(body, "project_id");
        if (projectId == null && session.get("project_id") != null) {
            projectId = ((Number) session.get("project_id")).longValue();
        }

        Path sourceFile;
        boolean instant = session.get("instant_ref_project_file_id") != null;
        if (instant) {
            Map<String, Object> ref = projectMapper.findFileById(((Number) session.get("instant_ref_project_file_id")).longValue());
            sourceFile = storageService.resolveStored(String.valueOf(ref.get("storage_path")));
            if (sourceFile == null || !Files.exists(sourceFile)) {
                throw BizException.notFound("秒传命中的文件已不存在，请重新上传");
            }
        } else {
            int total = ((Number) session.get("total_chunks")).intValue();
            List<Integer> uploaded = parseChunks(session.get("uploaded_chunks"));
            if (uploaded.size() < total) {
                throw new BizException(1003, "分片不完整（已收 " + uploaded.size() + "/" + total + "），请续传完成后再提交");
            }
            Path merged = storageService.tmpDir().resolve(sessionId + "_" + fileName);
            try {
                storageService.mergeParts(storageService.stagingDir(sessionId), total, merged);
            } catch (IOException e) {
                throw new BizException(500, "分片合并失败: " + e.getMessage(), 500);
            }
            String expected = (String) session.get("sha256");
            try {
                String actual = storageService.sha256(merged);
                if (expected != null && !expected.equalsIgnoreCase(actual)) {
                    Files.deleteIfExists(merged);
                    throw new BizException(1005, "文件校验失败（sha256 不一致）：期望 " + expected + "，实际 " + actual);
                }
            } catch (IOException e) {
                throw new BizException(500, "文件校验失败: " + e.getMessage(), 500);
            }
            sourceFile = merged;
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upload_session_id", sessionId);
        data.put("file_name", fileName);
        long srcSize = 0L;
        if (sourceFile != null && Files.exists(sourceFile)) {
            try {
                srcSize = Files.size(sourceFile);
            } catch (IOException ignored) {
                // 忽略
            }
        }
        data.put("file_size", srcSize);
        data.put("instant", instant);

        if (projectId == null) {
            // 未关联项目：仅完成会话，文件留在 uploads/tmp（前端需带 project_id 才能登记 project_file）
            Map<String, Object> done = new LinkedHashMap<>();
            done.put("upload_session_id", sessionId);
            done.put("status", 1);
            done.put("storage_path", sourceFile == null ? null : sourceFile.toAbsolutePath().toString());
            uploadMapper.updateSessionDone(done);
            data.put("project_file_id", null);
            data.put("storage_path", sourceFile == null ? null : sourceFile.toAbsolutePath().toString());
            data.put("message", "上传完成，但未提供 project_id，未登记到项目版本（请在 complete 时带 project_id）");
            return data;
        }

        Map<String, Object> project = requireAuthorProject(projectId, uid);
        String projectType = String.valueOf(project.get("project_type"));
        int versionNo = projectMapper.maxVersionNo(projectId) + 1;
        Path sourceDir;
        try {
            sourceDir = storageService.projectVersionDir(projectId, versionNo, "source");
        } catch (IOException e) {
            throw new BizException(500, "创建存储目录失败: " + e.getMessage(), 500);
        }
        Path target = sourceDir.resolve(fileName);
        try {
            if (!target.toAbsolutePath().normalize().equals(sourceFile.toAbsolutePath().normalize())) {
                Files.createDirectories(sourceDir);
                Files.copy(sourceFile, target, StandardCopyOption.REPLACE_EXISTING);
                Files.deleteIfExists(sourceFile);
            }
        } catch (IOException e) {
            throw new BizException(500, "文件落盘失败: " + e.getMessage(), 500);
        }

        Map<String, Object> version = new LinkedHashMap<>();
        version.put("project_id", projectId);
        version.put("version_no", versionNo);
        version.put("changelog", Params.str(body, "changelog"));
        version.put("created_by", uid);
        projectMapper.insertVersion(version);
        long versionId = ((Number) version.get("project_version_id")).longValue();

        String fileType = Params.str(body, "file_type", "source");
        boolean scratch = "scratch".equals(projectType);
        long targetSize = 0L;
        try {
            targetSize = Files.exists(target) ? Files.size(target) : 0L;
        } catch (IOException ignored) {
            // 忽略
        }
        Map<String, Object> file = new LinkedHashMap<>();
        file.put("project_version_id", versionId);
        file.put("file_type", fileType);
        file.put("file_name", fileName);
        file.put("file_size", targetSize);
        file.put("sha256", session.get("sha256"));
        file.put("storage_path", target.toAbsolutePath().toString());
        file.put("scan_status", 1);
        file.put("build_status", scratch ? 1 : 0);
        file.put("build_path", null);
        projectMapper.insertFile(file);
        long projectFileId = ((Number) file.get("project_file_id")).longValue();

        // html 且是 zip：解压到 source 目录，便于沙箱直接运行
        if ("html".equals(projectType) && fileName.toLowerCase().endsWith(".zip")) {
            try {
                storageService.unzip(target, sourceDir);
                log.info("已解压 html 项目包: {} -> {}", target, sourceDir);
            } catch (Exception e) {
                log.warn("解压失败（不影响登记）: {}", e.getMessage());
            }
        }

        Map<String, Object> done = new LinkedHashMap<>();
        done.put("upload_session_id", sessionId);
        done.put("status", 1);
        done.put("storage_path", target.toAbsolutePath().toString());
        uploadMapper.updateSessionDone(done);

        data.put("project_file_id", projectFileId);
        data.put("project_version_id", versionId);
        data.put("version_no", versionNo);
        data.put("storage_path", target.toAbsolutePath().toString());

        if (scratch) {
            Long taskId = triggerBuild(projectId, versionId, projectFileId, target, versionNo, fileType);
            data.put("build_task_id", taskId);
        }
        return data;
    }

    /** 手动重新触发打包（作者或管理员） */
    @Transactional
    public Map<String, Object> retryBuild(long projectId) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        if (((Number) project.get("author_id")).longValue() != uid && !AuthContext.isAdmin()) {
            throw BizException.forbidden("仅作者或管理员可以重新打包");
        }
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        if (version == null) {
            throw BizException.badRequest("项目还没有上传文件");
        }
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("project_version_id", version.get("project_version_id"));
        q.put("file_type", "source");
        Map<String, Object> source = projectMapper.findFileByVersionAndType(q);
        if (source == null) {
            throw BizException.badRequest("未找到源文件");
        }
        if (!"scratch".equals(String.valueOf(project.get("project_type")))) {
            throw BizException.badRequest("只有 scratch 类型需要打包");
        }
        Path src = storageService.resolveStored(String.valueOf(source.get("storage_path")));
        Long taskId = triggerBuild(projectId, ((Number) version.get("project_version_id")).longValue(),
                ((Number) source.get("project_file_id")).longValue(), src,
                ((Number) version.get("version_no")).intValue(), "source");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("build_task_id", taskId);
        return data;
    }

    /** 触发 8091 打包；失败优雅降级：任务保持 status=0 待处理 */
    private Long triggerBuild(long projectId, long versionId, long projectFileId, Path sourceFile, int versionNo, String fileType) {
        Path buildDir;
        try {
            buildDir = storageService.projectVersionDir(projectId, versionNo, "build");
        } catch (IOException e) {
            throw new BizException(500, "创建构建目录失败: " + e.getMessage(), 500);
        }
        Map<String, Object> task = new LinkedHashMap<>();
        task.put("project_file_id", projectFileId);
        task.put("status", 0);
        task.put("log_path", buildDir.resolve("pack.log").toString());
        task.put("output_path", null);
        task.put("message", "待处理");
        task.put("finished_at", null);
        uploadMapper.insertBuildTask(task);
        long taskId = ((Number) task.get("build_task_id")).longValue();

        Map<String, Object> payload = new LinkedHashMap<>();
        payload.put("project_id", projectId);
        payload.put("project_version_id", versionId);
        payload.put("project_file_id", projectFileId);
        payload.put("file_name", sourceFile == null ? null : sourceFile.getFileName().toString());
        payload.put("source_path", sourceFile == null ? null : sourceFile.toAbsolutePath().toString());
        payload.put("output_dir", buildDir.toAbsolutePath().toString());
        payload.put("file_type", fileType);

        Map<String, Object> resp = packerClient.pack(payload);
        Map<String, Object> update = new LinkedHashMap<>();
        update.put("build_task_id", taskId);
        if (resp != null && (Boolean.TRUE.equals(resp.get("ok")) || "ok".equals(resp.get("status")))) {
            String outPath = resp.get("output_path") == null ? buildDir.toAbsolutePath().toString() : String.valueOf(resp.get("output_path"));
            update.put("status", 2);
            update.put("output_path", outPath);
            update.put("message", "打包成功");
            uploadMapper.updateBuildTask(update);
            Map<String, Object> fileUpdate = new LinkedHashMap<>();
            fileUpdate.put("project_file_id", projectFileId);
            fileUpdate.put("build_status", 2);
            fileUpdate.put("build_path", outPath);
            projectMapper.updateFileBuild(fileUpdate);
        } else {
            update.put("status", 0);
            update.put("output_path", null);
            update.put("message", "打包服务 8091 暂不可用，任务已排队待处理（可稍后调用 build/retry）");
            update.put("build_task_id", taskId);
            uploadMapper.updateBuildTask(update);
        }
        return taskId;
    }

    private Map<String, Object> requireSession(long sessionId, long uid) {
        Map<String, Object> session = uploadMapper.findSession(sessionId);
        if (session == null) {
            throw BizException.notFound("上传会话不存在");
        }
        if (((Number) session.get("user_id")).longValue() != uid) {
            throw BizException.forbidden("无权操作该上传会话");
        }
        return session;
    }

    private Map<String, Object> requireAuthorProject(long projectId, long uid) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        if (((Number) project.get("author_id")).longValue() != uid) {
            throw BizException.forbidden("只有作者本人可以上传文件");
        }
        return project;
    }

    @SuppressWarnings("unchecked")
    private List<Integer> parseChunks(Object json) {
        if (json == null) {
            return new ArrayList<>();
        }
        try {
            List<Object> raw = objectMapper.readValue(String.valueOf(json), new TypeReference<List<Object>>() {
            });
            List<Integer> list = new ArrayList<>();
            for (Object o : raw) {
                list.add(((Number) o).intValue());
            }
            return list;
        } catch (Exception e) {
            return new ArrayList<>();
        }
    }

    private void persistChunks(long sessionId, List<Integer> uploaded) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("upload_session_id", sessionId);
        try {
            p.put("uploaded_chunks", objectMapper.writeValueAsString(uploaded));
        } catch (Exception e) {
            p.put("uploaded_chunks", uploaded.toString());
        }
        uploadMapper.updateChunks(p);
    }
}
