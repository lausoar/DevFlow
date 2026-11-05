package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.TestExecution;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TestExecutionMapper extends BaseMapper<TestExecution> {

    /** 用例的执行历史（含执行人展示名与关联缺陷标题） */
    @Select("SELECT e.*, COALESCE(NULLIF(u.real_name, ''), u.username) AS executor_name, " +
            "b.title AS bug_title " +
            "FROM test_execution e " +
            "LEFT JOIN sys_user u ON e.executor_id = u.id " +
            "LEFT JOIN bug b ON e.bug_id = b.id " +
            "WHERE e.case_id = #{caseId} ORDER BY e.created_at DESC")
    List<TestExecution> selectByCase(@Param("caseId") String caseId);

    /** 项目全部执行记录（最新在前） */
    @Select("SELECT e.*, COALESCE(NULLIF(u.real_name, ''), u.username) AS executor_name, " +
            "b.title AS bug_title " +
            "FROM test_execution e " +
            "LEFT JOIN sys_user u ON e.executor_id = u.id " +
            "LEFT JOIN bug b ON e.bug_id = b.id " +
            "WHERE e.project_id = #{projectId} ORDER BY e.created_at DESC")
    List<TestExecution> selectByProject(@Param("projectId") String projectId);
}
