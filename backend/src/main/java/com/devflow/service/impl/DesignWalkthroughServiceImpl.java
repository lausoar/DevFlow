package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.Bug;
import com.devflow.entity.DesignAsset;
import com.devflow.entity.DesignWalkthrough;
import com.devflow.entity.DesignWalkthroughItem;
import com.devflow.entity.Requirement;
import com.devflow.mapper.BugMapper;
import com.devflow.mapper.DesignAssetMapper;
import com.devflow.mapper.DesignWalkthroughItemMapper;
import com.devflow.mapper.DesignWalkthroughMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.service.DesignWalkthroughService;
import com.devflow.service.WorkflowService;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDateTime;
import java.util.ArrayList;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;

/**
 * UI 走查单实现。
 *
 * 设计要点：
 * - 走查单锁定设计稿版本（design_version），避免"设计改了三版之后再来验收"的口径混乱；
 * - 失败项转缺陷是幂等的：同一项重复转不会产生重复缺陷；
 * - 转出的缺陷描述里带设计稿版本与截图链接，开发不用回头问是哪一版。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class DesignWalkthroughServiceImpl implements DesignWalkthroughService {

    private final DesignWalkthroughMapper walkthroughMapper;
    private final DesignWalkthroughItemMapper itemMapper;
    private final RequirementMapper requirementMapper;
    private final DesignAssetMapper designAssetMapper;
    private final BugMapper bugMapper;
    private final WorkflowService workflowService;

    private static final List<String> RESULTS = List.of("PENDING", "PASS", "FAIL", "NA");

    @Override
    public List<Map<String, Object>> listByRequirement(String requirementId) {
        List<DesignWalkthrough> list = walkthroughMapper.selectList(
                new LambdaQueryWrapper<DesignWalkthrough>()
                        .eq(DesignWalkthrough::getRequirementId, requirementId)
                        .orderByDesc(DesignWalkthrough::getCreatedAt));
        List<Map<String, Object>> out = new ArrayList<>();
        for (DesignWalkthrough w : list) {
            Map<String, Object> row = new LinkedHashMap<>();
            row.put("walkthrough", w);
            row.putAll(progress(w.getId()));
            out.add(row);
        }
        return out;
    }

    @Override
    public Map<String, Object> detail(String walkthroughId) {
        DesignWalkthrough w = require(walkthroughId);
        List<DesignWalkthroughItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DesignWalkthroughItem>()
                        .eq(DesignWalkthroughItem::getWalkthroughId, walkthroughId)
                        .orderByAsc(DesignWalkthroughItem::getSort)
                        .orderByAsc(DesignWalkthroughItem::getCreatedAt));
        Map<String, Object> result = new LinkedHashMap<>();
        result.put("walkthrough", w);
        result.put("items", items);
        result.putAll(progress(walkthroughId));
        return result;
    }

    @Override
    public DesignWalkthrough create(DesignWalkthrough walkthrough, String operatorId) {
        if (!StringUtils.hasText(walkthrough.getRequirementId())) {
            throw new BusinessException(400, "走查单必须关联需求");
        }
        Requirement requirement = requirementMapper.selectById(walkthrough.getRequirementId());
        if (requirement == null) {
            throw new BusinessException(404, "需求不存在或已被删除");
        }
        DesignWalkthrough w = new DesignWalkthrough();
        w.setProjectId(requirement.getProjectId());
        w.setRequirementId(requirement.getId());
        w.setTitle(StringUtils.hasText(walkthrough.getTitle())
                ? walkthrough.getTitle()
                : requirement.getTitle() + " · UI 走查");
        w.setStatus("OPEN");
        w.setCreatedBy(operatorId);
        w.setDeleted(0);

        // 锁定设计稿与版本：验收依据不能是"最新版"
        String assetId = walkthrough.getDesignAssetId();
        if (StringUtils.hasText(assetId)) {
            DesignAsset asset = designAssetMapper.selectById(assetId);
            if (asset == null || !requirement.getProjectId().equals(asset.getProjectId())) {
                throw new BusinessException(400, "设计稿不存在或不属于该项目");
            }
            w.setDesignAssetId(asset.getId());
            w.setDesignVersion(asset.getCurrentVersion());
        } else {
            // 未指定时自动取该需求的主设计稿
            DesignAsset primary = designAssetMapper.selectList(new LambdaQueryWrapper<DesignAsset>()
                            .eq(DesignAsset::getRequirementId, requirement.getId())
                            .eq(DesignAsset::getIsPrimary, 1)
                            .last("LIMIT 1"))
                    .stream().findFirst().orElse(null);
            if (primary != null) {
                w.setDesignAssetId(primary.getId());
                w.setDesignVersion(primary.getCurrentVersion());
            }
        }
        walkthroughMapper.insert(w);
        return w;
    }

    @Override
    @Transactional
    public int addItems(String walkthroughId, List<String> titles, String operatorId) {
        require(walkthroughId);
        if (titles == null || titles.isEmpty()) {
            return 0;
        }
        Long existing = itemMapper.selectCount(new LambdaQueryWrapper<DesignWalkthroughItem>()
                .eq(DesignWalkthroughItem::getWalkthroughId, walkthroughId));
        int sort = existing == null ? 0 : existing.intValue();
        int added = 0;
        for (String raw : titles) {
            if (!StringUtils.hasText(raw)) {
                continue;
            }
            DesignWalkthroughItem item = new DesignWalkthroughItem();
            item.setWalkthroughId(walkthroughId);
            item.setTitle(raw.trim());
            item.setResult("PENDING");
            item.setSort(sort++);
            itemMapper.insert(item);
            added++;
        }
        return added;
    }

    @Override
    public DesignWalkthroughItem setResult(String itemId, String result, String note,
                                           String screenshotUrl, String operatorId) {
        DesignWalkthroughItem item = requireItem(itemId);
        String target = result == null ? "" : result.toUpperCase();
        if (!RESULTS.contains(target)) {
            throw new BusinessException(400, "无效的走查结果");
        }
        DesignWalkthroughItem patch = new DesignWalkthroughItem();
        patch.setId(itemId);
        patch.setResult(target);
        patch.setNote(note);
        patch.setScreenshotUrl(screenshotUrl);
        patch.setCheckedBy(operatorId);
        patch.setCheckedAt(LocalDateTime.now());
        itemMapper.updateById(patch);
        return itemMapper.selectById(itemId);
    }

    @Override
    public void deleteItem(String itemId, String operatorId) {
        requireItem(itemId);
        itemMapper.deleteById(itemId);
    }

    @Override
    public void close(String walkthroughId, String operatorId) {
        require(walkthroughId);
        DesignWalkthrough patch = new DesignWalkthrough();
        patch.setId(walkthroughId);
        patch.setStatus("CLOSED");
        walkthroughMapper.updateById(patch);
    }

    @Override
    @Transactional
    public String convertItemToBug(String itemId, String operatorId, String extraNote) {
        DesignWalkthroughItem item = requireItem(itemId);
        if (!"FAIL".equals(item.getResult())) {
            throw new BusinessException(400, "只有失败的走查项可以转缺陷");
        }
        // 幂等：已转过就直接返回，避免同一项刷出一堆重复缺陷
        if (StringUtils.hasText(item.getBugId())) {
            return item.getBugId();
        }

        DesignWalkthrough w = require(item.getWalkthroughId());
        Requirement requirement = requirementMapper.selectById(w.getRequirementId());

        StringBuilder desc = new StringBuilder();
        if (StringUtils.hasText(extraNote)) {
            desc.append("走查说明：").append(extraNote).append("\n\n");
        }
        desc.append("【走查项】").append(item.getTitle()).append("\n");
        if (StringUtils.hasText(item.getNote())) {
            desc.append("【走查备注】").append(item.getNote()).append("\n");
        }
        if (StringUtils.hasText(w.getDesignAssetId())) {
            DesignAsset asset = designAssetMapper.selectById(w.getDesignAssetId());
            desc.append("【设计稿】").append(asset != null ? asset.getTitle() : w.getDesignAssetId())
                    .append("（版本 v").append(w.getDesignVersion() == null ? "?" : w.getDesignVersion())
                    .append("）\n");
            if (asset != null && StringUtils.hasText(asset.getEmbedUrl())) {
                desc.append("【设计稿链接】").append(asset.getEmbedUrl()).append("\n");
            }
        }
        if (StringUtils.hasText(item.getScreenshotUrl())) {
            desc.append("【截图】").append(item.getScreenshotUrl()).append("\n");
        }
        if (requirement != null) {
            desc.append("【关联需求】").append(requirement.getTitle()).append("\n");
        }
        desc.append("\n（由 UI 走查单「").append(w.getTitle()).append("」自动生成）");

        Bug bug = new Bug();
        bug.setProjectId(w.getProjectId());
        bug.setTitle("【UI 走查】" + item.getTitle());
        bug.setDescription(desc.toString());
        bug.setStatus(workflowService.initialStatus(w.getProjectId(), WorkflowService.TYPE_BUG));
        bug.setPriority("MEDIUM");
        bug.setSeverity("MINOR");
        bug.setReporterId(operatorId);
        if (requirement != null) {
            bug.setFixVersionId(requirement.getFixVersionId());
        }
        bugMapper.insert(bug);

        DesignWalkthroughItem patch = new DesignWalkthroughItem();
        patch.setId(itemId);
        patch.setBugId(bug.getId());
        itemMapper.updateById(patch);
        return bug.getId();
    }

    // ==================== 内部 ====================

    private DesignWalkthrough require(String walkthroughId) {
        DesignWalkthrough w = walkthroughMapper.selectById(walkthroughId);
        if (w == null) {
            throw new BusinessException(404, "走查单不存在");
        }
        return w;
    }

    private DesignWalkthroughItem requireItem(String itemId) {
        DesignWalkthroughItem item = itemMapper.selectById(itemId);
        if (item == null) {
            throw new BusinessException(404, "走查项不存在");
        }
        return item;
    }

    /** 进度：总数 / 已检查 / 通过 / 失败 / 不适用 / 通过率 */
    private Map<String, Object> progress(String walkthroughId) {
        List<DesignWalkthroughItem> items = itemMapper.selectList(
                new LambdaQueryWrapper<DesignWalkthroughItem>()
                        .eq(DesignWalkthroughItem::getWalkthroughId, walkthroughId));
        long total = items.size();
        long pass = items.stream().filter(i -> "PASS".equals(i.getResult())).count();
        long fail = items.stream().filter(i -> "FAIL".equals(i.getResult())).count();
        long na = items.stream().filter(i -> "NA".equals(i.getResult())).count();
        long pending = items.stream().filter(i -> "PENDING".equals(i.getResult())).count();
        long checked = total - pending;
        long applicable = total - na;

        Map<String, Object> m = new LinkedHashMap<>();
        m.put("total", total);
        m.put("checked", checked);
        m.put("pending", pending);
        m.put("pass", pass);
        m.put("fail", fail);
        m.put("na", na);
        m.put("passRate", applicable > 0 ? Math.round(pass * 1000.0 / applicable) / 10.0 : 0.0);
        m.put("convertedBugs", items.stream().filter(i -> StringUtils.hasText(i.getBugId())).count());
        return m;
    }
}
