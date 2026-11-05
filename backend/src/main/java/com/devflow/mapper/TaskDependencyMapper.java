package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.TaskDependency;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface TaskDependencyMapper extends BaseMapper<TaskDependency> {

    /** 沿依赖链向上游走一步：某任务依赖的全部前置（用于环检测） */
    @Select("SELECT depends_on_task_id FROM task_dependency WHERE task_id = #{taskId}")
    List<String> selectDependsOnIds(@Param("taskId") String taskId);

    /** 项目内每个任务的未完成前置数量（看板徽章用，一次查询） */
    @Select("SELECT td.task_id AS taskId, COUNT(*) AS undone " +
            "FROM task_dependency td " +
            "JOIN task t ON td.task_id = t.id " +
            "JOIN task pre ON td.depends_on_task_id = pre.id " +
            "WHERE t.project_id = #{projectId} AND pre.status <> 'DONE' " +
            "GROUP BY td.task_id")
    List<Map<String, Object>> countUndoneBlockersByProject(@Param("projectId") String projectId);
}
