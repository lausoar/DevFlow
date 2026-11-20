package com.devflow.service;

import com.devflow.entity.Objective;

import java.util.List;
import java.util.Map;

/**
 * 企业目标（OKR）服务。
 *
 * 写权限沿用企业级 enterprise:manage（与项目集一致，不新增权限点——
 * 新权限码不会自动进入既有企业的权限快照，见 PermissionServiceImpl.codesFor）。
 */
public interface ObjectiveService {

    List<Objective> listByEnterprise(String enterpriseId);

    Objective get(String objectiveId);

    Objective create(String enterpriseId, Objective objective, String operatorId);

    Objective update(String objectiveId, Objective patch, String operatorId);

    void delete(String objectiveId, String operatorId);

    /** 目标详情：项目级投入/产出汇总 + 需求分布 */
    Map<String, Object> rollup(String objectiveId);

    /** 把项目挂到目标（objectiveId 为空表示移出）；校验同企业 */
    void assignProject(String objectiveId, String projectId, String operatorId);

    /** 校验目标属于指定企业，返回目标（需求/项目关联时复用） */
    Objective requireInEnterprise(String objectiveId, String enterpriseId);
}
