package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.entity.Subscription;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.SubscriptionMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.service.SubscriptionService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;

import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * 订阅式通知实现。
 *
 * 两条规则：
 * 1. 被指派 / 被提及 / 自己创建的对象**自动关注**（否则用户会漏掉与自己直接相关的事）；
 * 2. 显式取消关注会删掉关注行；但 shouldNotify 还会看"当前是否仍与该对象有自动关系"，
 *    所以被重新指派时通知会恢复 —— 取消是强意图，但"又被指派了"说明关系重建了。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class SubscriptionServiceImpl implements SubscriptionService {

    private final SubscriptionMapper subscriptionMapper;
    private final TaskMapper taskMapper;
    private final BugMapper bugMapper;
    private final RequirementMapper requirementMapper;
    private final com.devflow.service.ProjectAccessService projectAccessService;

    @Override
    public void subscribe(String userId, String targetType, String targetId, String projectId) {
        subscribeInternal(userId, targetType, targetId, projectId, 0);
    }

    @Override
    public void autoSubscribe(String userId, String targetType, String targetId, String projectId) {
        subscribeInternal(userId, targetType, targetId, projectId, 1);
    }

    private void subscribeInternal(String userId, String targetType, String targetId,
                                   String projectId, int auto) {
        if (userId == null || targetType == null || targetId == null) {
            return;
        }
        Long exists = subscriptionMapper.selectCount(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId)
                .eq(Subscription::getTargetType, targetType)
                .eq(Subscription::getTargetId, targetId));
        if (exists != null && exists > 0) {
            return;
        }
        Subscription s = new Subscription();
        s.setUserId(userId);
        s.setTargetType(targetType);
        s.setTargetId(targetId);
        s.setProjectId(projectId);
        s.setAuto(auto);
        subscriptionMapper.insert(s);
    }

    @Override
    public void unsubscribe(String userId, String targetType, String targetId) {
        subscriptionMapper.delete(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId)
                .eq(Subscription::getTargetType, targetType)
                .eq(Subscription::getTargetId, targetId));
    }

    @Override
    public boolean isSubscribed(String userId, String targetType, String targetId) {
        Long n = subscriptionMapper.selectCount(new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId)
                .eq(Subscription::getTargetType, targetType)
                .eq(Subscription::getTargetId, targetId));
        return n != null && n > 0;
    }

    @Override
    public List<Map<String, Object>> listMine(String userId, String targetType) {
        LambdaQueryWrapper<Subscription> wrapper = new LambdaQueryWrapper<Subscription>()
                .eq(Subscription::getUserId, userId)
                .orderByDesc(Subscription::getCreatedAt)
                .last("LIMIT 200");
        if (targetType != null && !targetType.isBlank()) {
            wrapper.eq(Subscription::getTargetType, targetType.toUpperCase());
        }
        List<Subscription> rows = subscriptionMapper.selectList(wrapper);
        // 被移出项目后，订阅关系本身还在，但不能再泄露原项目的标题与 ID
        java.util.Set<String> visibleProjectIds = new java.util.HashSet<>(
                projectAccessService.visibleProjectIds(userId));
        List<Map<String, Object>> out = new ArrayList<>();
        for (Subscription s : rows) {
            if (s.getProjectId() != null && !visibleProjectIds.contains(s.getProjectId())) {
                continue;
            }
            Map<String, Object> m = new LinkedHashMap<>();
            m.put("targetType", s.getTargetType());
            m.put("targetId", s.getTargetId());
            m.put("projectId", s.getProjectId());
            m.put("auto", s.getAuto());
            m.put("createdAt", s.getCreatedAt());
            m.put("title", titleOf(s.getTargetType(), s.getTargetId()));
            out.add(m);
        }
        return out;
    }

    @Override
    public List<String> subscribersOf(String targetType, String targetId) {
        return subscriptionMapper.selectList(new LambdaQueryWrapper<Subscription>()
                        .eq(Subscription::getTargetType, targetType)
                        .eq(Subscription::getTargetId, targetId))
                .stream().map(Subscription::getUserId).distinct().toList();
    }

    @Override
    public boolean shouldNotify(String userId, String targetType, String targetId) {
        return isSubscribed(userId, targetType, targetId)
                || isAutoRelated(userId, targetType, targetId);
    }

    /** 是否与该对象存在"自动关注"关系（当前是创建人或被指派人） */
    private boolean isAutoRelated(String userId, String targetType, String targetId) {
        try {
            return switch (targetType.toUpperCase()) {
                case "TASK" -> {
                    var t = taskMapper.selectById(targetId);
                    yield t != null && (userId.equals(t.getAssigneeId()) || userId.equals(t.getReporterId()));
                }
                case "BUG" -> {
                    var b = bugMapper.selectById(targetId);
                    yield b != null && (userId.equals(b.getAssigneeId()) || userId.equals(b.getReporterId()));
                }
                case "REQUIREMENT" -> {
                    var r = requirementMapper.selectById(targetId);
                    yield r != null && (userId.equals(r.getRequesterId()) || userId.equals(r.getAssigneeId()));
                }
                default -> false;
            };
        } catch (Exception e) {
            return false;
        }
    }

    private String titleOf(String targetType, String targetId) {
        try {
            return switch (targetType.toUpperCase()) {
                case "TASK" -> {
                    var t = taskMapper.selectById(targetId);
                    yield t == null ? "(已删除)" : t.getTitle();
                }
                case "BUG" -> {
                    var b = bugMapper.selectById(targetId);
                    yield b == null ? "(已删除)" : b.getTitle();
                }
                case "REQUIREMENT" -> {
                    var r = requirementMapper.selectById(targetId);
                    yield r == null ? "(已删除)" : r.getTitle();
                }
                default -> targetId;
            };
        } catch (Exception e) {
            return targetId;
        }
    }
}
