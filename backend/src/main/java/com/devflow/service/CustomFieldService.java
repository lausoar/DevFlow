package com.devflow.service;

import com.devflow.entity.CustomField;

import java.util.List;
import java.util.Map;

/**
 * 自定义字段。
 *
 * 设计取舍（重要）：字段定义按"项目 + 问题类型"配置，值以 **JSON 快照**存在
 * custom_field_value.value 里，而不是 EAV 宽表或动态列。
 * 理由：
 * - 动态加列需要 DDL，且客户字段数量不可控；
 * - EAV 逐字段查询会带来 N 次往返；
 * - 一次取回整份 JSON，前端表单直接渲染，代价是**不能按自定义字段做服务端排序/聚合**。
 *   需要按自定义字段过滤时走 QueryController 的白名单解析（本批未接入自定义字段）。
 */
public interface CustomFieldService {

    /** 项目某对象类型下的字段定义（启用 + 停用，按 sort） */
    List<CustomField> list(String projectId, String issueType);

    /** 新建字段定义（fieldKey 项目内唯一） */
    CustomField create(String projectId, String issueType, CustomField field);

    /** 更新字段定义（type 不可改，避免已有值语义漂移） */
    CustomField update(String fieldId, CustomField patch);

    /** 删除字段定义（连带清理其值） */
    void delete(String fieldId);

    /** 读取某个工作项的自定义字段值：fieldKey → value */
    Map<String, Object> valuesOf(String issueId, String issueType);

    /**
     * 保存工作项的自定义字段值（全量覆盖式）。
     * 会按字段定义做校验：必填、类型、SELECT 选项是否合法。
     */
    void saveValues(String issueId, String issueType, String projectId, Map<String, Object> values);

    /** 删除某工作项的全部自定义字段值（工作项删除时调用） */
    void deleteValuesOf(String issueId);

    /** 字段所属项目（鉴权用；字段不存在时 404） */
    String projectOf(String fieldId);

    /** 工作项所属项目（鉴权用；工作项不存在时 404，不泄露存在性） */
    String projectOfIssue(String issueType, String issueId);
}
