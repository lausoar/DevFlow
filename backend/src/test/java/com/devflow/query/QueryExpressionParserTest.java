package com.devflow.query;

import com.devflow.common.exception.BusinessException;
import org.junit.jupiter.api.DisplayName;
import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;

/**
 * 查询表达式解析器单测。
 *
 * 这是"用户输入直接影响的解析层"，安全性主要靠它保证：
 * 未知字段、未知运算符、试图注入 SQL 片段都必须被拒绝，而不是透传。
 */
@DisplayName("QueryExpressionParser 结构化查询解析")
class QueryExpressionParserTest {

    private final QueryExpressionParser parser = new QueryExpressionParser("TASK");

    @Test
    @DisplayName("单条件等值")
    void singleEquality() {
        var parsed = parser.parse("priority = HIGH");
        assertThat(parsed.andConditions()).hasSize(1);
        assertThat(parsed.andConditions().get(0).field()).isEqualTo("priority");
        assertThat(parsed.andConditions().get(0).operator()).isEqualTo("=");
        assertThat(parsed.andConditions().get(0).values()).containsExactly("HIGH");
    }

    @Test
    @DisplayName("AND 串联多个条件")
    void andChain() {
        var parsed = parser.parse("status = TODO AND priority = HIGH AND assignee = currentUser()");
        assertThat(parsed.andConditions()).hasSize(3);
        assertThat(parsed.orConditions()).isEmpty();
    }

    @Test
    @DisplayName("OR 条件单独成组")
    void orGroup() {
        var parsed = parser.parse("status = TODO OR status = DOING");
        assertThat(parsed.andConditions()).isEmpty();
        assertThat(parsed.orConditions()).hasSize(2);
    }

    @Test
    @DisplayName("IN 列表解析")
    void inList() {
        var parsed = parser.parse("status IN (TODO, DOING, TESTING)");
        assertThat(parsed.andConditions().get(0).operator()).isEqualTo("IN");
        assertThat(parsed.andConditions().get(0).values())
                .containsExactly("TODO", "DOING", "TESTING");
    }

    @Test
    @DisplayName("IS NULL / IS NOT NULL")
    void nullChecks() {
        assertThat(parser.parse("assignee IS NULL").andConditions().get(0).operator())
                .isEqualTo("IS NULL");
        assertThat(parser.parse("dueDate IS NOT NULL").andConditions().get(0).operator())
                .isEqualTo("IS NOT NULL");
    }

    @Test
    @DisplayName("比较运算符不被前缀误匹配（>= 不能当成 >）")
    void comparisonOperators() {
        var parsed = parser.parse("dueDate >= 2026-11-01");
        assertThat(parsed.andConditions().get(0).operator()).isEqualTo(">=");
        assertThat(parsed.andConditions().get(0).values()).containsExactly("2026-11-01");
    }

    @Test
    @DisplayName("~ 映射为 LIKE")
    void likeOperator() {
        var parsed = parser.parse("title ~ 登录");
        assertThat(parsed.andConditions().get(0).operator()).isEqualTo("~");
    }

    @Test
    @DisplayName("未知字段被拒绝（白名单）")
    void unknownFieldRejected() {
        assertThatThrownBy(() -> parser.parse("password = x"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的查询字段");
    }

    @Test
    @DisplayName("注入尝试被拒绝：字段名里带 SQL 片段")
    void injectionAttemptRejected() {
        assertThatThrownBy(() -> parser.parse("1=1 OR 1=1 = 1"))
                .isInstanceOf(BusinessException.class);
        assertThatThrownBy(() -> parser.parse("status = TODO; DROP TABLE task"))
                .isInstanceOf(BusinessException.class);
    }

    @Test
    @DisplayName("空表达式返回空条件（不报错）")
    void emptyExpression() {
        assertThat(parser.parse("").andConditions()).isEmpty();
        assertThat(parser.parse(null).andConditions()).isEmpty();
    }

    @Test
    @DisplayName("缺少值报错")
    void missingValueRejected() {
        assertThatThrownBy(() -> parser.parse("status = "))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("缺少值");
    }

    @Test
    @DisplayName("currentUser() 求值为用户 ID")
    void currentUserResolved() {
        var wrapper = parser.toWrapper("assignee = currentUser()", "u-123", java.util.List.of("p1"));
        // 参数在生成 SQL 片段时才写入 paramNameValuePairs，先触发一次
        wrapper.getSqlSegment();
        assertThat(wrapper.getParamNameValuePairs()).containsValue("u-123");
    }

    @Test
    @DisplayName("字段白名单按查询对象区分：REQUIREMENT 不接受 sprint / reporter")
    void fieldWhitelistIsEntityScoped() {
        var requirementParser = new QueryExpressionParser("REQUIREMENT");
        assertThatThrownBy(() -> requirementParser.parse("sprint = s1"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("不支持的查询字段");
        assertThatThrownBy(() -> requirementParser.parse("reporter = currentUser()"))
                .isInstanceOf(BusinessException.class)
                .hasMessageContaining("REQUIREMENT");
        // BUG 同样没有 sprint
        assertThatThrownBy(() -> new QueryExpressionParser("BUG").parse("sprint = s1"))
                .isInstanceOf(BusinessException.class);
        // TASK 有 sprint
        assertThat(new QueryExpressionParser("TASK").parse("sprint = s1").andConditions()).hasSize(1);
    }

    @Test
    @DisplayName("REQUIREMENT 支持 RICE 分数与工作量字段")
    void requirementScoreFieldsAllowed() {
        var requirementParser = new QueryExpressionParser("REQUIREMENT");
        for (String field : new String[]{"score", "riceScore", "reach", "impact", "confidence", "effort"}) {
            var wrapper = requirementParser.toWrapper(field + " >= 1", "u1", java.util.List.of("p1"));
            assertThat(wrapper.getSqlSegment()).contains("rice_score".equals(field) || "riceScore".equals(field)
                    ? "rice_score" : field);
        }
        // 这些字段只属于 REQUIREMENT，TASK 上不应被接受
        assertThatThrownBy(() -> new QueryExpressionParser("TASK").parse("riceScore > 1"))
                .isInstanceOf(BusinessException.class);
    }
}
