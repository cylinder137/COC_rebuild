package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.mapper.InteractionMapper;
import uk.collectionofcreations.coc.mapper.ProjectMapper;
import uk.collectionofcreations.coc.mapper.UserMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 互动业务：项目点赞 / 收藏、关注（全部要求登录，访客 401）。
 */
@Service
public class InteractionService {

    private final InteractionMapper interactionMapper;
    private final ProjectMapper projectMapper;
    private final UserMapper userMapper;

    public InteractionService(InteractionMapper interactionMapper, ProjectMapper projectMapper, UserMapper userMapper) {
        this.interactionMapper = interactionMapper;
        this.projectMapper = projectMapper;
        this.userMapper = userMapper;
    }

    @Transactional
    public Map<String, Object> toggleProjectLike(long projectId) {
        long uid = AuthContext.requireUserId();
        requireVisibleProject(projectId);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("user_id", uid);
        boolean liked;
        if (interactionMapper.findProjectLike(projectId, uid) != null) {
            interactionMapper.deleteProjectLike(projectId, uid);
            liked = false;
        } else {
            interactionMapper.insertProjectLike(p);
            liked = true;
        }
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("project_id", projectId);
        delta.put("delta", liked ? 1 : -1);
        projectMapper.incLikeCount(delta);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("liked", liked);
        data.put("like_count", interactionMapper.countProjectLikes(projectId));
        return data;
    }

    @Transactional
    public Map<String, Object> toggleProjectFavorite(long projectId) {
        long uid = AuthContext.requireUserId();
        requireVisibleProject(projectId);
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("project_id", projectId);
        p.put("user_id", uid);
        boolean favorited;
        if (interactionMapper.findProjectFavorite(projectId, uid) != null) {
            interactionMapper.deleteProjectFavorite(projectId, uid);
            favorited = false;
        } else {
            interactionMapper.insertProjectFavorite(p);
            favorited = true;
        }
        Map<String, Object> delta = new LinkedHashMap<>();
        delta.put("project_id", projectId);
        delta.put("delta", favorited ? 1 : -1);
        projectMapper.incFavoriteCount(delta);
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("project_id", projectId);
        data.put("favorited", favorited);
        data.put("favorite_count", interactionMapper.countProjectFavorites(projectId));
        return data;
    }

    @Transactional
    public Map<String, Object> toggleFollow(long followeeId) {
        long uid = AuthContext.requireUserId();
        if (uid == followeeId) {
            throw BizException.badRequest("不能关注自己");
        }
        if (userMapper.findPublicProfile(followeeId) == null) {
            throw BizException.notFound("用户不存在");
        }
        Map<String, Object> p = new LinkedHashMap<>();
        p.put("follower_id", uid);
        p.put("followee_id", followeeId);
        boolean following;
        if (interactionMapper.findFollow(uid, followeeId) != null) {
            interactionMapper.deleteFollow(uid, followeeId);
            following = false;
        } else {
            interactionMapper.insertFollow(p);
            following = true;
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", followeeId);
        data.put("following", following);
        data.put("followers", interactionMapper.countFollowers(followeeId));
        return data;
    }

    private void requireVisibleProject(long projectId) {
        Map<String, Object> project = projectMapper.findProjectById(projectId);
        if (project == null || ((Number) project.get("is_deleted")).intValue() == 1) {
            throw BizException.notFound("项目不存在");
        }
        int status = ((Number) project.get("status")).intValue();
        if (status != 2) {
            Long me = AuthContext.userId();
            boolean owner = me != null && me == ((Number) project.get("author_id")).longValue();
            if (!owner && !AuthContext.isAdmin()) {
                throw BizException.badRequest("项目尚未发布，暂不支持互动");
            }
        }
    }
}
