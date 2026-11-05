package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.TaskWorklog;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface TaskWorklogMapper extends BaseMapper<TaskWorklog> {

    /** 工时列表（含登记人展示名，按登记时间倒序） */
    @Select("SELECT w.*, COALESCE(NULLIF(u.real_name, ''), u.username) AS user_name " +
            "FROM task_worklog w LEFT JOIN sys_user u ON w.user_id = u.id " +
            "WHERE w.task_id = #{taskId} ORDER BY w.created_at DESC")
    List<TaskWorklog> selectByTask(@Param("taskId") String taskId);
}
