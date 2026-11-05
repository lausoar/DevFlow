package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.DocumentVersion;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

@Mapper
public interface DocumentVersionMapper extends BaseMapper<DocumentVersion> {

    /** 版本列表（含编辑人展示名，不含大字段 content） */
    @Select("SELECT dv.id, dv.document_id AS documentId, dv.version, dv.title, " +
            "dv.editor_id AS editorId, COALESCE(NULLIF(u.real_name, ''), u.username) AS editorName, " +
            "dv.created_at AS createdAt " +
            "FROM document_version dv LEFT JOIN sys_user u ON dv.editor_id = u.id " +
            "WHERE dv.document_id = #{documentId} ORDER BY dv.version DESC")
    List<Map<String, Object>> selectVersionList(@Param("documentId") String documentId);
}
