package com.superfriend.superfriend.service;

import com.superfriend.superfriend.config.FilePathConfig;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.stereotype.Service;

import javax.annotation.PreDestroy;
import java.util.HashMap;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;

/**
 * Skills 沙箱会话管理器
 * 为每个技能创建专用会话，管理会话生命周期
 */
@Slf4j
@Service
public class SkillSandboxSessionManager {

    private final BashSandboxService bashSandboxService;
    private final FilePathConfig filePathConfig;

    /**
     * 技能会话缓存
     * Key: skillName
     * Value: sessionId
     */
    private final Map<String, String> skillSessionCache = new ConcurrentHashMap<>();

    /**
     * 会话创建时间
     * Key: sessionId
     * Value: 创建时间戳
     */
    private final Map<String, Long> sessionCreationTime = new ConcurrentHashMap<>();

    /**
     * 会话超时时间（毫秒）- 默认 30 分钟
     */
    private static final long SESSION_TIMEOUT_MS = 30 * 60 * 1000;

    @Autowired
    public SkillSandboxSessionManager(BashSandboxService bashSandboxService,
                                       FilePathConfig filePathConfig) {
        this.bashSandboxService = bashSandboxService;
        this.filePathConfig = filePathConfig;
    }

    /**
     * 获取或创建技能专用会话
     *
     * @param skillName 技能名称
     * @return 会话 ID
     */
    public String getOrCreateSession(String skillName) {
        return getOrCreateSession(skillName, null);
    }

    /**
     * 获取或创建技能专用会话
     *
     * @param skillName 技能名称
     * @param workingDirectory 工作目录（可选，默认使用技能目录）
     * @return 会话 ID
     */
    public String getOrCreateSession(String skillName, String workingDirectory) {
        // 检查缓存中是否有有效会话
        String cachedSessionId = skillSessionCache.get(skillName);
        if (cachedSessionId != null) {
            // 检查会话是否过期
            Long creationTime = sessionCreationTime.get(cachedSessionId);
            if (creationTime != null &&
                (System.currentTimeMillis() - creationTime) < SESSION_TIMEOUT_MS) {
                log.debug("使用缓存的技能会话: skill={}, sessionId={}", skillName, cachedSessionId);
                return cachedSessionId;
            } else {
                // 会话过期，清理缓存
                log.info("技能会话已过期，重新创建: skill={}, sessionId={}", skillName, cachedSessionId);
                skillSessionCache.remove(skillName);
                sessionCreationTime.remove(cachedSessionId);
            }
        }

        // 创建新会话
        String effectiveWorkingDir = workingDirectory;
        if (effectiveWorkingDir == null || effectiveWorkingDir.isEmpty()) {
            effectiveWorkingDir = getSkillWorkingDirectory(skillName);
        }

        try {
            BashSandboxService.SessionInfo sessionInfo = bashSandboxService
                .createSession(effectiveWorkingDir, null, "skill-" + skillName)
                .join();

            if (sessionInfo != null && sessionInfo.getSessionId() != null) {
                String sessionId = sessionInfo.getSessionId();
                skillSessionCache.put(skillName, sessionId);
                sessionCreationTime.put(sessionId, System.currentTimeMillis());

                log.info("创建技能沙箱会话: skill={}, sessionId={}, workingDir={}",
                    skillName, sessionId, effectiveWorkingDir);

                return sessionId;
            } else {
                log.warn("创建技能会话返回空结果: skill={}", skillName);
                return null;
            }
        } catch (Exception e) {
            log.error("创建技能沙箱会话失败: skill={} - {}", skillName, e.getMessage());
            return null;
        }
    }

    /**
     * 获取技能工作目录
     *
     * @param skillName 技能名称
     * @return 工作目录路径
     */
    public String getSkillWorkingDirectory(String skillName) {
        return filePathConfig.getSkillsDir() + "/" + skillName;
    }

    /**
     * 获取技能会话的工作区路径
     *
     * @param skillName 技能名称
     * @return 工作区路径
     */
    public String getWorkspacePath(String skillName) {
        String sessionId = skillSessionCache.get(skillName);
        if (sessionId != null) {
            // 沙箱会话的工作区在沙箱目录下
            return filePathConfig.getPlatformSandboxDir() + "/sessions/" + sessionId + "/workspace";
        }
        // 如果没有会话，返回技能目录
        return getSkillWorkingDirectory(skillName);
    }

    /**
     * 关闭技能会话
     *
     * @param skillName 技能名称
     * @return 是否成功
     */
    public boolean closeSession(String skillName) {
        return closeSession(skillName, true);
    }

    /**
     * 关闭技能会话
     *
     * @param skillName 技能名称
     * @param cleanup   是否清理会话目录
     * @return 是否成功
     */
    public boolean closeSession(String skillName, boolean cleanup) {
        String sessionId = skillSessionCache.remove(skillName);
        if (sessionId == null) {
            log.debug("技能会话不存在: skill={}", skillName);
            return true;
        }

        sessionCreationTime.remove(sessionId);

        try {
            Boolean result = bashSandboxService.closeSession(sessionId, cleanup).join();
            log.info("关闭技能沙箱会话: skill={}, sessionId={}, cleanup={}, result={}",
                skillName, sessionId, cleanup, result);
            return result != null && result;
        } catch (Exception e) {
            log.error("关闭技能会话失败: skill={}, sessionId={} - {}", skillName, sessionId, e.getMessage());
            return false;
        }
    }

    /**
     * 检查技能会话是否存在
     *
     * @param skillName 技能名称
     * @return 是否存在
     */
    public boolean hasSession(String skillName) {
        String sessionId = skillSessionCache.get(skillName);
        if (sessionId == null) {
            return false;
        }

        // 检查是否过期
        Long creationTime = sessionCreationTime.get(sessionId);
        if (creationTime == null) {
            return false;
        }

        return (System.currentTimeMillis() - creationTime) < SESSION_TIMEOUT_MS;
    }

    /**
     * 获取技能会话 ID
     *
     * @param skillName 技能名称
     * @return 会话 ID，如果不存在返回 null
     */
    public String getSessionId(String skillName) {
        return skillSessionCache.get(skillName);
    }

    /**
     * 清理所有过期会话
     */
    public void cleanupExpiredSessions() {
        long now = System.currentTimeMillis();
        for (Map.Entry<String, String> entry : skillSessionCache.entrySet()) {
            String skillName = entry.getKey();
            String sessionId = entry.getValue();
            Long creationTime = sessionCreationTime.get(sessionId);

            if (creationTime != null && (now - creationTime) >= SESSION_TIMEOUT_MS) {
                log.info("清理过期技能会话: skill={}, sessionId={}", skillName, sessionId);
                closeSession(skillName, true);
            }
        }
    }

    /**
     * 获取活跃会话数量
     */
    public int getActiveSessionCount() {
        return skillSessionCache.size();
    }

    /**
     * 获取所有活跃技能会话
     */
    public Map<String, String> getActiveSessions() {
        return new HashMap<>(skillSessionCache);
    }

    /**
     * 应用关闭时清理所有会话
     */
    @PreDestroy
    public void onShutdown() {
        log.info("应用关闭，清理所有技能沙箱会话...");
        for (String skillName : skillSessionCache.keySet()) {
            try {
                closeSession(skillName, true);
            } catch (Exception e) {
                log.warn("清理技能会话失败: skill={} - {}", skillName, e.getMessage());
            }
        }
        log.info("技能沙箱会话清理完成");
    }
}
