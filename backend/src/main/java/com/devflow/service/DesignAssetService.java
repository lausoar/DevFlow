package com.devflow.service;

import com.devflow.entity.DesignAsset;
import com.devflow.entity.DesignAssetVersion;
import com.devflow.entity.Requirement;
import org.springframework.security.core.Authentication;

import java.util.List;

/**
 * 设计稿服务：关联、评审流转、版本与通知触发。
 *
 * 抽成服务层的理由：状态流转要带 CAS 前置条件、版本要与唯一键配合去重、
 * 通知文案需要需求/任务/负责人上下文，这些都不该散落在 controller 里。
 */
public interface DesignAssetService {

    String TRIGGER_DESIGN_CHANGED = "DESIGN_CHANGED";
    String TRIGGER_DESIGN_APPROVED = "DESIGN_APPROVED";

    List<DesignAsset> listByRequirement(String requirementId);

    DesignAsset create(String requirementId, DesignAsset asset, String userId);

    DesignAsset update(String id, DesignAsset patch, String userId);

    /**
     * 评审状态流转。并发下只有一个调用能成功，只有成功者才触发通知。
     */
    DesignAsset changeStatus(String id, String target, String userId);

    /**
     * 提交新版本：版本号由服务端分配，重复版本返回 409 而不是静默覆盖。
     */
    DesignAssetVersion addVersion(String id, DesignAssetVersion version, String userId);

    List<DesignAssetVersion> versions(String id);

    void delete(String id, String userId);

    /** 需求当前的设计状态（由 primary 设计稿推导；无主稿时取最新一张） */
    String resolveRequirementDesignStatus(String requirementId);
}
