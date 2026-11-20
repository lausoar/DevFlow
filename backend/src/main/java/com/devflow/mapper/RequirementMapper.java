package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.Requirement;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.util.List;

@Mapper
public interface RequirementMapper extends BaseMapper<Requirement> {

    /** 项目内仍在使用的状态码（工作流整体替换前校验）。 */
    @Select("SELECT DISTINCT status FROM requirement WHERE project_id = #{projectId} AND status IS NOT NULL")
    List<String> selectUsedStatusCodes(@Param("projectId") String projectId);

    /** 回收站：项目下已逻辑删除的记录（绕过 @TableLogic 过滤，需原生 SQL） */
    @Select("SELECT * FROM requirement WHERE project_id = #{projectId} AND deleted = 1 " +
            "ORDER BY deleted_at DESC, id DESC LIMIT 200")
    List<Requirement> selectDeletedByProject(@Param("projectId") String projectId);

    /** 按 ID 查询（绕过 @TableLogic 的 deleted=0 过滤，回收站用） */
    @Select("SELECT * FROM requirement WHERE id = #{id}")
    Requirement selectByIdIgnoreDeleted(@Param("id") String id);

    /**
     * 带审计的软删除。
     *
     * 不用 MyBatis-Plus 的 deleteById：它只写 deleted=1，不记录时间与人，
     * 回收站于是只能拿 updated_at 冒充"删除于"。这里显式写两列。
     * 返回影响行数，调用方据此判断目标是否真的存在/未被删。
     */
    @Update("UPDATE requirement SET deleted = 1, deleted_at = NOW(), deleted_by = #{operatorId} "
            + "WHERE id = #{id} AND deleted = 0")
    int softDelete(@Param("id") String id, @Param("operatorId") String operatorId);

    /** 回收站恢复 */
    @Update("UPDATE requirement SET deleted = 0, deleted_at = NULL, deleted_by = NULL "
            + "WHERE id = #{id} AND deleted = 1")
    int restoreById(@Param("id") String id);
}
