package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.CategoryMapper;
import uk.collectionofcreations.coc.mapper.ImMapper;
import uk.collectionofcreations.coc.mapper.InteractionMapper;
import uk.collectionofcreations.coc.mapper.LogMapper;
import uk.collectionofcreations.coc.mapper.ProjectMapper;
import uk.collectionofcreations.coc.mapper.ViewMapper;

import java.io.BufferedReader;
import java.io.IOException;
import java.nio.charset.StandardCharsets;
import java.nio.file.Files;
import java.nio.file.Path;
import java.nio.file.StandardOpenOption;
import java.time.LocalDate;
import java.time.LocalDateTime;
import java.time.format.DateTimeFormatter;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 项目内容业务：列表 / 详情 / 浏览计数 / 统计折线 / 运行地址 / 投稿（草稿、提交）。
 */
@Service
public class ProjectService {

    private static final Set<String> TYPES = Set.of("html", "scratch", "structure");
    /** 已下线类型：历史项目仍可读、可改，但不再允许新建（五期：MC 结构 → 素材库） */
    private static final Set<String> RETIRED_TYPES = Set.of("structure");
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");
    /** 列表代码预览：最多读入口文件前 1400 字符，纯静态展示、不执行，省用户端算力 */
    private static final int PREVIEW_CHARS = 1400;
    /** 可在线编辑的源码文本后缀 */
    private static final Set<String> TEXT_EXT = Set.of(
            "html", "htm", "css", "js", "json", "txt", "xml", "svg", "md", "yml", "yaml",
            "py", "java", "c", "cpp", "h", "cs", "php", "go", "rs", "sh");
    private static final long MAX_EDIT_BYTES = 2L * 1024 * 1024;

    private final ProjectMapper projectMapper;
    private final CategoryMapper categoryMapper;
    private final ViewMapper viewMapper;
    private final InteractionMapper interactionMapper;
    private final ImMapper imMapper;
    private final FileService fileService;
    private final SeriesService seriesService;
    private final uk.collectionofcreations.coc.storage.StorageService storageService;
    private final LogMapper logMapper;

    public ProjectService(ProjectMapper projectMapper, CategoryMapper categoryMapper, ViewMapper viewMapper,
                          InteractionMapper interactionMapper, ImMapper imMapper, FileService fileService,
                          SeriesService seriesService,
                          uk.collectionofcreations.coc.storage.StorageService storageService,
                          LogMapper logMapper) {
        this.projectMapper = projectMapper;
        this.categoryMapper = categoryMapper;
        this.viewMapper = viewMapper;
        this.interactionMapper = interactionMapper;
        this.imMapper = imMapper;
        this.fileService = fileService;
        this.seriesService = seriesService;
        this.storageService = storageService;
        this.logMapper = logMapper;
    }

    // ---------------- 封面 ----------------

    /** 封面图上限 5 MB；只收常见图片后缀 */
    private static final long COVER_MAX_BYTES = 5L * 1024 * 1024;
    private static final Set<String> COVER_EXT = Set.of("jpg", "jpeg", "png", "webp", "gif");
    private static final DateTimeFormatter STAMP = DateTimeFormatter.ofPattern("yyyyMMddHHmmssSSS");

    /** 作者（或管理员）上传/更换项目封面：落到 covers/p{项目id}-{时间戳}.{ext}，并写回 project.cover_path */
    @Transactional
    public Map<String, Object> uploadCover(long projectId, org.springframework.web.multipart.MultipartFile file) {
        Long uid = AuthContext.requireUserId();
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        boolean isAdmin = AuthContext.isAdmin();
        long authorId = ((Number) project.get("author_id")).longValue();
        if (!isAdmin && !uid.equals(authorId)) {
            throw BizException.forbidden("只有作者本人可以上传封面");
        }
        if (file == null || file.isEmpty()) {
            throw BizException.badRequest("请选择一张封面图片");
        }
        if (file.getSize() > COVER_MAX_BYTES) {
            throw BizException.badRequest("封面不能超过 5 MB");
        }
        String orig = file.getOriginalFilename() == null ? "" : file.getOriginalFilename();
        int dot = orig.lastIndexOf('.');
        String ext = dot >= 0 ? orig.substring(dot + 1).toLowerCase() : "";
        if (!COVER_EXT.contains(ext)) {
            throw BizException.badRequest("封面只支持 jpg / jpeg / png / webp / gif");
        }
        String contentType = file.getContentType();
        if (contentType != null && !contentType.toLowerCase().startsWith("image/")) {
            throw BizException.badRequest("文件类型不是图片");
        }
        String name = "p" + projectId + "-" + STAMP.format(LocalDateTime.now()) + "." + ext;
        try {
            java.nio.file.Path dir = storageService.coversDir();
            java.nio.file.Path target = dir.resolve(name);
            try (java.io.InputStream in = file.getInputStream()) {
                java.nio.file.Files.copy(in, target, java.nio.file.StandardCopyOption.REPLACE_EXISTING);
            }
            // 换封面时清掉旧图：只删本接口生成的（p{项目id}-{时间戳}.{ext}）且位于 covers 目录内的文件，
            // 避免误删种子封面/共享图（种子图如 cover-02.png 可能被别的作品共用）
            Object old = project.get("cover_path");
            if (old != null && !String.valueOf(old).isBlank()) {
                java.nio.file.Path oldPath = storageService.resolveStored(String.valueOf(old));
                if (isGeneratedCover(oldPath, dir) && !oldPath.getFileName().toString().equals(name)) {
                    storageService.deleteQuietly(oldPath);
                }
            }
            Map<String, Object> p = new LinkedHashMap<>();
            p.put("project_id", projectId);
            p.put("cover_path", "covers/" + name);
            projectMapper.updateCover(p);

            Map<String, Object> data = new LinkedHashMap<>();
            data.put("project_id", projectId);
            data.put("cover_path", "covers/" + name);
            data.put("cover_url", "/covers/" + name);
            HotCache.invalidate("projects:");
            data.put("file_name", name);
            data.put("size", file.getSize());
            return data;
        } catch (java.io.IOException e) {
            throw new BizException(1003, "封面保存失败：" + e.getMessage());
        }
    }

    /** 是否本接口生成的封面文件（p{项目id}-{毫秒时间戳}.{ext} 且位于 covers 目录内）——只删这种，不碰种子/共享图 */
    private boolean isGeneratedCover(java.nio.file.Path p, java.nio.file.Path coversDir) {
        if (p == null || p.getParent() == null || !p.getParent().equals(coversDir)) {
            return false;
        }
        return p.getFileName().toString().matches("^p\\d+-\\d{14,17}\\.(jpg|jpeg|png|webp|gif)$");
    }

    /** 移除封面（只清库里的 cover_path + 删本接口生成的封面文件） */
    @Transactional
    public Map<String, Object> removeCover(long projectId) {
        Long uid = AuthContext.requireUserId();
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        if (!AuthContext.isAdmin() && !uid.equals(((Number) project.get("author_id")).longValue())) {
            throw BizException.forbidden("只有作者本人可以移除封面");
        }
        Object old = project.get("cover_path");
        if (old != null && !String.valueOf(old).isBlank()) {
            try {
                java.nio.file.Path dir = storageService.coversDir();
                java.nio.file.Path oldPath = storageService.resolveStored(String.valueOf(old));
                if (isGeneratedCover(oldPath, dir)) {
                    storageService.deleteQuietly(oldPath);
                }
            } catch (java.io.IOException ignored) {
                // 目录不可用时忽略：库里清空即可
            }
        }
        projectMapper.clearCover(projectId);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("cover_path", null);
        return data;
    }

    // ---------------- 列表 / 详情 ----------------

    public Map<String, Object> list(Map<String, Object> query) {
        int page = Params.page(query);
        int size = Params.size(query, 12, 60);
        Map<String, Object> q = new LinkedHashMap<>(query);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        // 首页作品流 / 排行榜是典型的读多写少：内存缓存 20 秒（投稿/审核/删除时会主动失效）
        String cacheKey = "projects:list:" + page + ":" + size + ":" + q.toString();
        Object cached = HotCache.get(cacheKey);
        if (cached instanceof Map) {
            @SuppressWarnings("unchecked")
            Map<String, Object> hit = (Map<String, Object>) cached;
            return hit;
        }
        List<Map<String, Object>> list = projectMapper.listPublished(q);
        long total = projectMapper.countPublished(q);
        // 代码预览：浏览页传 include_preview=1 时，给每张卡附上入口文件的静态片段（不运行项目）。
        // 用参数参与缓存 key（cacheKey 由 q.toString 生成），带/不带预览各缓存一份，互不污染。
        if (Params.bool(q, "include_preview")) {
            for (Map<String, Object> item : list) {
                Object idv = item.get("project_id");
                if (idv == null) continue;
                item.put("code_preview", codePreview(((Number) idv).longValue()));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        HotCache.put(cacheKey, data, 20_000L);
        return data;
    }

    /**
     * 入口源码的静态预览片段（只读取、不执行），供列表卡片展示。
     * 优先运行目录 build 的入口，其次 source；读不到就返回空串，绝不影响列表主流程。
     */
    private String codePreview(long projectId) {
        try {
            Map<String, Object> version = projectMapper.findLatestVersion(projectId);
            if (version == null) return "";
            int vno = ((Number) version.get("version_no")).intValue();
            Path base = storageService.getRoot().resolve("projects")
                    .resolve(String.valueOf(projectId)).resolve("v" + vno);
            Path dir = base.resolve("build");
            String entry = fileService.resolveRunEntry(dir);
            if (entry == null) {
                dir = base.resolve("source");
                entry = fileService.resolveRunEntry(dir);
            }
            if (entry == null) return "";
            Path file = dir.resolve(entry).normalize();
            if (!file.startsWith(dir.normalize()) || !Files.isRegularFile(file)) return "";
            StringBuilder sb = new StringBuilder();
            try (BufferedReader r = Files.newBufferedReader(file, StandardCharsets.UTF_8)) {
                char[] buf = new char[1024];
                int n;
                while (sb.length() < PREVIEW_CHARS && (n = r.read(buf)) != -1) {
                    sb.append(buf, 0, Math.min(n, PREVIEW_CHARS - sb.length()));
                }
            }
            return sb.toString();
        } catch (Exception ignore) {
            return "";
        }
    }

    public Map<String, Object> detail(long projectId) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        int status = ((Number) project.get("status")).intValue();
        int isDeleted = ((Number) project.get("is_deleted")).intValue();
        Long me = AuthContext.userId();
        long authorId = ((Number) project.get("author_id")).longValue();
        boolean isAuthor = me != null && me == authorId;
        boolean isAdmin = AuthContext.isAdmin();
        if (isDeleted == 1 || (status != 2 && !isAuthor && !isAdmin)) {
            throw BizException.notFound("项目不存在或不可见");
        }

        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", project.get("project_id"));
        data.put("title", project.get("title"));
        data.put("summary", project.get("summary"));
        data.put("description", project.get("description"));
        data.put("project_type", project.get("project_type"));
        data.put("category_id", project.get("category_id"));
        data.put("category_name", project.get("category_name"));
        data.put("cover_path", project.get("cover_path"));
        data.put("status", status);
        data.put("audit_mode", project.get("audit_mode"));
        data.put("is_deleted", isDeleted);
        data.put("reject_reason", project.get("reject_reason"));
        data.put("down_reason", project.get("down_reason"));
        data.put("like_count", project.get("like_count"));
        data.put("favorite_count", project.get("favorite_count"));
        data.put("comment_count", project.get("comment_count"));
        data.put("view_count_total", project.get("view_count_total"));
        data.put("published_at", project.get("published_at"));
        data.put("created_at", project.get("created_at"));
        data.put("updated_at", project.get("updated_at"));
        Map<String, Object> author = new LinkedHashMap<>();
        author.put("user_id", project.get("author_id"));
        author.put("nickname", project.get("author_nickname"));
        author.put("avatar_path", project.get("author_avatar"));
        data.put("author", author);
        data.put("is_author", isAuthor);
        // 所属项目系列 + 同系列的其他项目（详情页做「系列导航」）
        Object seriesId = project.get("series_id");
        data.put("series_id", seriesId);
        data.put("series_name", project.get("series_name"));
        data.put("series_is_public", project.get("series_is_public"));
        if (seriesId != null) {
            List<Map<String, Object>> siblings = new ArrayList<>();
            for (Map<String, Object> s : projectMapper.listBySeries(((Number) seriesId).longValue())) {
                boolean published = s.get("status") != null && ((Number) s.get("status")).intValue() == 2;
                // 同系列里未发布的项目不能泄漏给访客，只给作者/管理员看
                if (!published && !isAuthor && !isAdmin) {
                    continue;
                }
                Map<String, Object> brief = new LinkedHashMap<>();
                brief.put("project_id", s.get("project_id"));
                brief.put("title", s.get("title"));
                brief.put("summary", s.get("summary"));
                brief.put("cover_path", s.get("cover_path"));
                brief.put("project_type", s.get("project_type"));
                brief.put("status", s.get("status"));
                brief.put("is_current", ((Number) s.get("project_id")).longValue() == projectId);
                siblings.add(brief);
            }
            data.put("series_projects", siblings);
        }

        List<Map<String, Object>> files = projectMapper.listFilesByProject(projectId);
        List<Map<String, Object>> fileBrief = new ArrayList<>();
        for (Map<String, Object> f : files) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("project_file_id", f.get("project_file_id"));
            x.put("version_no", f.get("version_no"));
            x.put("file_type", f.get("file_type"));
            x.put("file_name", f.get("file_name"));
            x.put("file_size", f.get("file_size"));
            x.put("build_status", f.get("build_status"));
            fileBrief.add(x);
        }
        data.put("files", fileBrief);
        data.put("tags", categoryMapper.listTagsByProject(projectId));
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        data.put("latest_version", version == null ? null : version.get("version_no"));

        if (me != null) {
            data.put("liked", interactionMapper.findProjectLike(projectId, me) != null);
            data.put("favorited", interactionMapper.findProjectFavorite(projectId, me) != null);
        } else {
            data.put("liked", false);
            data.put("favorited", false);
        }
        return data;
    }

    /** 访客也可调用的浏览计数（只写天/小时计数表 + 总数，无用户维度） */
    @Transactional
    public Map<String, Object> recordView(long projectId) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        viewMapper.upsertDaily(projectId);
        viewMapper.upsertHourly(projectId);
        projectMapper.incViewCount(projectId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("view_count_total", ((Number) project.get("view_count_total")).longValue() + 1);
        return data;
    }

    /** 统计折线：range=2 走小时表（48 点），7/15/30 走天表；评论数动态计算 */
    public List<Map<String, Object>> stats(long projectId, int range) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        List<Map<String, Object>> points = new ArrayList<>();
        if (range == 2) {
            Map<String, Object> q = new LinkedHashMap<>();
            q.put("project_id", projectId);
            q.put("hours", 47);
            Map<String, Long> views = toCountMap(viewMapper.hourlyViews(q));
            Map<String, Long> comments = toCountMap(viewMapper.hourlyComments(q));
            LocalDateTime base = LocalDateTime.now().withMinute(0).withSecond(0).withNano(0);
            for (int i = 47; i >= 0; i--) {
                LocalDateTime t = base.minusHours(i);
                String key = t.format(HOUR_FMT);
                points.add(point(key, views, comments));
            }
            return points;
        }
        int days = (range == 15 || range == 30) ? range : 7;
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("project_id", projectId);
        q.put("days", days - 1);
        Map<String, Long> views = toCountMap(viewMapper.dailyViews(q));
        Map<String, Long> comments = toCountMap(viewMapper.dailyComments(q));
        LocalDate today = LocalDate.now();
        for (int i = days - 1; i >= 0; i--) {
            String key = today.minusDays(i).format(DAY_FMT);
            points.add(point(key, views, comments));
        }
        return points;
    }

    private Map<String, Object> point(String key, Map<String, Long> views, Map<String, Long> comments) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("date", key);
        p.put("views", views.getOrDefault(key, 0L));
        p.put("comments", comments.getOrDefault(key, 0L));
        return p;
    }

    private Map<String, Long> toCountMap(List<Map<String, Object>> rows) {
        Map<String, Long> m = new LinkedHashMap<>();
        for (Map<String, Object> r : rows) {
            Object k = r.get("stat_key");
            Object c = r.get("cnt");
            if (k != null && c != null) {
                m.put(String.valueOf(k), ((Number) c).longValue());
            }
        }
        return m;
    }

    /** 运行地址：优先构建产物目录，其次源码目录（沙箱 iframe 使用） */
    public Map<String, Object> runInfo(long projectId) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        int status = ((Number) project.get("status")).intValue();
        Long me = AuthContext.userId();
        boolean isAuthor = me != null && me == ((Number) project.get("author_id")).longValue();
        if (status != 2 && !isAuthor && !AuthContext.isAdmin()) {
            throw BizException.notFound("项目不存在或不可见");
        }
        String type = String.valueOf(project.get("project_type"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("mode", "scratch".equals(type) ? "scratch" : "html");
        data.put("project_type", type);
        data.put("sandbox", true);
        // 入口名按运行目录实际内容解析（兼容用户上传的任意 HTML 文件名）
        String entry = null;
        try {
            entry = fileService.resolveRunEntry(fileService.resolveRunRoot(projectId));
        } catch (Exception ignored) {
            // 目录还没就绪时不影响 run 接口返回地址
        }
        data.put("entry", entry == null ? "index.html" : entry);
        return data;
    }

    // ---------------- 投稿 ----------------

    @Transactional
    public Map<String, Object> createDraft(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        String title = Params.requireStr(body, "title", "标题");
        String type = Params.requireStr(body, "project_type", "项目类型");
        if (!TYPES.contains(type)) {
            throw BizException.badRequest("项目类型只支持 html / scratch");
        }
        if (RETIRED_TYPES.contains(type)) {
            throw BizException.badRequest("MC 结构板块已下线：.mcstructure 等结构文件请到「素材」里上传，再在作品里引用其直链");
        }
        if (title.length() > 100) {
            throw BizException.badRequest("标题过长（<=100 字）");
        }
        Long seriesId = resolveSeriesId(body, uid);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("author_id", uid);
        p.put("title", title);
        p.put("summary", Params.str(body, "summary"));
        p.put("description", Params.str(body, "description"));
        p.put("project_type", type);
        p.put("category_id", Params.lng(body, "category_id"));
        p.put("series_id", seriesId);
        p.put("cover_path", Params.str(body, "cover_path"));
        p.put("status", 0);
        p.put("audit_mode", "html".equals(type) ? "manual" : "auto");
        projectMapper.insertProject(p);
        long projectId = ((Number) p.get("project_id")).longValue();
        saveTags(projectId, body.get("tags"));
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("status", 0);
        data.put("audit_mode", p.get("audit_mode"));
        return data;
    }

    /**
     * 解析「归属项目系列」：可以传已有 series_id，也可以传 new_series_name 就近建一个。
     * 传进来的系列必须属于当前用户，否则 403（防止挂到别人的系列里）。
     */
    private Long resolveSeriesId(Map<String, Object> body, long uid) {
        Long seriesId = Params.lng(body, "series_id");
        String newName = Params.str(body, "new_series_name");
        if (seriesId == null && newName != null && !newName.isBlank()) {
            seriesId = seriesService.ensureByName(SeriesService.Kind.PROJECT, uid, newName, false);
        }
        if (seriesId != null) {
            seriesService.requireOwn(SeriesService.Kind.PROJECT, seriesId, uid);
        }
        return seriesId;
    }

    @Transactional
    public Map<String, Object> updateDraft(long projectId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = requireOwnEditable(projectId, uid);
        String type = Params.str(body, "project_type", String.valueOf(project.get("project_type")));
        if (!TYPES.contains(type)) {
            throw BizException.badRequest("项目类型只支持 html / scratch / structure");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("title", Params.str(body, "title", String.valueOf(project.get("title"))));
        p.put("summary", Params.str(body, "summary", (String) project.get("summary")));
        p.put("description", Params.str(body, "description", (String) project.get("description")));
        p.put("project_type", type);
        p.put("category_id", Params.lng(body, "category_id") != null ? Params.lng(body, "category_id") : project.get("category_id"));
        p.put("cover_path", Params.str(body, "cover_path"));
        // 系列：显式传 null / 0 / 空串 表示「移出系列」，不传则保持原样
        if (body.containsKey("series_id")) {
            Object raw = body.get("series_id");
            if (raw == null || "".equals(raw) || "0".equals(String.valueOf(raw))) {
                p.put("clear_series", true);
            } else {
                p.put("series_id", resolveSeriesId(body, uid));
            }
        }
        projectMapper.updateProjectBasic(p);
        if (body.get("tags") != null) {
            categoryMapper.deleteProjectTags(projectId);
            saveTags(projectId, body.get("tags"));
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("updated", true);
        return data;
    }

    /** 提交：html → 待审(1)；scratch / structure → 免审直发(2) 并建项目群 */
    @Transactional
    public Map<String, Object> submit(long projectId) {
        long uid = AuthContext.requireUserId();
        // 2026-11-04：幂等 —— 上传完成已自动置为待审核(1)时，重复 submit 不再报错
        Map<String, Object> preSubmit = projectMapper.findProjectById(projectId);
        if (preSubmit != null && ((Number) preSubmit.get("is_deleted")).intValue() == 0
                && ((Number) preSubmit.get("status")).intValue() == 1) {
            Map<String, Object> dup = new LinkedHashMap<>();
            dup.put("project_id", projectId);
            dup.put("status", 1);
            dup.put("audit_mode", "manual");
            dup.put("message", "已在审核中，无需重复提交");
            return dup;
        }
        Map<String, Object> project = requireOwnEditable(projectId, uid);
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        if (version == null) {
            throw BizException.badRequest("请先上传项目文件再提交");
        }
        Map<String, Object> sourceQuery = new LinkedHashMap<>();
        sourceQuery.put("project_version_id", version.get("project_version_id"));
        sourceQuery.put("file_type", "source");
        if (projectMapper.findFileByVersionAndType(sourceQuery) == null) {
            throw BizException.badRequest("请先上传项目源文件再提交");
        }
        String type = String.valueOf(project.get("project_type"));
        boolean auto = !"html".equals(type);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("project_type", type);
        if (auto) {
            projectMapper.publishProject(projectId);
            ensureProjectGroup(projectId, uid, String.valueOf(project.get("title")));
            data.put("status", 2);
            data.put("audit_mode", "auto");
            data.put("message", "免审类型，已直接发布");
        } else {
            projectMapper.submitForReview(projectId);
            data.put("status", 1);
            data.put("audit_mode", "manual");
            data.put("message", "已提交审核，等待管理员处理");
        }
        // 首页/排行缓存立即失效，作者刷新就能看到自己的新作品
        HotCache.invalidate("projects:");
        return data;
    }

    /** 我的作品 */
    public Map<String, Object> myProjects(Map<String, Object> query) {
        long uid = AuthContext.requireUserId();
        int page = Params.page(query);
        int size = Params.size(query, 12, 60);
        Map<String, Object> q = new LinkedHashMap<>(query);
        q.put("author_id", uid);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        // 前端可能传 1 / "1" / true，统一成 Boolean，否则 Mapper 里 no_series == true 判断不成立
        q.put("no_series", Params.bool(q, "no_series"));
        List<Map<String, Object>> list = projectMapper.listMine(q);
        long total = projectMapper.countMine(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 作者主页的已发布作品 */
    public List<Map<String, Object>> publishedByAuthor(long authorId, int limit) {
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("author_id", authorId);
        q.put("status", 2);
        q.put("limit", limit);
        q.put("offset", 0);
        return projectMapper.listMine(q);
    }

    /** 项目群（kind=2）：发布时自动建立，作者先入群 */
    public void ensureProjectGroup(long projectId, long authorId, String title) {
        Map<String, Object> session = imMapper.findGroupSessionByProject(projectId);
        long sessionId;
        if (session == null) {
            Map<String, Object> s = new LinkedHashMap<>();
            s.put("kind", 2);
            s.put("project_id", projectId);
            s.put("user_a_id", authorId);
            s.put("user_b_id", null);
            s.put("title", (title == null ? "项目群" : title));
            imMapper.insertSession(s);
            sessionId = ((Number) s.get("im_session_id")).longValue();
        } else {
            sessionId = ((Number) session.get("im_session_id")).longValue();
        }
        Map<String, Object> m = new LinkedHashMap<>();
        m.put("session_id", sessionId);
        m.put("user_id", authorId);
        imMapper.addMember(m);
    }

    private void saveTags(long projectId, Object tags) {
        if (!(tags instanceof List<?> list)) {
            return;
        }
        for (Object t : list) {
            if (t == null) {
                continue;
            }
            String name = String.valueOf(t).trim();
            if (name.isEmpty() || name.length() > 30) {
                continue;
            }
            Map<String, Object> tag = new LinkedHashMap<>();
            tag.put("name", name);
            categoryMapper.insertTagIgnore(tag);
            Map<String, Object> found = categoryMapper.findTagByName(name);
            if (found != null) {
                Map<String, Object> rel = new LinkedHashMap<>();
                rel.put("project_id", projectId);
                rel.put("tag_id", found.get("tag_id"));
                categoryMapper.insertProjectTag(rel);
            }
        }
    }

    private Map<String, Object> requireOwnEditable(long projectId, long uid) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        if (((Number) project.get("author_id")).longValue() != uid) {
            throw BizException.forbidden("只有作者本人可以修改");
        }
        int status = ((Number) project.get("status")).intValue();
        if (status != 0 && status != 4) {
            throw BizException.badRequest("只有草稿或已拒绝的项目可以编辑");
        }
        return project;
    }

    // ===================================================================
    // 作者自助管理（我的作品页完整增删改查）—— 需求 1
    // ===================================================================

    /** 作者本人的项目（未删除，任意状态）：改标题/简介/分类/标签，已发布也能改元信息 */
    private Map<String, Object> requireOwnAny(long projectId, long uid) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        if (((Number) project.get("author_id")).longValue() != uid) {
            throw BizException.forbidden("只有作者本人可以操作");
        }
        return project;
    }

    /** 作者修改项目的标题 / 简介 / 描述 / 分类 / 标签（不改状态，已发布作品即时生效） */
    @Transactional
    public Map<String, Object> updateMine(long projectId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = requireOwnAny(projectId, uid);
        String title = Params.str(body, "title", String.valueOf(project.get("title")));
        if (title == null || title.trim().isEmpty()) throw BizException.badRequest("标题不能为空");
        if (title.length() > 100) throw BizException.badRequest("标题过长（<=100 字）");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("title", title.trim());
        p.put("summary", Params.str(body, "summary", (String) project.get("summary")));
        p.put("description", Params.str(body, "description", (String) project.get("description")));
        p.put("project_type", String.valueOf(project.get("project_type")));
        Object cat = body.containsKey("category_id") ? body.get("category_id") : project.get("category_id");
        p.put("category_id", cat == null || "".equals(cat) ? null : cat);
        projectMapper.updateProjectBasic(p);
        if (body.containsKey("tags")) {
            categoryMapper.deleteProjectTags(projectId);
            saveTags(projectId, body.get("tags"));
        }
        HotCache.invalidate("projects:");
        selfLog(uid, "self_update_project", projectId, "作者修改作品元信息：《" + title + "》");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("updated", true);
        return data;
    }

    /** 作者下架自己的已发布作品（status 2 → 3） */
    @Transactional
    public Map<String, Object> downMine(long projectId, String reason) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = requireOwnAny(projectId, uid);
        int status = ((Number) project.get("status")).intValue();
        if (status != 2) throw BizException.badRequest("只有已发布的作品可以下架");
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("reason", (reason == null || reason.trim().isEmpty()) ? "作者主动下架" : reason.trim());
        projectMapper.downProject(p);
        HotCache.invalidate("projects:");
        selfLog(uid, "self_down_project", projectId, "作者下架作品：《" + project.get("title") + "》");
        return Map.of("project_id", projectId, "status", 3);
    }

    /** 作者重新上架自己已下架的作品（status 3 → 2） */
    @Transactional
    public Map<String, Object> restoreMine(long projectId) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = requireOwnAny(projectId, uid);
        int status = ((Number) project.get("status")).intValue();
        if (status != 3) throw BizException.badRequest("只有已下架的作品可以恢复上架");
        projectMapper.restoreProject(projectId);
        HotCache.invalidate("projects:");
        selfLog(uid, "self_restore_project", projectId, "作者恢复上架：《" + project.get("title") + "》");
        return Map.of("project_id", projectId, "status", 2);
    }

    /** 作者删除自己的作品（软删，保留文件，可联系管理员恢复） */
    @Transactional
    public Map<String, Object> deleteMine(long projectId, String reason) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = requireOwnAny(projectId, uid);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("reason", (reason == null || reason.trim().isEmpty()) ? "作者主动删除" : reason.trim());
        projectMapper.softDeleteProject(p);
        HotCache.invalidate("projects:");
        selfLog(uid, "self_delete_project", projectId, "作者删除作品：《" + project.get("title") + "》");
        return Map.of("project_id", projectId, "deleted", true);
    }

    /** 把已有作品加入某个项目系列（新建系列走 create；这里只做挂载 + 校验归属） */
    @Transactional
    public Map<String, Object> attachSeries(long projectId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> project = requireOwnAny(projectId, uid);
        Long seriesId = Params.lng(body, "series_id");
        String newName = Params.str(body, "new_series_name");
        if (seriesId == null && (newName == null || newName.isBlank())) {
            throw BizException.badRequest("请选择系列或填写新系列名称");
        }
        if (seriesId == null) {
            seriesId = seriesService.ensureByName(SeriesService.Kind.PROJECT, uid, newName, Params.bool(body, "is_public"));
        } else {
            seriesService.requireOwn(SeriesService.Kind.PROJECT, seriesId, uid);
        }
        seriesService.attach(SeriesService.Kind.PROJECT, seriesId, projectId, uid);
        HotCache.invalidate("projects:");
        selfLog(uid, "self_attach_series", projectId, "作品加入系列 #" + seriesId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("series_id", seriesId);
        return data;
    }

    /** 在线列出作者本人项目的源码文本文件（文本附带内容，供「改代码」用） */
    public Map<String, Object> mySourceFiles(long projectId) {
        long uid = AuthContext.requireUserId();
        requireOwnAny(projectId, uid);
        return readSourceFiles(projectId);
    }

    /** 在线保存作者对源码文本文件的修改 */
    @Transactional
    public Map<String, Object> saveMySource(long projectId, String rel, String content) {
        long uid = AuthContext.requireUserId();
        requireOwnAny(projectId, uid);
        Map<String, Object> res = writeSourceFile(projectId, rel, content);
        HotCache.invalidate("projects:");
        selfLog(uid, "self_edit_source", projectId, "作者在线编辑源码: " + rel);
        return res;
    }

    // ---- 源码读写内部实现（与管理员端同构，鉴权在调用方） ----

    private Map<String, Object> readSourceFiles(long projectId) {
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        List<Map<String, Object>> files = new ArrayList<>();
        Path sourceDir = null;
        if (version != null) {
            int vno = ((Number) version.get("version_no")).intValue();
            sourceDir = storageService.getRoot().resolve("projects").resolve(String.valueOf(projectId))
                    .resolve("v" + vno).resolve("source");
        }
        Map<String, Map<String, Object>> dbByRel = new LinkedHashMap<>();
        for (Map<String, Object> f : projectMapper.listFilesByProject(projectId)) {
            Path p = storageService.resolveStored(String.valueOf(f.get("storage_path")));
            if (p == null) continue;
            String rel = sourceDir != null && p.normalize().startsWith(sourceDir.normalize())
                    ? sourceDir.relativize(p.normalize()).toString().replace('\\', '/')
                    : p.getFileName().toString();
            dbByRel.put(rel, f);
        }
        if (sourceDir != null && Files.isDirectory(sourceDir)) {
            List<Path> paths = new ArrayList<>();
            try (var walk = Files.walk(sourceDir)) {
                walk.filter(Files::isRegularFile).forEach(paths::add);
            } catch (IOException e) {
                throw BizException.badRequest("读取源码目录失败: " + e.getMessage());
            }
            paths.sort((a, b) -> a.toString().compareTo(b.toString()));
            for (Path p : paths) {
                files.add(sourceFileEntry(sourceDir.relativize(p.normalize()).toString().replace('\\', '/'),
                        p, dbByRel.get(sourceDir.relativize(p.normalize()).toString().replace('\\', '/'))));
            }
        } else {
            for (Map.Entry<String, Map<String, Object>> e : dbByRel.entrySet()) {
                Path p = storageService.resolveStored(String.valueOf(e.getValue().get("storage_path")));
                if (p != null && Files.exists(p)) files.add(sourceFileEntry(e.getKey(), p, e.getValue()));
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("files", files);
        return data;
    }

    private Map<String, Object> sourceFileEntry(String rel, Path p, Map<String, Object> db) {
        Map<String, Object> x = new LinkedHashMap<>();
        long size = 0L;
        try { size = Files.size(p); } catch (IOException ignored) {}
        String ext = extOf(p.getFileName().toString());
        boolean text = TEXT_EXT.contains(ext);
        x.put("rel", rel);
        x.put("file_name", p.getFileName().toString());
        x.put("ext", ext);
        x.put("size", size);
        x.put("editable", text && size <= MAX_EDIT_BYTES);
        x.put("file_id", db == null ? null : db.get("project_file_id"));
        String content = null;
        if (text && size <= MAX_EDIT_BYTES) {
            try {
                byte[] bytes = Files.readAllBytes(p);
                if (!hasNul(bytes)) content = new String(bytes, StandardCharsets.UTF_8);
            } catch (IOException ignored) {}
        }
        x.put("content", content);
        return x;
    }

    private Map<String, Object> writeSourceFile(long projectId, String rel, String content) {
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        if (version == null) throw BizException.badRequest("项目尚无版本文件，无法在线编辑");
        int vno = ((Number) version.get("version_no")).intValue();
        Path sourceDir = storageService.getRoot().resolve("projects").resolve(String.valueOf(projectId))
                .resolve("v" + vno).resolve("source");
        if (rel == null || rel.isBlank() || rel.contains("..") || rel.startsWith("/")) {
            throw BizException.badRequest("非法路径");
        }
        Path target = sourceDir.resolve(rel).normalize();
        if (!target.startsWith(sourceDir.normalize())) throw BizException.forbidden("非法路径");
        if (!Files.exists(target) || Files.isDirectory(target)) throw BizException.notFound("文件不存在: " + rel);
        String ext = extOf(target.getFileName().toString());
        if (!TEXT_EXT.contains(ext)) throw BizException.badRequest("该文件类型不可在线编辑");
        byte[] bytes = (content == null ? "" : content).getBytes(StandardCharsets.UTF_8);
        if (bytes.length > MAX_EDIT_BYTES) throw BizException.badRequest("文件超过 2MB，不可在线编辑");
        try {
            Files.write(target, bytes, StandardOpenOption.CREATE, StandardOpenOption.TRUNCATE_EXISTING, StandardOpenOption.WRITE);
        } catch (IOException e) {
            throw BizException.badRequest("写入失败: " + e.getMessage());
        }
        for (Map<String, Object> f : projectMapper.listFilesByProject(projectId)) {
            Path p = storageService.resolveStored(String.valueOf(f.get("storage_path")));
            if (p != null && p.normalize().equals(target)) {
                Map<String, Object> upd = new LinkedHashMap<>();
                upd.put("project_file_id", f.get("project_file_id"));
                upd.put("file_size", (long) bytes.length);
                try { upd.put("sha256", storageService.sha256(target)); } catch (IOException e) { upd.put("sha256", null); }
                projectMapper.updateFileMeta(upd);
                break;
            }
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("rel", rel);
        data.put("size", (long) bytes.length);
        data.put("saved", true);
        return data;
    }

    private String extOf(String name) {
        int i = name.lastIndexOf('.');
        return i >= 0 && i < name.length() - 1 ? name.substring(i + 1).toLowerCase() : "";
    }

    private boolean hasNul(byte[] b) {
        for (byte x : b) if (x == 0) return true;
        return false;
    }

    private void selfLog(long uid, String action, long projectId, String detail) {
        try {
            Map<String, Object> log = new LinkedHashMap<>();
            log.put("admin_id", uid);
            log.put("action", action);
            log.put("target_type", "project");
            log.put("target_id", projectId);
            log.put("detail", detail);
            log.put("ip", null);
            logMapper.insertLog(log);
        } catch (Exception ignore) {
            // 审计失败不阻断主操作
        }
    }
}
