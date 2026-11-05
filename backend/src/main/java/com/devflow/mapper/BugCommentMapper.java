package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.BugComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;

@Mapper
public interface BugCommentMapper extends BaseMapper<BugComment> {

    /** 评论列表（含评论人展示名与头像，按时间正序） */
    @Select("SELECT c.*, COALESCE(NULLIF(u.real_name, ''), u.username) AS user_name, " +
            "u.avatar AS user_avatar " +
            "FROM bug_comment c LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.bug_id = #{bugId} AND c.deleted = 0 " +
            "ORDER BY c.created_at ASC")
    List<BugComment> selectByBug(@Param("bugId") String bugId);
}
