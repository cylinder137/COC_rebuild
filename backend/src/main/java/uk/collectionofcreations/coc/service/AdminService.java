package uk.collectionofcreations.coc.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.security.crypto.bcrypt.BCryptPasswordEncoder;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.CommentMapper;
import uk.collectionofcreations.coc.mapper.LogMapper;
import uk.collectionofcreations.coc.mapper.NotificationMapper;
import uk.collectionofcreations.coc.mapper.PasswordResetMapper;
import uk.collectionofcreations.coc.mapper.ProjectMapper;
import uk.collectionofcreations.coc.mapper.ReportMapper;
import uk.collectionofcreations.coc.mapper.UserMapper;
import uk.collectionofcreations.coc.mapper.ViewMapper;
import uk.collectionofcreations.coc.storage.StorageService;

import java.io.IOException;
import java.nio.file.Files;
import java.nio.file.Path;
import java.security.SecureRandom;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 管理端业务：审核、下架/恢复/删除、评论管理、开发者申请、举报处理、找回密码、注销、平台统计、存储路径。
 */
@Service
public class AdminService {

    private static final String TEMP_ALPHABET = "abcdefghjkmnpqrstuvwxyzABCDEFGHJKMNPQRSTUVWXYZ23456789";
    private static final SecureRandom RANDOM = new SecureRandom();

    private final ProjectMapper projectMapper;
    private final CommentMapper commentMapper;
    private final UserMapper userMapper;
    private final ReportMapper reportMapper;
    private final PasswordResetMapper passwordResetMapper;
    private final NotificationMapper notificationMapper;
    private final LogMapper logMapper;
    private final ViewMapper viewMapper;
    private final StorageService storageService;
    private final ProjectService projectService;
    private final int reportRewardPoints;
    private final BCryptPasswordEncoder encoder = new BCryptPasswordEncoder();

    public AdminService(ProjectMapper projectMapper, CommentMapper commentMapper, UserMapper userMapper,
                        ReportMapper reportMapper, PasswordResetMapper passwordResetMapper,
                        NotificationMapper notificationMapper, LogMapper logMapper, ViewMapper viewMapper,
                        StorageService storageService, ProjectService projectService,
                        @Value("${coc.report.reward-points:5}") int reportRewardPoints) {
        this.projectMapper = projectMapper;
        this.commentMapper = commentMapper;
        this.userMapper = userMapper;
        this.reportMapper = reportMapper;
        this.passwordResetMapper = passwordResetMapper;
        this.notificationMapper = notificationMapper;
        this.logMapper = logMapper;
        this.viewMapper = viewMapper;
        this.storageService = storageService;
        this.projectService = projectService;
        this.reportRewardPoints = reportRewardPoints;
    }

    // ---------------- 项目审核 ----------------

    public Map<String, Object> listProjects(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>(query);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        q.put("include_deleted", Params.bool(query, "include_deleted") ? Boolean.TRUE : Boolean.FALSE);
        List<Map<String, Object>> list = projectMapper.adminListProjects(q);
        long total = projectMapper.adminCountProjects(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    public Map<String, Object> reviewQueue(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = projectMapper.listReviewQueue(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", projectMapper.countByStatus(1));
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 审核通过：status 1 → 2，写审核记录 + 建项目群 */
    @Transactional
    public Map<String, Object> approve(long projectVersionId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> version = projectMapper.findVersionById(projectVersionId);
        if (version == null) {
            throw BizException.notFound("版本不存在");
        }
        long projectId = ((Number) version.get("project_id")).longValue();
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        projectMapper.publishProject(projectId);
        insertReview(projectVersionId, adminId, "approve", reason);
        projectService.ensureProjectGroup(projectId, ((Number) project.get("author_id")).longValue(),
                String.valueOf(project.get("title")));
        notify(((Number) project.get("author_id")).longValue(), "review_result", "你的作品已通过审核",
                "《" + project.get("title") + "》已发布", "/project/" + projectId);
        audit(adminId, "approve_project", "project", projectId, "通过审核，版本 " + version.get("version_no"), ip);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("status", 2);
        return data;
    }

    /** 审核驳回：直接删除被驳回的作品（软删 DB + 物理删除服务器代码目录），不保留审核记录 */
    @Transactional
    public Map<String, Object> reject(long projectVersionId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> version = projectMapper.findVersionById(projectVersionId);
        if (version == null) {
            throw BizException.notFound("版本不存在");
        }
        long projectId = ((Number) version.get("project_id")).longValue();
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("作品不存在或已删除");
        }
        String r = (reason == null || reason.trim().isEmpty()) ? "不符合投稿规范" : reason.trim();
        // 2026-11-04：改为软驳回 —— 只置 status=4 并记录理由，保留源码文件；
        // 作者修改/重传后可再次提交（重新进入 status=1 待审核）。
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("reason", r);
        projectMapper.rejectProject(p);
        notify(((Number) project.get("author_id")).longValue(), "review_result", "你的作品未通过审核",
                "《" + project.get("title") + "》未通过审核，原因：" + r
                        + "。可在「我的作品」修改或重新上传后再次提交。", "/me/projects");
        audit(adminId, "reject_project", "project", projectId,
                "驳回（保留文件，可重发）：《" + project.get("title") + "》原因：" + r, ip);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("status", 4);
        data.put("deleted", false);
        data.put("reject_reason", r);
        return data;
    }

    @Transactional
    public Map<String, Object> down(long projectId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        requireProject(projectId);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("reason", reason == null ? "管理员下架" : reason);
        projectMapper.downProject(p);
        Map<String, Object> version = projectMapper.findLatestVersion(projectId);
        if (version != null) {
            insertReview(((Number) version.get("project_version_id")).longValue(), adminId, "down", reason);
        }
        audit(adminId, "down_project", "project", projectId, "下架：" + p.get("reason"), ip);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("status", 3);
        return data;
    }

    @Transactional
    public Map<String, Object> restore(long projectId, String ip) {
        long adminId = AuthContext.requireAdmin();
        requireProject(projectId);
        projectMapper.restoreProject(projectId);
        audit(adminId, "restore_project", "project", projectId, "恢复上架", ip);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("status", 2);
        return data;
    }

    /** 删除：草稿=软删（保留文件，可「撤销删除」）；其他=软删 DB + 物理删目录（不可撤销） */
    @Transactional
    public Map<String, Object> delete(long projectId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> project = requireProject(projectId);
        int status = ((Number) project.get("status")).intValue();
        boolean draft = status == 0;
        String r = (reason == null || reason.trim().isEmpty()) ? "违规删除" : reason.trim();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("reason", r);
        projectMapper.softDeleteProject(p);
        Path dir = storageService.projectRootDir(projectId);
        boolean existed = Files.exists(dir);
        boolean storageRemoved = false;
        // 2026-11-04：草稿删除保留源文件，支持「撤销删除」；非草稿仍物理清理
        if (!draft) {
            storageService.deleteRecursively(dir);
            storageRemoved = existed;
        }
        audit(adminId, "delete_project", "project", projectId,
                (draft ? "删除草稿（可撤销，文件保留）：《" : "删除项目（物理清目录）：《")
                        + project.get("title") + "》原因：" + r + "，目录 " + dir
                        + (draft ? "" : (existed ? "（已删除）" : "（不存在）")), ip);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("deleted", true);
        data.put("undoable", draft);
        data.put("storage_removed", storageRemoved);
        data.put("storage_exists", Files.exists(dir));
        return data;
    }

    /** 撤销删除：仅草稿（status=0 且 is_deleted=1）可恢复，文件若仍在则完整恢复（2026-11-04） */
    @Transactional
    public Map<String, Object> undelete(long projectId, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        if (((Number) project.get("is_deleted")).intValue() == 0) {
            throw BizException.badRequest("该项目未被删除，无需撤销");
        }
        int status = ((Number) project.get("status")).intValue();
        if (status != 0) {
            throw BizException.badRequest("只有草稿状态的作品可以撤销删除（当前状态：" + status + "）");
        }
        projectMapper.undeleteProject(projectId);
        Path dir = storageService.projectRootDir(projectId);
        boolean filesAlive = Files.exists(dir);
        audit(adminId, "undelete_project", "project", projectId,
                "撤销删除草稿：《" + project.get("title") + "》，源目录" + (filesAlive ? "仍在" : "已被历史清理"), ip);
        HotCache.invalidate("projects:");
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("status", status);
        data.put("is_deleted", 0);
        data.put("files_alive", filesAlive);
        data.put("message", filesAlive ? "已撤销删除，草稿恢复" : "已撤销删除，但源文件已被清理，需重新上传");
        return data;
    }

    /** 管理端删除任意评论 */
    @Transactional
    public Map<String, Object> deleteComment(long commentId, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> comment = commentMapper.findById(commentId);
        if (comment == null) {
            throw BizException.notFound("评论不存在");
        }
        if (((Number) comment.get("is_deleted")).intValue() == 1) {
            throw BizException.badRequest("评论已删除");
        }
        commentMapper.softDelete(commentId);
        Map<String, Object> counter = new LinkedHashMap<>();
        counter.put("project_id", ((Number) comment.get("project_id")).longValue());
        counter.put("delta", -1);
        projectMapper.incCommentCount(counter);
        audit(adminId, "delete_comment", "comment", commentId, "删除评论", ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comment_id", commentId);
        data.put("deleted", true);
        return data;
    }

    public List<Map<String, Object>> listComments(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("project_id", Params.lng(query, "project_id"));
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        return commentMapper.adminListComments(q);
    }

    // ---------------- 开发者申请 ----------------

    public Map<String, Object> devApplications(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("apply_status", Params.integer(query, "status", null));
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = userMapper.listDevApplications(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", userMapper.countDevApplications(q));
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    @Transactional
    public Map<String, Object> approveDeveloper(long developerProfileId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> apply = userMapper.findDevApplicationById(developerProfileId);
        if (apply == null) {
            throw BizException.notFound("申请不存在");
        }
        long uid = ((Number) apply.get("user_id")).longValue();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("developer_profile_id", developerProfileId);
        p.put("apply_status", 1);
        p.put("reviewer_id", adminId);
        p.put("reason", reason == null ? "审核通过" : reason);
        userMapper.updateDevProfileStatus(p);
        Map<String, Object> s = new LinkedHashMap<>();
        s.put("user_id", uid);
        s.put("status", 1);
        userMapper.updateStatus(s);
        notify(uid, "system", "开发者申请已通过", "你现在可以登录并投稿了", "/login");
        audit(adminId, "approve_developer", "developer_profile", developerProfileId, "通过开发者申请", ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", uid);
        data.put("apply_status", 1);
        data.put("user_status", 1);
        return data;
    }

    @Transactional
    public Map<String, Object> rejectDeveloper(long developerProfileId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> apply = userMapper.findDevApplicationById(developerProfileId);
        if (apply == null) {
            throw BizException.notFound("申请不存在");
        }
        long uid = ((Number) apply.get("user_id")).longValue();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("developer_profile_id", developerProfileId);
        p.put("apply_status", 2);
        p.put("reviewer_id", adminId);
        p.put("reason", reason == null ? "不符合开发者申请条件" : reason);
        userMapper.updateDevProfileStatus(p);
        audit(adminId, "reject_developer", "developer_profile", developerProfileId, "驳回开发者申请", ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", uid);
        data.put("apply_status", 2);
        return data;
    }

    // ---------------- 举报 ----------------

    public Map<String, Object> reports(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("status", Params.integer(query, "status", null));
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = reportMapper.adminList(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", reportMapper.adminCount(q));
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /**
     * 处理举报：action=resolve（成立，给举报人加正反馈积分）/ reject（驳回）。
     * 处理结果写 notification 通知举报人。
     */
    @Transactional
    public Map<String, Object> handleReport(long reportId, String action, String result, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> report = reportMapper.findById(reportId);
        if (report == null) {
            throw BizException.notFound("举报不存在");
        }
        if (((Number) report.get("status")).intValue() != 0) {
            throw BizException.badRequest("该举报已处理");
        }
        boolean resolve = !"reject".equalsIgnoreCase(action);
        int points = resolve ? reportRewardPoints : 0;
        long reporterId = ((Number) report.get("reporter_id")).longValue();

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("report_id", reportId);
        p.put("status", resolve ? 1 : 2);
        p.put("handle_result", result == null ? (resolve ? "举报成立，已处理" : "经核实举报不成立") : result);
        p.put("handler_id", adminId);
        p.put("reward_points", points);
        reportMapper.handle(p);

        if (resolve && points > 0) {
            Map<String, Object> rp = new LinkedHashMap<>();
            rp.put("user_id", reporterId);
            rp.put("points", points);
            userMapper.addRewardPoints(rp);
        }
        notify(reporterId, "report_handled", "你的举报已处理",
                String.valueOf(p.get("handle_result")) + (points > 0 ? "（+" + points + " 正反馈积分）" : ""),
                "/me/reports");
        audit(adminId, "handle_report", "report", reportId, String.valueOf(p.get("handle_result")), ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("report_id", reportId);
        data.put("status", p.get("status"));
        data.put("reward_points", points);
        return data;
    }

    // ---------------- 找回密码 / 注销 ----------------

    public Map<String, Object> passwordResets(Map<String, Object> query) {
        AuthContext.requireAdmin();
        int page = Params.page(query);
        int size = Params.size(query, 20, 100);
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("status", Params.integer(query, "status", null));
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> list = passwordResetMapper.listApplies(q);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", passwordResetMapper.countApplies(q));
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 重置密码：生成一次性临时密码（24 小时有效，首次登录强制改密），旧 token 立即失效 */
    @Transactional
    public Map<String, Object> resetPassword(long userId, Long applyId, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> user = userMapper.findAuthById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        String temp = randomPassword(8);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("user_id", userId);
        p.put("password_hash", encoder.encode(temp));
        userMapper.resetPassword(p);
        if (applyId != null) {
            Map<String, Object> h = new LinkedHashMap<>();
            h.put("apply_id", applyId);
            h.put("temp_password", temp);
            h.put("handled_by", adminId);
            passwordResetMapper.markHandled(h);
        }
        notify(userId, "system", "管理员已重置你的密码",
                "请在 24 小时内用临时密码登录并立即修改密码", "/login");
        audit(adminId, "reset_password", "user", userId, "重置密码（临时密码 24h 有效）", ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("qq_account", user.get("qq_account"));
        data.put("temp_password", temp);
        data.put("must_change_password", true);
        data.put("expires_in_hours", 24);
        return data;
    }

    /** 驳回找回密码申请：保留申请记录（status=2 + 驳回理由），有对应用户时通知他 */
    @Transactional
    public Map<String, Object> rejectPasswordReset(long applyId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> apply = passwordResetMapper.findApply(applyId);
        if (apply == null) {
            throw BizException.notFound("申请不存在");
        }
        int st = apply.get("status") == null ? 0 : ((Number) apply.get("status")).intValue();
        if (st != 0) {
            throw BizException.badRequest("该申请已处理，不能重复操作");
        }
        String r = (reason == null || reason.trim().isEmpty()) ? "信息不足，无法核实身份" : reason.trim();
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("apply_id", applyId);
        p.put("reject_reason", r);
        p.put("handled_by", adminId);
        passwordResetMapper.markRejected(p);
        String qq = apply.get("qq_account") == null ? null : String.valueOf(apply.get("qq_account"));
        Map<String, Object> applicant = qq == null ? null : userMapper.findByQq(qq);
        if (applicant != null && applicant.get("user_id") != null) {
            long uid = ((Number) applicant.get("user_id")).longValue();
            notify(uid, "system", "密码找回申请未通过",
                    "原因：" + r + "。请补充能证明账号归属的信息后重新提交。", "/forgot");
        }
        audit(adminId, "reject_password_reset", "password_reset_apply", applyId,
                "驳回找回密码申请：" + r, ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("apply_id", applyId);
        data.put("status", 2);
        data.put("reject_reason", r);
        return data;
    }

    /** 注销账号：status=3 软删；作品保留，作者名回退「用户已注销」 */
    @Transactional
    public Map<String, Object> cancelAccount(long userId, String reason, String ip) {
        long adminId = AuthContext.requireAdmin();
        if (userId == adminId) {
            throw BizException.badRequest("不能注销当前登录的管理员账号");
        }
        Map<String, Object> user = userMapper.findAuthById(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("user_id", userId);
        p.put("cancel_reason", reason == null ? "管理员注销" : reason);
        userMapper.cancelUser(p);
        audit(adminId, "cancel_account", "user", userId, "注销账号：" + p.get("cancel_reason"), ip);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("status", 3);
        data.put("message", "账号已注销；作品保留，昵称展示为「用户已注销」");
        return data;
    }

    // ---------------- 平台统计 / 存储路径 ----------------

    public Map<String, Object> platformStats() {
        AuthContext.requireAdmin();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("users_total", userMapper.countDevelopers() + userMapper.countAdmins());
        data.put("developers", userMapper.countDevelopers());
        data.put("admins", userMapper.countAdmins());
        data.put("cancelled_users", userMapper.countCancelled());
        data.put("projects_total", projectMapper.countActive());
        data.put("projects_published", projectMapper.countByStatus(2));
        data.put("projects_pending", projectMapper.countByStatus(1));
        data.put("projects_down", projectMapper.countByStatus(3));
        data.put("projects_rejected", projectMapper.countByStatus(4));
        data.put("views_today", viewMapper.todayViewsTotal());
        data.put("reports_pending", reportMapper.countPending());
        Map<String, Object> pendingDev = new LinkedHashMap<>();
        pendingDev.put("apply_status", 0);
        data.put("dev_applies_pending", userMapper.countDevApplications(pendingDev));
        data.put("storage_root", storageService.getRoot().toString());
        return data;
    }

    /** 服务器绝对存储路径（仅管理员，写审计） */
    public Map<String, Object> projectStorage(long projectId, String ip) {
        long adminId = AuthContext.requireAdmin();
        Map<String, Object> project = requireProject(projectId);
        List<Map<String, Object>> files = projectMapper.listFilesByProject(projectId);
        List<Map<String, Object>> pathList = new ArrayList<>();
        long totalSize = 0L;
        for (Map<String, Object> f : files) {
            Map<String, Object> x = new LinkedHashMap<>();
            x.put("project_file_id", f.get("project_file_id"));
            x.put("version_no", f.get("version_no"));
            x.put("file_type", f.get("file_type"));
            x.put("file_name", f.get("file_name"));
            x.put("storage_path", f.get("storage_path"));
            String buildPath = (String) f.get("build_path");
            x.put("build_path", buildPath);
            long size = 0L;
            Path p = storageService.resolveStored(String.valueOf(f.get("storage_path")));
            try {
                if (p != null && Files.exists(p)) {
                    size = Files.size(p);
                }
            } catch (IOException ignored) {
                // 忽略
            }
            x.put("size_bytes", size);
            totalSize += size;
            pathList.add(x);
        }
        Path root = storageService.projectRootDir(projectId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("title", project.get("title"));
        data.put("storage_root", storageService.getRoot().toString());
        data.put("project_dir", root.toString());
        data.put("project_dir_exists", Files.exists(root));
        data.put("files", pathList);
        data.put("total_size_bytes", totalSize);
        audit(adminId, "view_storage", "project", projectId, "查看服务器存储路径", ip);
        return data;
    }

    public List<Map<String, Object>> logs(Map<String, Object> query) {
        AuthContext.requireAdmin();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("action", Params.str(query, "action"));
        q.put("target_type", Params.str(query, "target_type"));
        q.put("limit", Params.size(query, 50, 200));
        return logMapper.listLogs(q);
    }

    /** 管理员：用户列表（按 QQ 号找人、重置密码用） */
    public List<Map<String, Object>> listUsers(Map<String, Object> query) {
        AuthContext.requireAdmin();
        return userMapper.adminListUsers(query);
    }

    // ---------------- 内部工具 ----------------

    private Map<String, Object> requireProject(long projectId) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null) {
            throw BizException.notFound("项目不存在");
        }
        return project;
    }

    private void insertReview(long versionId, long adminId, String action, String reason) {
        Map<String, Object> r = new LinkedHashMap<>();
        r.put("project_version_id", versionId);
        r.put("admin_id", adminId);
        r.put("action", action);
        r.put("reason", reason);
        projectMapper.insertReview(r);
    }

    private void notify(long userId, String type, String title, String content, String link) {
        Map<String, Object> n = new LinkedHashMap<>();
        n.put("user_id", userId);
        n.put("type", type);
        n.put("title", title);
        n.put("content", content);
        n.put("link", link);
        notificationMapper.insert(n);
    }

    private void audit(long adminId, String action, String targetType, Long targetId, String detail, String ip) {
        Map<String, Object> log = new LinkedHashMap<>();
        log.put("admin_id", adminId);
        log.put("action", action);
        log.put("target_type", targetType);
        log.put("target_id", targetId);
        log.put("detail", detail);
        log.put("ip", ip);
        logMapper.insertLog(log);
    }

    private String randomPassword(int len) {
        StringBuilder sb = new StringBuilder();
        for (int i = 0; i < len; i++) {
            sb.append(TEMP_ALPHABET.charAt(RANDOM.nextInt(TEMP_ALPHABET.length())));
        }
        return sb.toString();
    }
}
