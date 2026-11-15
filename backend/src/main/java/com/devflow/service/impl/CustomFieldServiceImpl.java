package com.devflow.service.impl;

import com.baomidou.mybatisplus.core.conditions.query.LambdaQueryWrapper;
import com.devflow.common.exception.BusinessException;
import com.devflow.entity.CustomField;
import com.devflow.entity.CustomFieldValue;
import com.devflow.mapper.CustomFieldMapper;
import com.devflow.mapper.CustomFieldValueMapper;
import com.devflow.service.CustomFieldService;
import com.fasterxml.jackson.core.type.TypeReference;
import com.fasterxml.jackson.databind.ObjectMapper;
import lombok.RequiredArgsConstructor;
import lombok.extern.slf4j.Slf4j;
import org.springframework.stereotype.Service;
import org.springframework.transaction.annotation.Transactional;
import org.springframework.util.StringUtils;

import java.time.LocalDate;
import java.util.ArrayList;
import java.util.HashMap;
import java.util.LinkedHashMap;
import java.util.List;
import java.util.Map;
import java.util.Set;

/**
 * 自定义字段实现。
 *
 * 校验集中在这里：字段类型、选项合法性、必填。
 * 前端会做同样的校验给即时反馈，但**服务端才是权威** —— 直接调接口绕不过去。
 */
@Slf4j
@Service
@RequiredArgsConstructor
public class CustomFieldServiceImpl implements CustomFieldService {

    private final CustomFieldMapper fieldMapper;
    private final CustomFieldValueMapper valueMapper;
    private final com.devflow.mapper.TaskMapper taskMapper;
    private final com.devflow.mapper.BugMapper bugMapper;
    private final com.devflow.mapper.RequirementMapper requirementMapper;
    private final ObjectMapper objectMapper;

    /** 支持的字段类型 */
    public static final Set<String> TYPES = Set.of(
            "TEXT", "TEXTAREA", "NUMBER", "DATE", "SELECT", "MULTI_SELECT", "USER", "CHECKBOX");

    @Override
    public List<CustomField> list(String projectId, String issueType) {
        return fieldMapper.selectList(new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getProjectId, projectId)
                .eq(CustomField::getIssueType, issueType)
                .orderByAsc(CustomField::getSort)
                .orderByAsc(CustomField::getCreatedAt));
    }

    @Override
    public CustomField create(String projectId, String issueType, CustomField field) {
        if (!StringUtils.hasText(field.getFieldKey()) || !StringUtils.hasText(field.getName())) {
            throw new BusinessException(400, "字段标识与名称不能为空");
        }
        if (!field.getFieldKey().matches("^[a-zA-Z][a-zA-Z0-9_]{0,59}$")) {
            throw new BusinessException(400, "字段标识需以字母开头，只允许字母/数字/下划线");
        }
        if (!TYPES.contains(field.getType())) {
            throw new BusinessException(400, "不支持的字段类型: " + field.getType());
        }
        if (("SELECT".equals(field.getType()) || "MULTI_SELECT".equals(field.getType()))
                && !StringUtils.hasText(field.getOptions())) {
            throw new BusinessException(400, "选择类字段必须提供选项");
        }
        Long exists = fieldMapper.selectCount(new LambdaQueryWrapper<CustomField>()
                .eq(CustomField::getProjectId, projectId)
                .eq(CustomField::getIssueType, issueType)
                .eq(CustomField::getFieldKey, field.getFieldKey()));
        if (exists != null && exists > 0) {
            throw new BusinessException(409, "该字段标识已存在");
        }

        field.setId(null);
        field.setProjectId(projectId);
        field.setIssueType(issueType);
        if (field.getRequired() == null) {
            field.setRequired(0);
        }
        if (field.getEnabled() == null) {
            field.setEnabled(1);
        }
        if (field.getSort() == null) {
            Long count = fieldMapper.selectCount(new LambdaQueryWrapper<CustomField>()
                    .eq(CustomField::getProjectId, projectId)
                    .eq(CustomField::getIssueType, issueType));
            field.setSort(count == null ? 0 : count.intValue());
        }
        field.setDeleted(0);
        fieldMapper.insert(field);
        return field;
    }

    @Override
    public CustomField update(String fieldId, CustomField patch) {
        CustomField existing = requireField(fieldId);
        // type 不可改：已有值的语义会漂移（比如 TEXT 改 SELECT，旧值全都不在选项里）
        if (patch.getType() != null && !patch.getType().equals(existing.getType())) {
            throw new BusinessException(400, "字段类型创建后不可修改");
        }
        patch.setId(fieldId);
        patch.setProjectId(existing.getProjectId());
        patch.setIssueType(existing.getIssueType());
        patch.setFieldKey(existing.getFieldKey());
        patch.setType(existing.getType());
        fieldMapper.updateById(patch);
        return fieldMapper.selectById(fieldId);
    }

    @Override
    @Transactional
    public void delete(String fieldId) {
        requireField(fieldId);
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getFieldId, fieldId));
        fieldMapper.deleteById(fieldId);
    }

    @Override
    public Map<String, Object> valuesOf(String issueId, String issueType) {
        List<CustomFieldValue> rows = valueMapper.selectList(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId)
                .eq(CustomFieldValue::getIssueType, issueType));
        if (rows.isEmpty()) {
            return Map.of();
        }
        Map<String, CustomField> defs = new LinkedHashMap<>();
        for (CustomField f : fieldMapper.selectBatchIds(rows.stream().map(CustomFieldValue::getFieldId).toList())) {
            defs.put(f.getId(), f);
        }
        Map<String, Object> out = new HashMap<>();
        for (CustomFieldValue row : rows) {
            CustomField def = defs.get(row.getFieldId());
            if (def == null) {
                continue;
            }
            out.put(def.getFieldKey(), decode(def, row.getValue()));
        }
        return out;
    }

    @Override
    @Transactional
    public void saveValues(String issueId, String issueType, String projectId, Map<String, Object> values) {
        List<CustomField> defs = list(projectId, issueType).stream()
                .filter(f -> Integer.valueOf(1).equals(f.getEnabled()))
                .toList();
        Map<String, CustomField> byKey = new LinkedHashMap<>();
        defs.forEach(f -> byKey.put(f.getFieldKey(), f));

        // 必填校验：只在"本次提交涉及该字段或整体保存"时校验。
        // 这里的语义是全量覆盖，因此对每个必填字段都要求有值。
        for (CustomField def : defs) {
            if (Integer.valueOf(1).equals(def.getRequired())) {
                Object v = values == null ? null : values.get(def.getFieldKey());
                if (v == null || (v instanceof String s && s.isBlank())
                        || (v instanceof List<?> l && l.isEmpty())) {
                    throw new BusinessException(400, "「" + def.getName() + "」为必填项");
                }
            }
        }

        if (values != null) {
            for (Map.Entry<String, Object> e : values.entrySet()) {
                CustomField def = byKey.get(e.getKey());
                if (def == null) {
                    // 未知字段直接拒绝：避免客户端塞任意 key 进来堆积垃圾数据
                    throw new BusinessException(400, "未知的自定义字段: " + e.getKey());
                }
                String encoded = encode(def, e.getValue());
                CustomFieldValue row = valueMapper.selectOne(new LambdaQueryWrapper<CustomFieldValue>()
                        .eq(CustomFieldValue::getFieldId, def.getId())
                        .eq(CustomFieldValue::getIssueId, issueId));
                if (row == null) {
                    row = new CustomFieldValue();
                    row.setFieldId(def.getId());
                    row.setIssueId(issueId);
                    row.setIssueType(issueType);
                    row.setProjectId(projectId);
                    row.setValue(encoded);
                    valueMapper.insert(row);
                } else {
                    row.setValue(encoded);
                    valueMapper.updateById(row);
                }
            }
        }
    }

    @Override
    public void deleteValuesOf(String issueId) {
        valueMapper.delete(new LambdaQueryWrapper<CustomFieldValue>()
                .eq(CustomFieldValue::getIssueId, issueId));
    }

    @Override
    public String projectOf(String fieldId) {
        return requireField(fieldId).getProjectId();
    }

    @Override
    public String projectOfIssue(String issueType, String issueId) {
        String projectId = switch (issueType) {
            case "BUG" -> {
                var bug = bugMapper.selectById(issueId);
                yield bug == null ? null : bug.getProjectId();
            }
            case "REQUIREMENT" -> {
                var req = requirementMapper.selectById(issueId);
                yield req == null ? null : req.getProjectId();
            }
            default -> {
                var task = taskMapper.selectById(issueId);
                yield task == null ? null : task.getProjectId();
            }
        };
        if (!StringUtils.hasText(projectId)) {
            throw new BusinessException(404, "工作项不存在或已被删除");
        }
        return projectId;
    }

    // ==================== 内部 ====================

    private CustomField requireField(String fieldId) {
        CustomField f = fieldMapper.selectById(fieldId);
        if (f == null) {
            throw new BusinessException(404, "字段不存在");
        }
        return f;
    }

    /** 值 → 存储字符串（多选存 JSON 数组，其余原样） */
    private String encode(CustomField def, Object raw) {
        if (raw == null) {
            return null;
        }
        String type = def.getType();
        if ("MULTI_SELECT".equals(type)) {
            List<String> list = new ArrayList<>();
            if (raw instanceof List<?> l) {
                l.forEach(x -> list.add(String.valueOf(x)));
            } else {
                list.add(String.valueOf(raw));
            }
            validateOptions(def, list);
            try {
                return objectMapper.writeValueAsString(list);
            } catch (Exception e) {
                throw new BusinessException(400, "多选值格式不正确");
            }
        }
        String value = String.valueOf(raw).trim();
        switch (type) {
            case "NUMBER" -> {
                try {
                    new java.math.BigDecimal(value);
                } catch (NumberFormatException e) {
                    throw new BusinessException(400, "「" + def.getName() + "」必须是数字");
                }
            }
            case "DATE" -> {
                try {
                    LocalDate.parse(value);
                } catch (Exception e) {
                    throw new BusinessException(400, "「" + def.getName() + "」日期格式应为 YYYY-MM-DD");
                }
            }
            case "SELECT" -> validateOptions(def, List.of(value));
            case "CHECKBOX" -> {
                if (!List.of("true", "false", "1", "0").contains(value.toLowerCase())) {
                    throw new BusinessException(400, "「" + def.getName() + "」只能为是/否");
                }
            }
            default -> {
                // TEXT / TEXTAREA / USER 原样存
            }
        }
        return value;
    }

    /** 存储字符串 → 值（多选还原成数组） */
    private Object decode(CustomField def, String stored) {
        if (stored == null) {
            return null;
        }
        if ("MULTI_SELECT".equals(def.getType())) {
            try {
                return objectMapper.readValue(stored, new TypeReference<List<String>>() {});
            } catch (Exception e) {
                return List.of();
            }
        }
        if ("NUMBER".equals(def.getType())) {
            try {
                return new java.math.BigDecimal(stored);
            } catch (NumberFormatException e) {
                return stored;
            }
        }
        if ("CHECKBOX".equals(def.getType())) {
            return List.of("true", "1").contains(stored.toLowerCase());
        }
        return stored;
    }

    /** 校验选项是否在定义内（防止写进已删除选项） */
    private void validateOptions(CustomField def, List<String> values) {
        if (!StringUtils.hasText(def.getOptions())) {
            return;
        }
        try {
            List<String> allowed = objectMapper.readValue(def.getOptions(), new TypeReference<List<String>>() {});
            for (String v : values) {
                if (!allowed.contains(v)) {
                    throw new BusinessException(400, "「" + def.getName() + "」的取值不在可选项内: " + v);
                }
            }
        } catch (BusinessException e) {
            throw e;
        } catch (Exception e) {
            log.warn("字段 {} 的选项解析失败，跳过选项校验", def.getId());
        }
    }
}
