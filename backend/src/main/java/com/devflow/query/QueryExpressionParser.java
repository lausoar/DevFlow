package com.devflow.query;

import com.baomidou.mybatisplus.core.conditions.query.QueryWrapper;
import com.devflow.common.exception.BusinessException;

import java.time.LocalDate;
import java.time.format.DateTimeParseException;
import java.util.ArrayList;
import java.util.List;
import java.util.Locale;
import java.util.Map;
import java.util.Set;

/**
 * 受限查询表达式解析器（JQL 的简化版）。
 *
 * 设计取舍（重要）：
 * - **不解析 SQL**，只解析"字段 运算符 值"的三元组，再由白名单字段映射到列名；
 * - 字段、运算符、函数都来自白名单，未知字段直接报 400，杜绝拼接注入；
 * - 值一律走 MyBatis 参数绑定，不参与字符串拼接。
 *
 * 支持语法：
 *   status IN (TODO, DOING) AND priority = HIGH AND dueDate &lt; endOfWeek()
 *   assignee = currentUser() OR reporter = currentUser()
 *   title ~ "登录"            （~ 表示 LIKE）
 *   resolved IS NULL
 * 运算符：= != &gt; &gt;= &lt; &lt;= ~ IN NOT IN IS NULL IS NOT NULL
 * 逻辑：AND / OR（不支持括号嵌套，保持可预期）
 */
public class QueryExpressionParser {

    /** 单个字段查询条件 */
    public record Condition(String field, String operator, List<String> values) {
    }

    /** 解析结果：按 AND 分组的 OR 子句（最外层仅支持 AND/OR 同级串联） */
    public record ParsedQuery(List<Condition> andConditions, List<Condition> orConditions) {
    }

    /**
     * 允许查询的字段 → 列名。
     *
     * 按查询对象分别维护：三种工作项的列并不相同（requirement 没有 reporter_id /
     * sprint_id，bug 没有 sprint_id，task 没有 severity）。用一份全局白名单会让
     * REQUIREMENT 的合法表达式生成引用不存在列的 SQL，直接把 400 变成 500。
     */
    private static final Map<String, String> COMMON_COLUMNS = Map.ofEntries(
            Map.entry("status", "status"),
            Map.entry("priority", "priority"),
            Map.entry("assignee", "assignee_id"),
            Map.entry("assigneeId", "assignee_id"),
            Map.entry("fixVersion", "fix_version_id"),
            Map.entry("title", "title"),
            Map.entry("keyword", "title"),
            Map.entry("createdAt", "created_at"),
            Map.entry("updatedAt", "updated_at"),
            Map.entry("project", "project_id"));

    private static final Map<String, Map<String, String>> ENTITY_COLUMNS = Map.of(
            "TASK", merge(COMMON_COLUMNS, Map.ofEntries(
                    Map.entry("reporter", "reporter_id"),
                    Map.entry("sprint", "sprint_id"),
                    Map.entry("sprintId", "sprint_id"),
                    Map.entry("dueDate", "due_date"))),
            "BUG", merge(COMMON_COLUMNS, Map.ofEntries(
                    Map.entry("reporter", "reporter_id"),
                    Map.entry("severity", "severity"))),
            "REQUIREMENT", merge(COMMON_COLUMNS, Map.ofEntries(
                    // RICE 评分：分数是生成列，可按价值筛选与排序
                    Map.entry("score", "rice_score"),
                    Map.entry("riceScore", "rice_score"),
                    Map.entry("reach", "reach"),
                    Map.entry("impact", "impact"),
                    Map.entry("confidence", "confidence"),
                    Map.entry("effort", "effort"))));

    private static Map<String, String> merge(Map<String, String> base, Map<String, String> extra) {
        Map<String, String> merged = new java.util.HashMap<>(base);
        merged.putAll(extra);
        return Map.copyOf(merged);
    }

    /** 可接受的时间函数 */
    private static final Set<String> TIME_FUNCTIONS = Set.of(
            "now()", "today()", "startOfWeek()", "endOfWeek()", "startOfMonth()", "endOfMonth()");

    private static final Set<String> OPERATORS = Set.of(
            "=", "!=", ">", ">=", "<", "<=", "~", "IN", "NOT IN", "IS NULL", "IS NOT NULL");

    private final String entity;
    private final Map<String, String> fieldColumns;

    /**
     * @param entity 查询对象（TASK/BUG/REQUIREMENT），决定字段白名单与错误提示
     */
    public QueryExpressionParser(String entity) {
        this.entity = entity == null ? "TASK" : entity.toUpperCase(Locale.ROOT);
        this.fieldColumns = ENTITY_COLUMNS.getOrDefault(this.entity, ENTITY_COLUMNS.get("TASK"));
    }

    /**
     * 把表达式翻译成 MyBatis Plus 的 QueryWrapper。
     *
     * @param expression 表达式文本
     * @param userId     当前用户（currentUser() 函数求值用）
     * @param projectIds 可见项目（强制收口，表达式不能突破）
     */
    public QueryWrapper<?> toWrapper(String expression, String userId, List<String> projectIds) {
        ParsedQuery parsed = parse(expression);
        QueryWrapper<?> wrapper = new QueryWrapper<>();
        // 可见性优先：无论表达式写什么，都只能查可见项目
        wrapper.in("project_id", projectIds);
        for (Condition c : parsed.andConditions()) {
            apply(wrapper, c, userId, true);
        }
        if (!parsed.orConditions().isEmpty()) {
            wrapper.and(w -> {
                boolean first = true;
                for (Condition c : parsed.orConditions()) {
                    if (first) {
                        apply(w, c, userId, false);
                        first = false;
                    } else {
                        w.or();
                        apply(w, c, userId, false);
                    }
                }
            });
        }
        return wrapper;
    }

    /** 解析表达式为条件列表（纯逻辑，单测友好） */
    public ParsedQuery parse(String expression) {
        if (expression == null || expression.isBlank()) {
            return new ParsedQuery(List.of(), List.of());
        }
        // 先按 AND / OR 切分（值内部不允许出现未加引号的 AND/OR，保持语法可预期）
        List<Condition> andConds = new ArrayList<>();
        List<Condition> orConds = new ArrayList<>();
        String[] andParts = expression.split("(?i)\\s+AND\\s+");
        for (String andPart : andParts) {
            String[] orParts = andPart.split("(?i)\\s+OR\\s+");
            if (orParts.length > 1) {
                for (String orPart : orParts) {
                    orConds.add(parseCondition(orPart));
                }
            } else {
                andConds.add(parseCondition(andPart));
            }
        }
        return new ParsedQuery(andConds, orConds);
    }

    private Condition parseCondition(String raw) {
        String text = raw.trim();
        if (text.isEmpty()) {
            throw new BusinessException(400, "查询表达式存在空条件");
        }

        // IS NULL / IS NOT NULL
        String upper = text.toUpperCase(Locale.ROOT);
        if (upper.endsWith(" IS NOT NULL")) {
            return new Condition(requireField(text.substring(0, upper.lastIndexOf(" IS NOT NULL"))),
                    "IS NOT NULL", List.of());
        }
        if (upper.endsWith(" IS NULL")) {
            return new Condition(requireField(text.substring(0, upper.lastIndexOf(" IS NULL"))),
                    "IS NULL", List.of());
        }

        // 运算符（长的先匹配，避免 ">=" 被当成 ">"）
        String[] operators = {">=", "<=", "!=", "NOT IN", "IN", ">", "<", "=", "~"};
        for (String op : operators) {
            int idx = indexOfOperator(text, op);
            if (idx < 0) {
                continue;
            }
            String field = requireField(text.substring(0, idx));
            String valuePart = text.substring(idx + op.length()).trim();
            List<String> values = parseValues(valuePart);
            return new Condition(field, op, values);
        }
        throw new BusinessException(400, "无法解析查询条件: " + text);
    }

    private int indexOfOperator(String text, String op) {
        if ("IN".equals(op) || "NOT IN".equals(op)) {
            // IN 必须是独立单词，避免匹配到 "INDEX" 之类
            String upper = text.toUpperCase(Locale.ROOT);
            String target = " " + op + " ";
            int idx = upper.indexOf(target);
            return idx < 0 ? -1 : idx + 1;
        }
        return text.indexOf(op);
    }

    private String requireField(String raw) {
        String field = raw.trim();
        if (!fieldColumns.containsKey(field)) {
            throw new BusinessException(400, "不支持的查询字段: " + field + "（" + entity + "）");
        }
        return field;
    }

    private List<String> parseValues(String raw) {
        String value = raw.trim();
        if (value.isEmpty()) {
            throw new BusinessException(400, "查询条件缺少值");
        }
        // IN (a, b, c)
        if (value.startsWith("(") && value.endsWith(")")) {
            value = value.substring(1, value.length() - 1);
        }
        List<String> values = new ArrayList<>();
        for (String part : value.split(",")) {
            String v = part.trim();
            if (v.startsWith("\"") && v.endsWith("\"") && v.length() >= 2) {
                v = v.substring(1, v.length() - 1);
            }
            if (!v.isEmpty()) {
                values.add(requireSafeValue(v));
            }
        }
        if (values.isEmpty()) {
            throw new BusinessException(400, "查询条件缺少值");
        }
        return values;
    }

    /**
     * 值的安全校验：值本来就走参数绑定，这里再拒绝一次 SQL 片段特征，
     * 目的是让"把 SQL 写进查询表达式"这类误用直接报错，而不是被静默当成普通文本。
     */
    private String requireSafeValue(String value) {
        String lowered = value.toLowerCase(Locale.ROOT);
        if (lowered.contains(";") || lowered.contains("--") || lowered.contains("/*")
                || lowered.contains("*/") || lowered.contains("'") || lowered.contains("`")
                || lowered.contains("\\")) {
            throw new BusinessException(400, "查询值包含非法字符: " + value);
        }
        return value;
    }

    // ==================== 条件落到 QueryWrapper ====================

    private void apply(QueryWrapper<?> wrapper, Condition c, String userId, boolean andMode) {
        String column = fieldColumns.get(c.field());
        List<String> values = resolveValues(c.values(), userId);
        switch (c.operator()) {
            case "=" -> wrapper.eq(column, values.get(0));
            case "!=" -> wrapper.ne(column, values.get(0));
            case ">" -> wrapper.gt(column, values.get(0));
            case ">=" -> wrapper.ge(column, values.get(0));
            case "<" -> wrapper.lt(column, values.get(0));
            case "<=" -> wrapper.le(column, values.get(0));
            case "~" -> wrapper.like(column, values.get(0));
            case "IN" -> wrapper.in(column, values);
            case "NOT IN" -> wrapper.notIn(column, values);
            case "IS NULL" -> wrapper.isNull(column);
            case "IS NOT NULL" -> wrapper.isNotNull(column);
            default -> throw new BusinessException(400, "不支持的运算符: " + c.operator());
        }
    }

    /** 函数求值：currentUser() → 用户 ID；时间函数 → LocalDate 字符串 */
    private List<String> resolveValues(List<String> raw, String userId) {
        List<String> out = new ArrayList<>();
        for (String v : raw) {
            String trimmed = v.trim();
            if ("currentUser()".equalsIgnoreCase(trimmed)) {
                out.add(userId);
            } else if (TIME_FUNCTIONS.contains(trimmed)) {
                out.add(resolveTimeFunction(trimmed));
            } else {
                out.add(trimmed);
            }
        }
        return out;
    }

    private String resolveTimeFunction(String fn) {
        LocalDate today = LocalDate.now();
        return switch (fn) {
            case "now()", "today()" -> today.toString();
            case "startOfWeek()" -> today.minusDays(today.getDayOfWeek().getValue() - 1).toString();
            case "endOfWeek()" -> today.plusDays(7 - today.getDayOfWeek().getValue()).toString();
            case "startOfMonth()" -> today.withDayOfMonth(1).toString();
            case "endOfMonth()" -> today.withDayOfMonth(today.lengthOfMonth()).toString();
            default -> throw new BusinessException(400, "不支持的时间函数: " + fn);
        };
    }

    /** 供上层校验日期字面量（可选） */
    public static boolean isDate(String value) {
        try {
            LocalDate.parse(value);
            return true;
        } catch (DateTimeParseException e) {
            return false;
        }
    }
}
