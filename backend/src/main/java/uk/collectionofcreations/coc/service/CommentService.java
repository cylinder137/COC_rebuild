package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.common.Params;
import uk.collectionofcreations.coc.mapper.CommentMapper;
import uk.collectionofcreations.coc.mapper.ProjectMapper;

import java.util.ArrayList;
import java.util.HashSet;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 评论业务：评论树（楼中楼一层）、发评论、评论点赞。
 */
@Service
public class CommentService {

    private final CommentMapper commentMapper;
    private final ProjectMapper projectMapper;

    public CommentService(CommentMapper commentMapper, ProjectMapper projectMapper) {
        this.commentMapper = commentMapper;
        this.projectMapper = projectMapper;
    }

    /** 评论树：根评论分页，每条带 replies（一层） */
    public Map<String, Object> tree(long projectId, int page, int size) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        long authorId = ((Number) project.get("author_id")).longValue();
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("project_id", projectId);
        q.put("limit", size);
        q.put("offset", Params.offset(page, size));
        List<Map<String, Object>> roots = commentMapper.listRootComments(q);
        long total = commentMapper.countRootComments(projectId);

        List<Long> rootIds = new ArrayList<>();
        for (Map<String, Object> r : roots) {
            rootIds.add(((Number) r.get("comment_id")).longValue());
        }
        Map<Long, List<Map<String, Object>>> repliesMap = new LinkedHashMap<>();
        if (!rootIds.isEmpty()) {
            Map<String, Object> rq = new LinkedHashMap<>();
            rq.put("root_ids", rootIds);
            for (Map<String, Object> rep : commentMapper.listReplies(rq)) {
                Long rootId = ((Number) rep.get("root_comment_id")).longValue();
                repliesMap.computeIfAbsent(rootId, k -> new ArrayList<>()).add(shapeComment(rep, authorId, null));
            }
        }
        Set<Long> liked = likedIds(rootIds);

        List<Map<String, Object>> list = new ArrayList<>();
        for (Map<String, Object> r : roots) {
            Long cid = ((Number) r.get("comment_id")).longValue();
            Map<String, Object> x = shapeComment(r, authorId, liked.contains(cid));
            List<Map<String, Object>> replies = repliesMap.getOrDefault(cid, new ArrayList<>());
            for (Map<String, Object> rep : replies) {
                Long rid = ((Number) rep.get("comment_id")).longValue();
                rep.put("liked", liked.contains(rid));
            }
            x.put("replies", replies);
            x.put("reply_count", replies.size());
            list.add(x);
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("list", list);
        data.put("total", total);
        data.put("page", page);
        data.put("size", size);
        return data;
    }

    /** 发表评论（必须登录；仅已发布项目可评论） */
    @Transactional
    public Map<String, Object> create(Map<String, Object> body) {
        long uid = AuthContext.requireUserId();
        long projectId = Params.requireLng(body, "project_id", "project_id");
        String content = Params.requireStr(body, "content", "评论内容");
        if (content.length() > 1000) {
            throw BizException.badRequest("评论内容过长（<=1000 字）");
        }
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        int status = ((Number) project.get("status")).intValue();
        long authorId = ((Number) project.get("author_id")).longValue();
        if (status != 2 && uid != authorId) {
            throw BizException.badRequest("该项目尚未发布，不能评论");
        }

        Long parentId = Params.lng(body, "parent_comment_id");
        Long rootId = null;
        if (parentId != null) {
            Map<String, Object> parent = commentMapper.findById(parentId);
            if (parent == null || ((Number) parent.get("is_deleted")).intValue() == 1) {
                throw BizException.badRequest("要回复的评论不存在");
            }
            if (((Number) parent.get("project_id")).longValue() != projectId) {
                throw BizException.badRequest("评论不属于该项目");
            }
            Object pr = parent.get("root_comment_id");
            rootId = pr != null ? ((Number) pr).longValue() : parentId;
        }

        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("user_id", uid);
        p.put("parent_comment_id", parentId);
        p.put("root_comment_id", rootId);
        p.put("content", content);
        commentMapper.insertComment(p);
        long commentId = ((Number) p.get("comment_id")).longValue();

        Map<String, Object> counter = new LinkedHashMap<>();
        counter.put("project_id", projectId);
        counter.put("delta", 1);
        projectMapper.incCommentCount(counter);

        Map<String, Object> saved = commentMapper.findDetailById(commentId);
        Map<String, Object> data = shapeComment(saved, authorId, false);
        data.put("is_author", uid == authorId);
        return data;
    }

    /** 评论点赞开关（必须登录） */
    @Transactional
    public Map<String, Object> toggleLike(long commentId) {
        long uid = AuthContext.requireUserId();
        Map<String, Object> comment = commentMapper.findById(commentId);
        if (comment == null || ((Number) comment.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("评论不存在");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("comment_id", commentId);
        p.put("user_id", uid);
        boolean liked;
        if (commentMapper.findLike(commentId, uid) != null) {
            commentMapper.deleteLike(commentId, uid);
            liked = false;
        } else {
            commentMapper.insertLike(p);
            liked = true;
        }
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("comment_id", commentId);
        delta.put("delta", liked ? 1 : -1);
        commentMapper.incLikeCount(delta);
        Map<String, Object> after = commentMapper.findById(commentId);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("comment_id", commentId);
        data.put("liked", liked);
        data.put("like_count", after.get("like_count"));
        return data;
    }

    private Set<Long> likedIds(List<Long> commentIds) {
        Long uid = AuthContext.userId();
        if (uid == null || commentIds.isEmpty()) {
            return Set.of();
        }
        Map<String, Object> q = new LinkedHashMap<>();
        q.put("user_id", uid);
        q.put("comment_ids", commentIds);
        return new HashSet<>(commentMapper.likedCommentIds(q));
    }

    private Map<String, Object> shapeComment(Map<String, Object> row, long projectAuthorId, Boolean liked) {
        Map<String, Object> x = new LinkedHashMap<>();
        x.put("comment_id", row.get("comment_id"));
        x.put("project_id", row.get("project_id"));
        x.put("content", row.get("content"));
        x.put("like_count", row.get("like_count"));
        x.put("created_at", row.get("created_at"));
        x.put("parent_comment_id", row.get("parent_comment_id"));
        x.put("root_comment_id", row.get("root_comment_id"));
        Map<String, Object> u = new LinkedHashMap<>();
        u.put("user_id", row.get("user_id"));
        u.put("nickname", row.get("user_nickname"));
        u.put("avatar_path", row.get("user_avatar"));
        x.put("user", u);
        long uid = ((Number) row.get("user_id")).longValue();
        x.put("is_author", uid == projectAuthorId);
        x.put("liked", liked != null && liked);
        x.put("is_mine", AuthContext.userId() != null && AuthContext.userId() == uid);
        return x;
    }
}
