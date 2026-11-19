package uk.collectionofcreations.coc.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.AssetMapper;
import uk.collectionofcreations.coc.mapper.UploadMapper;
import uk.collectionofcreations.coc.storage.StorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardCopyOption;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.LinkedHashSet;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 素材库：图片 / GIF / 视频 / 音频 / JSON。
 *
 * <p>上传复用项目源码那套分片协议（uploads/init → parts → complete），
 * 只是 complete 时落到 {@code assets/{assetId}/{fileName}}，并登记 asset 表。
 *
 * <p><b>直链</b>：{@code /media/{assetId}/{fileName}}，由 nginx 直接 alias 到存储目录，
 * 所以复制出来的链接是「纯静态直链」，可以直接写进用户自己的 HTML：
 * {@code <img src="https://站点/media/12/test.png">}。这也意味着链接一旦外泄，
 * 未公开素材同样能访问——「公开性」只控制是否出现在素材广场与他人浏览列表中。
 */
@Service
public class AssetService {

    private static final Logger log = LoggerFactory.getLogger(AssetService.class);

    /** 允许的扩展名 → 素材类型。白名单，从严：不允许 html/svg/js 等可执行/可注入内容 */
    private static final Map<String, String> EXT_TYPE = new LinkedHashMap<>();
    /** 扩展名 → MIME（避免依赖系统 mime 表，Windows 上常常探测不准） */
    private static final Map<String, String> EXT_MIME = new LinkedHashMap<>();

    static {
        add("png", "image", "image/png");
        add("jpg", "image", "image/jpeg");
        add("jpeg", "image", "image/jpeg");
        add("webp", "image", "image/webp");
        add("bmp", "image", "image/bmp");
        add("avif", "image", "image/avif");
        add("ico", "image", "image/x-icon");
        add("gif", "gif", "image/gif");
        add("mp4", "video", "video/mp4");
        add("webm", "video", "video/webm");
        add("mov", "video", "video/quicktime");
        add("m4v", "video", "video/x-m4v");
        add("mp3", "audio", "audio/mpeg");
        add("wav", "audio", "audio/wav");
        add("ogg", "audio", "audio/ogg");
        add("m4a", "audio", "audio/mp4");
        add("aac", "audio", "audio/aac");
        add("flac", "audio", "audio/flac");
        add("json", "json", "application/json");
    }

    private static void add(String ext, String type, String mime) {
        EXT_TYPE.put(ext, type);
        EXT_MIME.put(ext, mime);
    }

    /** 单素材上限：300MB（超过请用外链；Cloudflare 走的是分片，不受 100MB 单请求限制） */
    private static final long MAX_SIZE = 300L * 1024 * 1024;

    private final AssetMapper assetMapper;
    private final UploadMapper uploadMapper;
    private final UploadService uploadService;
    private final SeriesService seriesService;
    private final StorageService storageService;

    public AssetService(AssetMapper assetMapper, UploadMapper uploadMapper, UploadService uploadService,
                        SeriesService seriesService, StorageService storageService) {
        this.assetMapper = assetMapper;
        this.uploadMapper = uploadMapper;
        this.uploadService = uploadService;
        this.seriesService = seriesService;
        this.storageService = storageService;
    }

    /* ================= 上传 ================= */

    /**
     * 初始化素材上传会话（分片）。前端拿到 session_id 后走统一的 /uploads/{id}/parts，
     * 最后调 /assets/upload/{id}/complete 落盘并登记。
     */
    @Transactional
    public Map<String, Object> initUpload(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        String rawName = Params.requireStr(body, "file_name", "文件名");
        String fileName = sanitize(rawName);
        String ext = extOf(fileName);
        String type = EXT_TYPE.get(ext);
        if (type == null) {
            throw BizException.badRequest("不支持的素材格式 ." + ext
                    + "；可用：图片 png/jpg/jpeg/webp/bmp/avif/ico、动图 gif、视频 mp4/webm/mov/m4v、音频 mp3/wav/ogg/m4a/aac/flac、数据 json");
        }
        Long fileSize = Params.lng(body, "file_size");
        if (fileSize == null || fileSize <= 0) {
            throw BizException.badRequest("file_size 不正确");
        }
        if (fileSize > MAX_SIZE) {
            throw BizException.badRequest("单个素材不能超过 300MB");
        }
        String sha256 = Params.str(body, "sha256");
        int chunkSize = Params.integer(body, "chunk_size", 5 * 1024 * 1024);
        if (chunkSize < 64 * 1024 || chunkSize > 96 * 1024 * 1024) {
            chunkSize = 5 * 1024 * 1024;
        }
        int totalChunks = (int) Math.ceil((double) fileSize / chunkSize);

        Map<String, Object> session = new LinkedHashMap<>();
        session.put("user_id", uid);
        session.put("project_id", null);
        session.put("kind", "asset");
        session.put("file_name", fileName);
        session.put("file_size", fileSize);
        session.put("sha256", sha256);
        session.put("chunk_size", chunkSize);
        session.put("total_chunks", totalChunks);
        session.put("uploaded_chunks", "[]");
        session.put("status", 0);
        session.put("storage_path", null);
        session.put("instant_ref_project_file_id", null);
        uploadMapper.insertSession(session);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("upload_session_id", session.get("upload_session_id"));
        data.put("chunk_size", chunkSize);
        data.put("total_chunks", totalChunks);
        data.put("uploaded_chunks", new ArrayList<>());
        data.put("asset_type", type);
        data.put("file_name", fileName);
        return data;
    }

    /** 完成素材上传：合并分片 → 落到 assets/{assetId}/ → 登记 asset 行 */
    @Transactional
    public Map<String, Object> completeUpload(long sessionId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> session = uploadMapper.findSession(sessionId);
        if (session == null) {
            throw BizException.notFound("上传会话不存在");
        }
        if (((Number) session.get("user_id")).longValue() != uid) {
            throw BizException.forbidden("无权操作该上传会话");
        }
        if (!"asset".equals(String.valueOf(session.get("kind")))) {
            throw BizException.badRequest("该会话不是素材上传会话");
        }
        if (((Number) session.get("status")).intValue() == 1) {
            throw BizException.badRequest("该上传会话已完成，无需重复提交");
        }

        String fileName = sanitize(String.valueOf(session.get("file_name")));
        String ext = extOf(fileName);
        String type = EXT_TYPE.get(ext);
        if (type == null) {
            throw BizException.badRequest("不支持的素材格式 ." + ext);
        }

        // 系列：可以传 series_id，也可以传 new_series_name 让后端就近建一个
        Long seriesId = Params.lng(body, "series_id");
        String newSeriesName = Params.str(body, "new_series_name");
        if (seriesId == null && newSeriesName != null && !newSeriesName.isBlank()) {
            seriesId = seriesService.ensureByName(SeriesService.Kind.ASSET, uid, newSeriesName,
                    Params.bool(body, "is_public"));
        }
        if (seriesId != null) {
            seriesService.requireOwn(SeriesService.Kind.ASSET, seriesId, uid);
        }

        String displayName = Params.str(body, "name");
        if (displayName == null || displayName.isBlank()) {
            displayName = stripExt(fileName);
        }
        displayName = displayName.trim();
        if (displayName.length() > 120) {
            displayName = displayName.substring(0, 120);
        }
        boolean instant = session.get("instant_ref_project_file_id") != null;
        Path merged = uploadService.materialize(sessionId, session);
        long size = 0L;
        try {
            size = Files.size(merged);
        } catch (IOException ignored) {
            // 取不到大小按 0 处理，不影响登记
        }

        Map<String, Object> row = new LinkedHashMap<>();
        row.put("user_id", uid);
        row.put("series_id", seriesId);
        row.put("name", displayName);
        row.put("file_name", fileName);
        row.put("asset_type", type);
        row.put("mime", EXT_MIME.getOrDefault(ext, "application/octet-stream"));
        row.put("file_size", size);
        row.put("sha256", session.get("sha256"));
        row.put("storage_path", "");
        row.put("is_public", Params.bool(body, "is_public") ? 1 : 0);
        assetMapper.insert(row);
        long assetId = ((Number) row.get("asset_id")).longValue();

        Path dir;
        Path target;
        try {
            dir = storageService.assetDir(assetId);
            target = uniqueTarget(dir, fileName, assetId);
            if (!target.toAbsolutePath().normalize().equals(merged.toAbsolutePath().normalize())) {
                Files.createDirectories(dir);
                // 秒传命中的是别人/别的素材的正式文件，只能复制，不能移走
                if (instant) {
                    Files.copy(merged, target, StandardCopyOption.REPLACE_EXISTING);
                } else {
                    Files.move(merged, target, StandardCopyOption.REPLACE_EXISTING);
                }
            }
        } catch (IOException e) {
            throw new BizException(500, "素材落盘失败: " + e.getMessage(), 500);
        }

        String stored = "assets/" + assetId + "/" + target.getFileName();
        Map<String, Object> upd = new LinkedHashMap<>();
        upd.put("asset_id", assetId);
        upd.put("storage_path", stored);
        upd.put("file_size", size);
        upd.put("mime", EXT_MIME.getOrDefault(ext, "application/octet-stream"));
        assetMapper.updateStored(upd);

        Map<String, Object> done = new LinkedHashMap<>();
        done.put("upload_session_id", sessionId);
        done.put("status", 1);
        done.put("storage_path", target.toAbsolutePath().toString());
        uploadMapper.updateSessionDone(done);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("asset_id", assetId);
        data.put("name", displayName);
        data.put("file_name", target.getFileName().toString());
        data.put("asset_type", type);
        data.put("file_size", size);
        data.put("series_id", seriesId);
        data.put("is_public", row.get("is_public"));
        data.put("url", mediaUrl(assetId, target.getFileName().toString()));
        log.info("素材登记完成 asset_id={} user={} type={} size={}", assetId, uid, type, size);
        return data;
    }

    /* ================= 列表 / 详情 ================= */

    /** 素材广场：只出公开素材 */
    public Map<String, Object> listPublic(Map<String, Object> query) {
        int page = Params.page(query);
        int size = Params.size(query, 24, 60);
        Map<String, Object> p = new LinkedHashMap<>(query);
        p.put("limit", size);
        p.put("offset", (page - 1) * size);
        List<Map<String, Object>> list = assetMapper.listPublic(p);
        long total = assetMapper.countPublic(p);
        decorate(list);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 我的素材：含未公开 */
    public Map<String, Object> listMine(Map<String, Object> query) {
        long uid = AuthContext.requireUserId();
        int page = Params.page(query);
        int size = Params.size(query, 24, 60);
        Map<String, Object> p = new LinkedHashMap<>(query);
        p.put("user_id", uid);
        p.put("limit", size);
        p.put("offset", (page - 1) * size);
        // 前端可能传 1 / "1" / true，统一成 Boolean，否则 Mapper 里 no_series == true 判断不成立
        p.put("no_series", Params.bool(p, "no_series"));
        List<Map<String, Object>> list = assetMapper.listMine(p);
        long total = assetMapper.countMine(p);
        decorate(list);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 详情：未公开素材只有作者本人（或管理员）能看 */
    public Map<String, Object> detail(long assetId) {
        Map<String, Object> row = assetMapper.findById(assetId);
        if (row == null || ((Number) row.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("素材不存在");
        }
        Long uid = AuthContext.userId();
        boolean pub = ((Number) row.get("is_public")).intValue() == 1;
        boolean owner = uid != null && ((Number) row.get("user_id")).longValue() == uid;
        if (!pub && !owner && !AuthContext.isAdmin()) {
            throw BizException.notFound("素材不存在");
        }
        decorate(List.of(row));
        row.put("can_edit", owner || AuthContext.isAdmin());
        return row;
    }

    /* ================= 修改 / 删除 ================= */

    @Transactional
    public Map<String, Object> update(long assetId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> row = assetMapper.findById(assetId);
        if (row == null || ((Number) row.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("素材不存在");
        }
        if (((Number) row.get("user_id")).longValue() != uid) {
            throw BizException.forbidden("只能修改自己的素材");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("asset_id", assetId);
        p.put("user_id", uid);
        String name = Params.str(body, "name");
        if (name != null) {
            name = name.trim();
            if (name.isEmpty()) {
                throw BizException.badRequest("素材名不能为空");
            }
            if (name.length() > 120) {
                name = name.substring(0, 120);
            }
        }
        p.put("name", name == null ? String.valueOf(row.get("name")) : name);

        Long seriesId = null;
        boolean clear = false;
        if (body.containsKey("series_id")) {
            Object raw = body.get("series_id");
            if (raw == null || "".equals(raw) || "0".equals(String.valueOf(raw))) {
                clear = true;
            } else {
                seriesId = Params.lng(body, "series_id");
            }
        }
        if (seriesId != null) {
            seriesService.requireOwn(SeriesService.Kind.ASSET, seriesId, uid);
        }
        p.put("series_id", seriesId);
        p.put("clear_series", clear);
        p.put("is_public", body.get("is_public") == null ? null : (Params.bool(body, "is_public") ? 1 : 0));
        assetMapper.updateMeta(p);

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("asset_id", assetId);
        data.put("updated", true);
        data.put("name", p.get("name"));
        return data;
    }

    /** 软删除：文件留在磁盘（可能已被别人的 HTML 引用），只从库里摘掉 */
    @Transactional
    public Map<String, Object> delete(long assetId) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> row = assetMapper.findById(assetId);
        if (row == null || ((Number) row.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("素材不存在");
        }
        if (((Number) row.get("user_id")).longValue() != uid && !AuthContext.isAdmin()) {
            throw BizException.forbidden("只能删除自己的素材");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("asset_id", assetId);
        p.put("user_id", ((Number) row.get("user_id")).longValue());
        assetMapper.softDelete(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("asset_id", assetId);
        data.put("deleted", true);
        return data;
    }

    /* ================= 工具 ================= */

    /** 统一补上直链字段：/media/{id}/{file} —— 前端「复制链接」直接用 */
    private void decorate(List<Map<String, Object>> list) {
        for (Map<String, Object> r : list) {
            Object id = r.get("asset_id");
            Object fn = r.get("file_name");
            if (id != null && fn != null) {
                r.put("url", mediaUrl(((Number) id).longValue(), String.valueOf(fn)));
            }
        }
    }

    public static String mediaUrl(long assetId, String fileName) {
        return "/media/" + assetId + "/" + fileName;
    }

    /** 去掉路径分隔符与控制字符，防止 ..\\ 穿越；保留中文与常见符号，方便直链可读 */
    public static String sanitize(String raw) {
        String name = raw == null ? "" : raw.replace('\\', '/');
        int slash = name.lastIndexOf('/');
        if (slash >= 0) {
            name = name.substring(slash + 1);
        }
        StringBuilder sb = new StringBuilder();
        for (char c : name.toCharArray()) {
            if (c < 32) {
                continue;
            }
            if (c == '/' || c == ':' || c == '*' || c == '?' || c == '"' || c == '<' || c == '>' || c == '|') {
                sb.append('_');
            } else {
                sb.append(c);
            }
        }
        String out = sb.toString().trim();
        while (out.startsWith(".")) {
            out = out.substring(1);
        }
        if (out.isEmpty()) {
            out = "file";
        }
        if (out.length() > 100) {
            String ext = extOf(out);
            String base = out.substring(0, Math.max(1, 100 - ext.length() - 1));
            out = base + (ext.isEmpty() ? "" : "." + ext);
        }
        return out;
    }

    public static String extOf(String fileName) {
        int dot = fileName == null ? -1 : fileName.lastIndexOf('.');
        if (dot < 0 || dot == fileName.length() - 1) {
            return "";
        }
        return fileName.substring(dot + 1).toLowerCase(Locale.ROOT);
    }

    private static String stripExt(String fileName) {
        int dot = fileName.lastIndexOf('.');
        return dot > 0 ? fileName.substring(0, dot) : fileName;
    }

    /** 同目录重名时加 -2 / -3，避免覆盖已存在文件（同一素材目录理论不会重名，兜底用） */
    private Path uniqueTarget(Path dir, String fileName, long assetId) {
        Path p = dir.resolve(fileName);
        if (!Files.exists(p)) {
            return p;
        }
        String ext = extOf(fileName);
        String base = stripExt(fileName);
        int i = 2;
        Set<String> tried = new LinkedHashSet<>();
        while (Files.exists(p) && i < 100) {
            String n = base + "-" + i + (ext.isEmpty() ? "" : "." + ext);
            if (!tried.add(n)) {
                break;
            }
            p = dir.resolve(n);
            i++;
        }
        return p;
    }
}
