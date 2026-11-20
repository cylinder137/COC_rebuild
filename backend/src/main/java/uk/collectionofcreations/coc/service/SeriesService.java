package uk.collectionofcreations.coc.service;

import org.springframework.dao.DuplicateKeyException;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.SeriesMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 系列：项目系列 / 素材系列。
 * <p>两者是一对平行的结构（一个系列 → 多个项目 / 多个素材），语义只差在子项类型，
 * 因此用同一份逻辑 + {@link Kind} 枚举控制表名，避免写两套几乎一样的 CRUD。
 */
@Service
public class SeriesService {

    /** 系列种类白名单：表名/子表/主键列都只从这里取，不接受前端字符串拼 SQL */
    public enum Kind {
        PROJECT("project_series", "project", "project_id", "author_id", "项目系列"),
        ASSET("asset_series", "asset", "asset_id", "user_id", "素材系列");

        final String table;
        final String child;
        final String idCol;
        final String ownerCol;
        final String label;

        Kind(String table, String child, String idCol, String ownerCol, String label) {
            this.table = table;
            this.child = child;
            this.idCol = idCol;
            this.ownerCol = ownerCol;
            this.label = label;
        }
    }

    private final SeriesMapper seriesMapper;

    public SeriesService(SeriesMapper seriesMapper) {
        this.seriesMapper = seriesMapper;
    }

    /** 前端传 project / asset（也接受 项目 / 素材），其余一律拒绝 */
    public static Kind parseKind(String raw) {
        String v = raw == null ? "" : raw.trim().toLowerCase();
        if (v.isEmpty() || "project".equals(v) || "projects".equals(v) || "项目".equals(raw) || "项目系列".equals(raw)) {
            return Kind.PROJECT;
        }
        if ("asset".equals(v) || "assets".equals(v) || "素材".equals(raw) || "素材系列".equals(raw)) {
            return Kind.ASSET;
        }
        throw BizException.badRequest("系列类型只支持 project / asset");
    }

    /** 我创建的系列（含条目数） */
    public List<Map<String, Object>> listMine(Kind kind) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> p = base(kind);
        p.put("user_id", uid);
        return seriesMapper.listByUser(p);
    }

    /** 某个用户公开的系列（个人主页展示用） */
    public List<Map<String, Object>> listPublicOfUser(Kind kind, long userId) {
        Map<String, Object> p = base(kind);
        p.put("user_id", userId);
        return seriesMapper.listPublicByUser(p);
    }

    @Transactional
    public Map<String, Object> create(Kind kind, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        String name = Params.requireStr(body, "name", kind.label + "名称").trim();
        if (name.length() > 80) {
            throw BizException.badRequest("名称过长（<=80 字）");
        }
        String intro = Params.str(body, "intro");
        if (intro != null && intro.length() > 255) {
            throw BizException.badRequest("简介过长（<=255 字）");
        }
        long count = seriesMapper.countByUser(base(kind, uid));
        if (count >= 50) {
            throw BizException.badRequest("最多创建 50 个" + kind.label);
        }
        Map<String, Object> p = base(kind);
        p.put("user_id", uid);
        p.put("name", name);
        p.put("intro", intro);
        p.put("is_public", Params.bool(body, "is_public") ? 1 : 0);
        try {
            seriesMapper.insert(p);
        } catch (DuplicateKeyException e) {
            throw BizException.badRequest("已有同名" + kind.label + "：" + name);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series_id", p.get("series_id"));
        data.put("name", name);
        data.put("kind", kind.name().toLowerCase());
        return data;
    }

    @Transactional
    public Map<String, Object> update(Kind kind, long seriesId, Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> row = requireOwn(kind, seriesId, uid);
        String name = Params.str(body, "name");
        if (name != null) {
            name = name.trim();
            if (name.isEmpty() || name.length() > 80) {
                throw BizException.badRequest("名称不能为空且不超过 80 字");
            }
        }
        Map<String, Object> p = base(kind);
        p.put("user_id", uid);
        p.put("series_id", seriesId);
        p.put("name", name);
        p.put("intro", Params.str(body, "intro"));
        p.put("is_public", body.get("is_public") == null ? null : (Params.bool(body, "is_public") ? 1 : 0));
        try {
            seriesMapper.update(p);
        } catch (DuplicateKeyException e) {
            throw BizException.badRequest("已有同名" + kind.label + "：" + name);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series_id", seriesId);
        data.put("updated", true);
        data.put("name", name == null ? row.get("name") : name);
        return data;
    }

    /** 删除系列：先把子项的 series_id 置空再删，项目 / 素材本身不受影响 */
    @Transactional
    public Map<String, Object> delete(Kind kind, long seriesId) {
        long uid = AuthContext.requireUserId();
        requireOwn(kind, seriesId, uid);
        Map<String, Object> p = base(kind);
        p.put("user_id", uid);
        p.put("series_id", seriesId);
        long detached = 0;
        try {
            seriesMapper.detachChildren(p);
            detached = seriesMapper.countChildren(kind.child, seriesId);
        } catch (Exception ignored) {
            // 解绑失败不阻断删除，外键 ON DELETE SET NULL 会兜底
        }
        seriesMapper.delete(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("series_id", seriesId);
        data.put("deleted", true);
        data.put("detached_children", detached);
        return data;
    }

    /** 校验系列归属（新建项目/素材时挂载用），不存在或不是自己的就报错 */
    public Map<String, Object> requireOwn(Kind kind, long seriesId, long uid) {
        Map<String, Object> p = base(kind);
        p.put("series_id", seriesId);
        Map<String, Object> row = seriesMapper.findById(p);
        if (row == null) {
            throw BizException.notFound(kind.label + "不存在");
        }
        if (((Number) row.get("user_id")).longValue() != uid) {
            throw BizException.forbidden("只能使用自己创建的" + kind.label);
        }
        return row;
    }

    /** 名称转 ID：前端允许「新建系列」只填名字，这里做一次取或建 */
    @Transactional
    public long ensureByName(Kind kind, long uid, String name, boolean makePublic) {
        String n = name == null ? "" : name.trim();
        if (n.isEmpty()) {
            throw BizException.badRequest(kind.label + "名称不能为空");
        }
        Map<String, Object> q = base(kind);
        q.put("user_id", uid);
        q.put("name", n);
        Map<String, Object> hit = seriesMapper.findByUserName(q);
        if (hit != null) {
            return ((Number) hit.get("series_id")).longValue();
        }
        Map<String, Object> p = base(kind);
        p.put("user_id", uid);
        p.put("name", n);
        p.put("intro", null);
        p.put("is_public", makePublic ? 1 : 0);
        try {
            seriesMapper.insert(p);
            return ((Number) p.get("series_id")).longValue();
        } catch (DuplicateKeyException e) {
            Map<String, Object> again = seriesMapper.findByUserName(q);
            if (again != null) {
                return ((Number) again.get("series_id")).longValue();
            }
            throw BizException.badRequest("创建" + kind.label + "失败，请重试");
        }
    }

    /** 把某个子项挂到系列上（供项目自助管理调用；归属校验由调用方完成） */
    @Transactional
    public void attach(Kind kind, long seriesId, long itemId, long uid) {
        Map<String, Object> p = base(kind);
        p.put("series_id", seriesId);
        p.put("id", itemId);
        p.put("user_id", uid);
        seriesMapper.attachChild(p);
    }

    private Map<String, Object> base(Kind kind) {
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("table", kind.table);
        p.put("child", kind.child);
        p.put("idCol", kind.idCol);
        p.put("ownerCol", kind.ownerCol);
        return p;
    }

    private Map<String, Object> base(Kind kind, long uid) {
        Map<String, Object> p = base(kind);
        p.put("user_id", uid);
        return p;
    }
}
