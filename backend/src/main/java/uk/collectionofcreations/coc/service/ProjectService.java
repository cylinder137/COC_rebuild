package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.CategoryMapper;
import uk.collectionofcreations.coc.mapper.ImMapper;
import uk.collectionofcreations.coc.mapper.InteractionMapper;
import uk.collectionofcreations.coc.mapper.ProjectMapper;
import uk.collectionofcreations.coc.mapper.ViewMapper;

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
    private static final DateTimeFormatter DAY_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd");
    private static final DateTimeFormatter HOUR_FMT = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:00");

    private final ProjectMapper projectMapper;
    private final CategoryMapper categoryMapper;
    private final ViewMapper viewMapper;
    private final InteractionMapper interactionMapper;
    private final ImMapper imMapper;
    private final FileService fileService;

    public ProjectService(ProjectMapper projectMapper, CategoryMapper categoryMapper, ViewMapper viewMapper,
                          InteractionMapper interactionMapper, ImMapper imMapper, FileService fileService) {
        this.projectMapper = projectMapper;
        this.categoryMapper = categoryMapper;
        this.viewMapper = viewMapper;
        this.interactionMapper = interactionMapper;
        this.imMapper = imMapper;
        this.fileService = fileService;
    }

    // ---------------- 列表 / 详情 ----------------

    public Map<String, Object> list(Map<String, Object> query) {
        int page = Params.page(query);
        int size = Params.size(query, 12, 60);
        Map<String, Object> q = new LinkedHashMap<>(query);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = projectMapper.listPublished(q);
        long total = projectMapper.countPublished(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
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
            throw BizException.badRequest("项目类型只支持 html / scratch / structure");
        }
        if (title.length() > 100) {
            throw BizException.badRequest("标题过长（<=100 字）");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("author_id", uid);
        p.put("title", title);
        p.put("summary", Params.str(body, "summary"));
        p.put("description", Params.str(body, "description"));
        p.put("project_type", type);
        p.put("category_id", Params.lng(body, "category_id"));
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
}
