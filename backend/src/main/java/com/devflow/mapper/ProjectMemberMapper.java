package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.ProjectMember;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

/**
 * 项目成员 Mapper
 */
@Mapper
public interface ProjectMemberMapper extends BaseMapper<ProjectMember> {

    /**
     * 查询项目成员列表（含用户信息）
     */
    @Select("SELECT pm.id, pm.project_id, pm.user_id, pm.role, pm.joined_at, " +
            "u.username, u.real_name, u.email, u.avatar " +
            "FROM project_member pm " +
            "LEFT JOIN sys_user u ON pm.user_id = u.id " +
            "WHERE pm.project_id = #{projectId} " +
            "ORDER BY pm.role ASC, pm.joined_at ASC")
    List<ProjectMember> selectMembersWithUser(@Param("projectId") String projectId);
}
