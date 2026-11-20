package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.Project;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

/**
 * 项目 Mapper
 */
@Mapper
public interface ProjectMapper extends BaseMapper<Project> {

    /** 串行化同一项目的工作流整体替换与状态流转。 */
    @Select("SELECT * FROM project WHERE id = #{projectId} FOR UPDATE")
    Project selectForUpdate(@Param("projectId") String projectId);
}
