package com.superfriend.superfriend.service;

import com.superfriend.superfriend.entity.Skill;
import com.superfriend.superfriend.entity.SkillScript;
import com.superfriend.superfriend.entity.SkillExecution;
import com.superfriend.superfriend.entity.UserSkill;
import com.superfriend.superfriend.event.UserSkillChangedEvent;
import com.superfriend.superfriend.mapper.SkillMapper;
import com.superfriend.superfriend.mapper.SkillScriptMapper;
import com.superfriend.superfriend.mapper.SkillExecutionMapper;
import com.superfriend.superfriend.mapper.UserSkillMapper;
import lombok.extern.slf4j.Slf4j;
import org.springframework.beans.factory.annotation.Autowired;
import org.springframework.context.ApplicationEventPublisher;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;

import java.util.List;
import java.util.Map;
import java.util.stream.Collectors;

@Slf4j
@Service
public class SkillService {
    
    @Autowired
    private SkillMapper skillMapper;
    
    @Autowired
    private UserSkillMapper userSkillMapper;
    
    @Autowired
    private SkillScriptMapper skillScriptMapper;
    
    @Autowired
    private SkillExecutionMapper skillExecutionMapper;

    @Autowired
    private ApplicationEventPublisher eventPublisher;

    // ==================== 技能管理 ====================
    
    public Skill findById(Long id) {
        return skillMapper.findById(id);
    }
    
    public Skill findByName(String name) {
        return skillMapper.findByName(name);
    }
    
    public List<Skill> findAll() {
        return skillMapper.findAllEnabled();
    }
    
    public List<Skill> findByScope(Integer scope) {
        return skillMapper.findByScope(scope);
    }
    
    public List<Skill> findByScopeIn(List<Integer> scopes) {
        return skillMapper.findByScopeIn(scopes);
    }
    
    public List<Skill> findByCategory(String category) {
        return skillMapper.findByCategory(category);
    }
    
    public List<Skill> findSelectedSkills() {
        return skillMapper.findSelected();
    }
    
    public List<Skill> search(String keyword) {
        return skillMapper.search(keyword);
    }
    
    @Transactional
    public Skill createSkill(Skill skill) {
        skillMapper.insert(skill);
        
        if (skill.getAllowedTools() != null) {
            skill.setAllowedTools(skill.getAllowedTools().stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList()));
        }
        
        if (skill.getTags() != null) {
            skill.setTags(skill.getTags().stream()
                .filter(s -> s != null && !s.isEmpty())
                .collect(Collectors.toList()));
        }
        
        return skill;
    }
    
    @Transactional
    public Skill createUserSkill(Skill skill, Long userId) {
        skill.setScope(3);
        skillMapper.insert(skill);
        
        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkillId(skill.getId());
        userSkill.setIsEnabled(1);
        userSkill.setIsSelected(false);
        userSkillMapper.insert(userSkill);
        
        log.info("创建用户技能成功: skillId={}, skillName={}, userId={}", skill.getId(), skill.getName(), userId);
        
        return skill;
    }
    
    @Transactional
    public Skill updateSkill(Skill skill) {
        skillMapper.update(skill);
        return skill;
    }
    
    @Transactional
    public void deleteSkill(Long id) {
        skillMapper.deleteById(id);
    }
    
    @Transactional
    public void deleteSkillCascade(Long id) {
        skillScriptMapper.deleteBySkillId(id);
        skillExecutionMapper.deleteBySkillId(id);
        userSkillMapper.deleteBySkillId(id);
        skillMapper.deleteById(id);
        log.info("级联删除技能完成: id={}", id);
    }
    
    @Transactional
    public void updateSelectionStatus(Long id, Boolean isSelected) {
        skillMapper.updateSelectionStatus(id, isSelected);
    }
    
    @Transactional
    public void updateUserSkillSelectionStatus(Long userId, Long skillId, Boolean isSelected) {
        UserSkill userSkill = userSkillMapper.findByUserIdAndSkillId(userId, skillId);
        if (userSkill == null) {
            userSkill = new UserSkill();
            userSkill.setUserId(userId);
            userSkill.setSkillId(skillId);
            userSkill.setIsEnabled(1);
            userSkill.setIsSelected(isSelected);
            userSkillMapper.insert(userSkill);
            log.info("创建用户技能关联并设置选择状态: userId={}, skillId={}, isSelected={}", userId, skillId, isSelected);
        } else {
            userSkillMapper.updateSelectionStatus(userId, skillId, isSelected);
            log.info("更新用户技能选择状态: userId={}, skillId={}, isSelected={}", userId, skillId, isSelected);
        }
        eventPublisher.publishEvent(new UserSkillChangedEvent(userId, UserSkillChangedEvent.ChangeType.SELECTION_CHANGED));
    }
    
    public List<UserSkill> findSelectedUserSkillsByUserId(Long userId) {
        return userSkillMapper.findSelectedByUserId(userId);
    }
    
    public List<Long> findSelectedSkillIdsByUserId(Long userId) {
        List<UserSkill> selectedUserSkills = userSkillMapper.findSelectedByUserId(userId);
        return selectedUserSkills.stream()
            .map(UserSkill::getSkillId)
            .collect(Collectors.toList());
    }
    
    // ==================== 用户技能管理 ====================
    
    public UserSkill addUserSkill(Long userId, Long skillId) {
        UserSkill userSkill = new UserSkill();
        userSkill.setUserId(userId);
        userSkill.setSkillId(skillId);
        userSkill.setIsEnabled(1);
        userSkillMapper.insert(userSkill);
        eventPublisher.publishEvent(new UserSkillChangedEvent(userId, UserSkillChangedEvent.ChangeType.ADDED));
        return userSkill;
    }
    
    public UserSkill updateUserSkill(UserSkill userSkill) {
        userSkillMapper.update(userSkill);
        eventPublisher.publishEvent(new UserSkillChangedEvent(userSkill.getUserId(), UserSkillChangedEvent.ChangeType.UPDATED));
        return userSkill;
    }
    
    public void removeUserSkill(Long userId, Long skillId) {
        userSkillMapper.deleteByUserIdAndSkillId(userId, skillId);
        eventPublisher.publishEvent(new UserSkillChangedEvent(userId, UserSkillChangedEvent.ChangeType.REMOVED));
    }
    
    public void enableUserSkill(Long userId, Long skillId) {
        userSkillMapper.enableSkill(userId, skillId);
        eventPublisher.publishEvent(new UserSkillChangedEvent(userId, UserSkillChangedEvent.ChangeType.ENABLED_CHANGED));
    }
    
    public void disableUserSkill(Long userId, Long skillId) {
        userSkillMapper.disableSkill(userId, skillId);
        eventPublisher.publishEvent(new UserSkillChangedEvent(userId, UserSkillChangedEvent.ChangeType.ENABLED_CHANGED));
    }
    
    public List<UserSkill> findUserSkillsByUserId(Long userId) {
        return userSkillMapper.findByUserId(userId);
    }
    
    public List<UserSkill> findEnabledUserSkillsByUserId(Long userId) {
        return userSkillMapper.findEnabledByUserId(userId);
    }
    
    public UserSkill findUserSkill(Long userId, Long skillId) {
        return userSkillMapper.findByUserIdAndSkillId(userId, skillId);
    }
    
    // ==================== 技能脚本管理 ====================
    
    public SkillScript addSkillScript(SkillScript script) {
        skillScriptMapper.insert(script);
        return script;
    }
    
    public SkillScript updateSkillScript(SkillScript script) {
        skillScriptMapper.update(script);
        return script;
    }
    
    public void deleteSkillScript(Long id) {
        skillScriptMapper.deleteById(id);
    }
    
    public void deleteSkillScriptsBySkillId(Long skillId) {
        skillScriptMapper.deleteBySkillId(skillId);
    }
    
    public List<SkillScript> findScriptsBySkillId(Long skillId) {
        return skillScriptMapper.findBySkillId(skillId);
    }
    
    public SkillScript findScriptBySkillIdAndType(Long skillId, String scriptType) {
        return skillScriptMapper.findBySkillIdAndType(skillId, scriptType);
    }
    
    @Transactional
    public void updateScript(SkillScript script) {
        if (script.getId() != null && script.getId() > 0) {
            skillScriptMapper.update(script);
        } else {
            skillScriptMapper.insert(script);
        }
    }
    
    // ==================== 执行历史管理 ====================
<<<<<<< HEAD

=======
    
    public SkillExecution addExecution(SkillExecution execution) {
        skillExecutionMapper.insert(execution);
        return execution;
    }
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    public void deleteExecution(Long id) {
        skillExecutionMapper.deleteById(id);
    }
    
    public void deleteExecutionsBySkillId(Long skillId) {
        skillExecutionMapper.deleteBySkillId(skillId);
    }
    
    public void deleteExecutionsByUserId(Long userId) {
        skillExecutionMapper.deleteByUserId(userId);
    }
<<<<<<< HEAD

=======
    
    public List<SkillExecution> findExecutionsBySkillId(Long skillId) {
        return skillExecutionMapper.findBySkillId(skillId);
    }
    
    public List<SkillExecution> findExecutionsByUserId(Long userId) {
        return skillExecutionMapper.findByUserId(userId);
    }
    
    public List<SkillExecution> findExecutionsBySessionId(String sessionId) {
        return skillExecutionMapper.findBySessionId(sessionId);
    }
    
>>>>>>> 60f6cf48ec8b86bef14fa75f9b08190db2685fc2
    // ==================== 统计信息 ====================
    
    public void updateSkillStatistics(Long skillId, Double successRate, Integer averageExecutionTime) {
        skillMapper.updateStatistics(skillId, successRate, averageExecutionTime);
    }
    
    public List<Skill> getEnabledSkills() {
        return skillMapper.findAllEnabled();
    }
    
    public List<Skill> getSystemSkills() {
        return skillMapper.findByScope(1);
    }
    
    public List<Skill> getProjectSkills() {
        return skillMapper.findByScope(2);
    }
    
    public List<Skill> getUserSkills() {
        return skillMapper.findByScope(3);
    }
}
