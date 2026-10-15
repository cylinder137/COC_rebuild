package uk.collectionofcreations.coc.service;

import org.springframework.stereotype.Service;
import uk.collectionofcreations.coc.auth.AuthContext;
import uk.collectionofcreations.coc.common.BizException;
import uk.collectionofcreations.coc.mapper.InteractionMapper;
import uk.collectionofcreations.coc.mapper.UserMapper;

import java.util.LinkedHashMap;
import java.util.Map;

/**
 * 个人主页业务。
 * <p>已注销账号（status=3）：昵称回退「用户已注销」，主页给占位（不 404）。
 */
@Service
public class UserService {

    private final UserMapper userMapper;
    private final InteractionMapper interactionMapper;
    private final ProjectService projectService;

    public UserService(UserMapper userMapper, InteractionMapper interactionMapper, ProjectService projectService) {
        this.userMapper = userMapper;
        this.interactionMapper = interactionMapper;
        this.projectService = projectService;
    }

    public Map<String, Object> profile(long userId) {
        Map<String, Object> user = userMapper.findPublicProfile(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", user.get("user_id"));
        data.put("nickname", user.get("display_name"));
        data.put("avatar_path", ((Number) user.get("is_cancelled")).intValue() == 1 ? null : user.get("avatar_path"));
        data.put("is_cancelled", ((Number) user.get("is_cancelled")).intValue() == 1);
        data.put("type", user.get("type"));
        data.put("created_at", user.get("created_at"));
        data.put("followers", interactionMapper.countFollowers(userId));
        data.put("following_count", interactionMapper.countFollowing(userId));
        data.put("projects", projectService.publishedByAuthor(userId, 30));
        data.put("project_count", ((java.util.List<?>) data.get("projects")).size());
        Long me = AuthContext.userId();
        data.put("following", me != null && interactionMapper.findFollow(me, userId) != null);
        return data;
    }

    public Map<String, Object> followStatus(long userId) {
        Map<String, Object> user = userMapper.findPublicProfile(userId);
        if (user == null) {
            throw BizException.notFound("用户不存在");
        }
        Long me = AuthContext.userId();
        Map<String, Object> data = new LinkedHashMap<>();
        data.put("user_id", userId);
        data.put("following", me != null && interactionMapper.findFollow(me, userId) != null);
        data.put("followers", interactionMapper.countFollowers(userId));
        return data;
    }
}
