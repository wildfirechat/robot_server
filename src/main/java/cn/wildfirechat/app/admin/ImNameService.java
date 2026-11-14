package cn.wildfirechat.app.admin;

import cn.wildfirechat.app.RobotConfig;
import cn.wildfirechat.app.im.RobotServiceProvider;
import cn.wildfirechat.common.ErrorCode;
import cn.wildfirechat.pojos.InputOutputUserInfo;
import cn.wildfirechat.pojos.PojoGroupInfo;
import cn.wildfirechat.sdk.RobotService;
import cn.wildfirechat.sdk.model.IMResult;
import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;
import org.springframework.util.StringUtils;

import java.util.Map;
import java.util.Optional;
import java.util.concurrent.ConcurrentHashMap;

/**
 * 管理后台展示名称解析：只展示用户/群名称，不把内部 ID 暴露到页面。
 */
@Service
public class ImNameService {
    private static final Logger LOG = LoggerFactory.getLogger(ImNameService.class);
    private static final long CACHE_TTL_MS = 10 * 60 * 1000L;
    private static final int MAX_CACHE_SIZE = 5000;

    private final RobotServiceProvider robotServiceProvider;
    private final RobotConfig robotConfig;
    private final Map<String, CachedProfile> profileCache = new ConcurrentHashMap<>();
    private final Map<String, CachedName> groupCache = new ConcurrentHashMap<>();

    public ImNameService(RobotServiceProvider robotServiceProvider, RobotConfig robotConfig) {
        this.robotServiceProvider = robotServiceProvider;
        this.robotConfig = robotConfig;
    }

    public String userName(String userId) {
        return profile(userId).getName();
    }

    public UserProfile profile(String userId) {
        if (!StringUtils.hasText(userId)) {
            return UserProfile.unknown("");
        }
        if (userId.equals(robotConfig.getIm_id())) {
            String name = StringUtils.hasText(robotConfig.getIm_name()) ? robotConfig.getIm_name() : "机器人";
            return new UserProfile(userId, name, null, null);
        }
        CachedProfile cached = getProfile(userId);
        if (cached != null) {
            return cached.profile;
        }
        UserProfile profile = loadProfile(userId);
        if (profile.isResolved()) {
            putProfile(userId, profile);
        }
        return profile;
    }

    /**
     * 手机号精确查询，使用 IM 的 /robot/user_info 接口；不用于模糊搜索。
     */
    public Optional<UserProfile> findByMobile(String mobile) {
        if (!StringUtils.hasText(mobile)) {
            return Optional.empty();
        }
        try {
            RobotService service = robotServiceProvider.get();
            IMResult<InputOutputUserInfo> result = service.getUserInfoByMobile(mobile.trim());
            if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS
                    && result.getResult() != null) {
                UserProfile profile = mapProfile(result.getResult());
                if (profile.isResolved()) {
                    putProfile(profile.id, profile);
                    return Optional.of(profile);
                }
            }
        } catch (Exception e) {
            LOG.debug("按手机号查询用户失败, mobile:{}, error:{}", mobile, e.getMessage());
        }
        return Optional.empty();
    }

    public String groupName(String groupId) {
        if (!StringUtils.hasText(groupId)) {
            return "未知群组";
        }
        CachedName cached = getGroup(groupId);
        if (cached != null) {
            return cached.name;
        }
        String name = "未知群组";
        try {
            RobotService service = robotServiceProvider.get();
            IMResult<PojoGroupInfo> result = service.getGroupInfo(groupId);
            if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS
                    && result.getResult() != null && StringUtils.hasText(result.getResult().getName())) {
                name = result.getResult().getName().trim();
            }
        } catch (Exception e) {
            LOG.debug("获取群名称失败, groupId:{}, error:{}", groupId, e.getMessage());
        }
        if (!"未知群组".equals(name)) {
            putGroup(groupId, name);
        }
        return name;
    }

    private UserProfile loadProfile(String userId) {
        try {
            RobotService service = robotServiceProvider.get();
            IMResult<InputOutputUserInfo> result = service.getUserInfo(userId);
            if (result != null && result.getErrorCode() == ErrorCode.ERROR_CODE_SUCCESS
                    && result.getResult() != null) {
                return mapProfile(result.getResult());
            }
        } catch (Exception e) {
            LOG.debug("获取用户资料失败, userId:{}, error:{}", userId, e.getMessage());
        }
        return UserProfile.unknown(userId);
    }

    private static UserProfile mapProfile(InputOutputUserInfo user) {
        String name = StringUtils.hasText(user.getDisplayName()) ? user.getDisplayName().trim()
                : StringUtils.hasText(user.getName()) ? user.getName().trim() : "未知用户";
        return new UserProfile(user.getUserId(), name, user.getMobile(), user.getEmail());
    }

    private CachedProfile getProfile(String id) {
        CachedProfile value = profileCache.get(id);
        if (value == null || System.currentTimeMillis() - value.cachedAt > CACHE_TTL_MS) {
            return null;
        }
        return value;
    }

    private void putProfile(String id, UserProfile profile) {
        if (profileCache.size() > MAX_CACHE_SIZE) {
            profileCache.clear();
        }
        profileCache.put(id, new CachedProfile(profile, System.currentTimeMillis()));
    }

    private CachedName getGroup(String id) {
        CachedName value = groupCache.get(id);
        if (value == null || System.currentTimeMillis() - value.cachedAt > CACHE_TTL_MS) {
            return null;
        }
        return value;
    }

    private void putGroup(String id, String name) {
        if (groupCache.size() > MAX_CACHE_SIZE) {
            groupCache.clear();
        }
        groupCache.put(id, new CachedName(name, System.currentTimeMillis()));
    }

    public static class UserProfile {
        private final String id;
        private final String name;
        private final String mobile;
        private final String email;

        public UserProfile(String id, String name, String mobile, String email) {
            this.id = id;
            this.name = name;
            this.mobile = mobile;
            this.email = email;
        }

        public static UserProfile unknown(String id) {
            return new UserProfile(id, "未知用户", null, null);
        }

        public boolean isResolved() {
            return !"未知用户".equals(name);
        }

        public String getId() {
            return id;
        }

        public String getName() {
            return name;
        }

        public String getMobile() {
            return mobile;
        }

        public String getEmail() {
            return email;
        }
    }

    private static class CachedProfile {
        private final UserProfile profile;
        private final long cachedAt;

        private CachedProfile(UserProfile profile, long cachedAt) {
            this.profile = profile;
            this.cachedAt = cachedAt;
        }

    }

    private static class CachedName {
        private final String name;
        private final long cachedAt;

        private CachedName(String name, long cachedAt) {
            this.name = name;
            this.cachedAt = cachedAt;
        }

    }
}
