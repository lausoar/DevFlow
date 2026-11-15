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
    @Select("SELECT * FROM bug WHERE project_id = #{projectId} AND deleted = 1 " +
            "ORDER BY deleted_at DESC, id DESC LIMIT 200")
    List<Bug> selectDeletedByProject(@Param("projectId") String projectId);

    /** 按 ID 查询（绕过 @TableLogic 的 deleted=0 过滤，回收站用） */
    @Select("SELECT * FROM bug WHERE id = #{id}")
    Bug selectByIdIgnoreDeleted(@Param("id") String id);

    /**
     * 带审计的软删除。
     *
     * 不用 MyBatis-Plus 的 deleteById：它只写 deleted=1，不记录时间与人，
     * 回收站于是只能拿 updated_at 冒充"删除于"。这里显式写两列。
     * 返回影响行数，调用方据此判断目标是否真的存在/未被删。
     */
    @Update("UPDATE bug SET deleted = 1, deleted_at = NOW(), deleted_by = #{operatorId} "
            + "WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("id") String id, @Param("operatorId") String operatorId);

    /** 回收站恢复 */
    @Update("UPDATE bug SET deleted = 0, deleted_at = NULL, deleted_by = NULL "
            + "WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") String id);
}
