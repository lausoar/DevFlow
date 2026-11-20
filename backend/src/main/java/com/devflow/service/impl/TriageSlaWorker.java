package com.devflow.service.impl;

import com.devflow.entity.TriageItem;
import com.devflow.mapper.TriageItemMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.scheduling.annotation.Scheduled;
import org.springframework.stereotype.Component;

import java.time.LocalDateTime;
import java.util.List;

/**
 * 分诊 SLA 扫描：到期未处理的条目提醒项目负责人。
 * 与文档索引发件箱同一模式——原子占用提醒名额，多实例不会重复打扰。
 */
@Slf4j
@Component
@RequiredArgsConstructor
public class TriageSlaWorker {

    private static final int BATCH = 100;

    private final TriageItemMapper triageItemMapper;
    private final TriageServiceImpl triageService;

    @Scheduled(
            fixedDelayString = "${devflow.triage.sla-scan-ms:60000}",
            initialDelayString = "${devflow.triage.sla-initial-delay-ms:30000}")
    public void scan() {
        List<TriageItem> due = triageItemMapper.selectDue(LocalDateTime.now(), BATCH);
        for (TriageItem item : due) {
            try {
                triageService.notifySlaBreach(item);
            } catch (Exception e) {
                log.error("分诊超时提醒失败: item={}, 原因={}", item.getId(), e.getMessage());
            }
        }
    }
}
