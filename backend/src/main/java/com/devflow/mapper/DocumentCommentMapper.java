package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.DocumentComment;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentCommentMapper extends BaseMapper<DocumentComment> {

    /** 评论列表（含评论人展示名与头像） */
    @Select("SELECT c.id, c.document_id AS documentId, c.user_id AS userId, c.content, " +
            "c.created_at AS createdAt, COALESCE(NULLIF(u.real_name, ''), u.username) AS userName, " +
            "u.avatar AS userAvatar " +
            "FROM document_comment c LEFT JOIN sys_user u ON c.user_id = u.id " +
            "WHERE c.document_id = #{documentId} ORDER BY c.created_at DESC")
    List<Map<String, Object>> selectCommentList(@Param("documentId") String documentId);
}
