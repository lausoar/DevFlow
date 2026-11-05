package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.Bug;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface BugMapper extends BaseMapper<Bug> {

    /** 回收站：项目下已逻辑删除的记录（绕过 @TableLogic 过滤，需原生 SQL） */
    @Select("SELECT * FROM bug WHERE project_id = #{projectId} AND deleted = 1 ORDER BY updated_at DESC LIMIT 200")
    List<Bug> selectDeletedByProject(@Param("projectId") String projectId);

    /** 按 ID 查询（绕过 @TableLogic 的 deleted=0 过滤，回收站用） */
    @Select("SELECT * FROM bug WHERE id = #{id}")
    Bug selectByIdIgnoreDeleted(@Param("id") String id);

    /** 回收站恢复 */
    @Update("UPDATE bug SET deleted = 0 WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") String id);
}
