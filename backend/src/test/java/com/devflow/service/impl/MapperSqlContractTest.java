package com.devflow.service.impl;

import com.devflow.mapper.BugMapper;
import com.devflow.mapper.ProjectMapper;
import com.devflow.mapper.RequirementMapper;
import com.devflow.mapper.TaskMapper;
import com.devflow.mapper.TestExecutionMapper;
import org.apache.ibatis.annotations.Select;
import org.junit.jupiter.api.Test;

import java.util.List;

import static org.assertj.core.api.Assertions.assertThat;

class MapperSqlContractTest {

    private String selectSql(Class<?> mapperType, String methodName) throws NoSuchMethodException {
        Select select = mapperType.getMethod(methodName, String.class).getAnnotation(Select.class);
        assertThat(select).isNotNull();
        return String.join(" ", select.value());
    }

    @Test
    void usedStatusQueriesIncludeRestorableSoftDeletedRows() {
        for (Class<?> mapperType : List.of(TaskMapper.class, BugMapper.class, RequirementMapper.class)) {
            String sql;
            try {
                sql = selectSql(mapperType, "selectUsedStatusCodes");
            } catch (NoSuchMethodException e) {
                throw new AssertionError(e);
            }
            assertThat(sql).contains("SELECT DISTINCT status").doesNotContain("deleted = 0");
        }
    }

    @Test
    void projectLockUsesCurrentLockingRead() throws Exception {
        assertThat(selectSql(ProjectMapper.class, "selectForUpdate"))
                .contains("WHERE id = #{projectId}")
                .contains("FOR UPDATE");
    }

    @Test
    void executionClaimLoserUsesCurrentLockingRead() throws Exception {
        assertThat(selectSql(TestExecutionMapper.class, "selectByIdForUpdate"))
                .contains("WHERE id = #{executionId}")
                .contains("FOR UPDATE");
    }

    @Test
    void linkedBugClaimLoserUsesCurrentLockingRead() throws Exception {
        assertThat(selectSql(BugMapper.class, "selectByIdForUpdate"))
                .contains("WHERE id = #{id} AND deleted = 0")
                .contains("FOR UPDATE");
    }
}
