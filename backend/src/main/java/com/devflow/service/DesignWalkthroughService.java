package com.devflow.service;

import com.devflow.entity.DesignWalkthrough;
import com.devflow.entity.DesignWalkthroughItem;

import java.util.List;
import java.util.Map;

/**
 * UI 走查单：视觉验收从"群里说一声"变成可计数、可留痕、可闭环的清单。
 *
 * 每个走查项四态（PENDING / PASS / FAIL / NA），失败项可一键转缺陷并回填关联；
 * 走查单锁定设计稿版本，事后能回答"当时按哪一版验收的"。
 */
public interface DesignWalkthroughService {

    /** 需求下的走查单（含进度） */
    List<Map<String, Object>> listByRequirement(String requirementId);

    /** 走查单详情：走查项清单 + 进度统计 */
    Map<String, Object> detail(String walkthroughId);

    DesignWalkthrough create(DesignWalkthrough walkthrough, String operatorId);

    /**
     * 批量添加走查项（通常一次贴一批检查点）。
     * 若走查单关联了设计稿，会同时锁定其当前版本作为验收基线。
     */
    int addItems(String walkthroughId, List<String> titles, String operatorId);

    DesignWalkthroughItem setResult(String itemId, String result, String note,
                                    String screenshotUrl, String operatorId);

    void deleteItem(String itemId, String operatorId);

    void close(String walkthroughId, String operatorId);

    /**
     * 失败走查项一键转缺陷：拼装走查项 + 设计稿版本 + 截图，
     * 回填 bug_id，并做幂等保护（已转过的直接返回原缺陷）。
     */
    String convertItemToBug(String itemId, String operatorId, String extraNote);
}
