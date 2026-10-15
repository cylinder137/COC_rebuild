package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.ReportMapper;
import uk.collectionofcreations.coc.mapper.UserMapper;

import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 举报业务：提交举报、我的举报（含正反馈积分）、标记处理结果已读。
 */
@Service
public class ReportService {

    private static final Set<String> TARGET_TYPES = Set.of("project", "comment", "user");

    private final ReportMapper reportMapper;
    private final UserMapper userMapper;

    public ReportService(ReportMapper reportMapper, UserMapper userMapper) {
        this.reportMapper = reportMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        String targetType = Params.requireStr(body, "target_type", "举报对象类型");
        if (!TARGET_TYPES.contains(targetType)) {
            throw BizException.badRequest("target_type 只支持 project / comment / user");
        }
        long targetId = Params.requireLng(body, "target_id", "举报对象ID");
        String reasonType = Params.requireStr(body, "reason_type", "举报原因");
        String detail = Params.str(body, "detail");
        if (detail != null && detail.length() > 500) {
            throw BizException.badRequest("补充说明过长（<=500 字）");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("reporter_id", uid);
        p.put("target_type", targetType);
        p.put("target_id", targetId);
        p.put("reason_type", reasonType);
        p.put("detail", detail);
        reportMapper.insertReport(p);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report_id", p.get("report_id"));
        data.put("status", 0);
        data.put("message", "举报已提交，管理员核实后会把处理结果通知你（有效举报可获得正反馈积分）");
        return data;
    }

    /** 我的举报 + 正反馈积分（诉求 #11） */
    public Map<String, Object> mine(Map<String, Object> query) {
        long uid = AuthContext.requireUserId();
        int page = Params.page(query);
        int size = Params.size(query, 20, 50);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("reporter_id", uid);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = reportMapper.listMine(q);
        long total = reportMapper.countMine(uid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        Integer points = userMapper.findRewardPoints(uid);
        data.put("reward_points", points == null ? 0 : points);
        data.put("unread", reportMapper.countUnreadMine(uid));
        return data;
    }

    /** 标记处理结果已读 */
    @Transactional
    public Map<String, Object> markRead() {
        long uid = AuthContext.requireUserId();
        int n = reportMapper.markMineRead(uid);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("updated", n);
        return data;
    }
}
