package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.Document;
import com.devflow.entity.DocumentIndexOutbox;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface DocumentIndexOutboxMapper extends BaseMapper<DocumentIndexOutbox> {

    /**
     * Atomic idempotent insert. The unique document/operation/version key makes
     * duplicate saves a no-op even when two application instances race.
     */
    @Insert("""
            INSERT INTO document_index_outbox
                (id, document_id, project_id, enterprise_id, operation,
                 title, content, doc_type, document_version, document_updated_at, status,
                 attempts, next_attempt_at, last_error, created_at, updated_at)
            VALUES
                (#{outbox.id}, #{outbox.documentId}, #{outbox.projectId}, #{outbox.enterpriseId},
                 #{outbox.operation}, #{outbox.title}, #{outbox.content}, #{outbox.docType},
                 #{outbox.documentVersion}, #{outbox.documentUpdatedAt}, #{outbox.status},
                 #{outbox.attempts}, #{outbox.nextAttemptAt}, #{outbox.lastError},
                 #{outbox.createdAt}, #{outbox.updatedAt})
            ON DUPLICATE KEY UPDATE document_id = VALUES(document_id)
            """)
    int insertIfAbsent(@Param("outbox") DocumentIndexOutbox outbox);

    @Update("""
            UPDATE document_index_outbox
            SET status = #{outbox.status}, attempts = #{outbox.attempts},
                next_attempt_at = #{outbox.nextAttemptAt}, last_error = #{outbox.lastError},
                updated_at = #{outbox.updatedAt}
            WHERE id = #{outbox.id}
            """)
    int updateState(@Param("outbox") DocumentIndexOutbox outbox);

    @Select("""
            SELECT * FROM document_index_outbox
            WHERE status IN ('PENDING', 'RETRY') AND next_attempt_at <= #{now}
            ORDER BY next_attempt_at, created_at
            LIMIT #{limit}
            """)
    List<DocumentIndexOutbox> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** Reconciliation must see logically deleted rows too. */
    @Select("SELECT * FROM document")
    List<Document> selectAllDocumentsIncludingDeleted();

    @Select("SELECT * FROM document WHERE id = #{documentId}")
    Document selectDocumentIncludingDeleted(@Param("documentId") String documentId);

    /** A failed version is retried by the next reconciliation without duplicating it. */
    @Update("""
            UPDATE document_index_outbox
            SET status = 'PENDING', attempts = 0, next_attempt_at = #{nextAttemptAt},
                last_error = NULL, updated_at = #{updatedAt}
            WHERE document_id = #{documentId} AND operation = #{operation}
              AND document_updated_at = #{documentUpdatedAt} AND status = 'FAILED'
            """)
    int requeueFailed(@Param("documentId") String documentId,
                      @Param("operation") String operation,
                      @Param("documentUpdatedAt") LocalDateTime documentUpdatedAt,
                      @Param("nextAttemptAt") LocalDateTime nextAttemptAt,
                      @Param("updatedAt") LocalDateTime updatedAt);
}
