package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.BugFeedbackMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * Bug 反馈：用户提交、管理端按状态查询与标记已处理。
 */
@Service
public class BugFeedbackService {

    private final BugFeedbackMapper bugFeedbackMapper;

    public BugFeedbackService(BugFeedbackMapper bugFeedbackMapper) {
        this.bugFeedbackMapper = bugFeedbackMapper;
    }

    /** 提交反馈（需登录） */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        String content = Params.requireStr(body, "content", "反馈内容");
        if (content.length() > 1000) {
            throw BizException.badRequest("反馈内容过长（<=1000 字）");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("user_id", uid);
        p.put("content", content);
        bugFeedbackMapper.insertFeedback(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bug_id", p.get("bug_id"));
        data.put("status", 0);
        data.put("message", "反馈已提交，感谢你的帮助");
        return data;
    }

    /** 管理端列表：status 传 0/1，不传为全部 */
    public Map<String, Object> adminList(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("status", Params.integer(query, "status", null));
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = bugFeedbackMapper.adminList(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", bugFeedbackMapper.adminCount(q));
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 标记已处理 */
    @Transactional
    public Map<String, Object> handle(long bugId) {
        AuthContext.requireAdmin();
        Map<String, Object> row = bugFeedbackMapper.findById(bugId);
        if (row == null) {
            throw BizException.notFound("反馈不存在");
        }
        if (((Number) row.get("status")).intValue() != 0) {
            throw BizException.badRequest("该反馈已处理");
        }
        bugFeedbackMapper.markHandled(bugId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bug_id", bugId);
        data.put("status", 1);
        return data;
    }

    /** 管理端详情 */
    public Map<String, Object> adminDetail(long bugId) {
        AuthContext.requireAdmin();
        Map<String, Object> row = bugFeedbackMapper.findById(bugId);
        if (row == null) {
            throw BizException.notFound("反馈不存在");
        }
        return row;
    }

    /** 管理端新增：代用户补录，user_id 不传记在管理员名下 */
    @Transactional
    public Map<String, Object> adminCreate(Map<String, Object> body) {
        AuthContext.requireAdmin();
        String content = Params.requireStr(body, "content", "反馈内容");
        if (content.length() > 1000) {
            throw BizException.badRequest("反馈内容过长（<=1000 字）");
        }
        Long uid = Params.lng(body, "user_id");
        if (uid == null) {
            uid = AuthContext.requireUserId();
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("user_id", uid);
        p.put("content", content);
        bugFeedbackMapper.insertFeedback(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bug_id", p.get("bug_id"));
        data.put("status", 0);
        return data;
    }

    /** 管理端编辑：content / status 至少改一项 */
    @Transactional
    public Map<String, Object> adminUpdate(long bugId, Map<String, Object> body) {
        AuthContext.requireAdmin();
        Map<String, Object> row = bugFeedbackMapper.findById(bugId);
        if (row == null) {
            throw BizException.notFound("反馈不存在");
        }
        String content = Params.str(body, "content");
        Integer status = Params.integer(body, "status", null);
        if (content == null && status == null) {
            throw BizException.badRequest("没有要修改的字段");
        }
        if (status != null && status != 0 && status != 1) {
            throw BizException.badRequest("status 只支持 0（未受理）/ 1（已处理）");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("bug_id", bugId);
        p.put("content", content);
        p.put("status", status);
        bugFeedbackMapper.updateById(p);
        return bugFeedbackMapper.findById(bugId);
    }

    /** 管理端删除 */
    @Transactional
    public Map<String, Object> adminDelete(long bugId) {
        AuthContext.requireAdmin();
        Map<String, Object> row = bugFeedbackMapper.findById(bugId);
        if (row == null) {
            throw BizException.notFound("反馈不存在");
        }
        bugFeedbackMapper.deleteById(bugId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("bug_id", bugId);
        data.put("deleted", true);
        return data;
    }
}
