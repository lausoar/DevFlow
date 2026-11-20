package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.TriageItem;
import org.apache.ibatis.annotations.Insert;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;
import org.apache.ibatis.annotations.Update;

import java.time.LocalDateTime;
import java.util.List;

@Mapper
public interface TriageItemMapper extends BaseMapper<TriageItem> {

    /**
     * 幂等入箱：同一条外部提交重复触发（重试、双写）只保留一条。
     * 返回 0 表示已存在，调用方不必再发通知。
     */
    @Insert("""
            INSERT INTO triage_item
                (id, project_id, enterprise_id, source, source_ref_id, title, submitter_name,
                 status, result_ref_id, decided_by, decided_at, sla_due_at, sla_notified_at,
                 created_at, updated_at)
            VALUES
                (#{item.id}, #{item.projectId}, #{item.enterpriseId}, #{item.source},
                 #{item.sourceRefId}, #{item.title}, #{item.submitterName}, #{item.status},
                 #{item.resultRefId}, #{item.decidedBy}, #{item.decidedAt}, #{item.slaDueAt},
                 #{item.slaNotifiedAt}, #{item.createdAt}, #{item.updatedAt})
            ON DUPLICATE KEY UPDATE triage_item.id = triage_item.id
            """)
    int insertIfAbsent(@Param("item") TriageItem item);

    /**
     * 原子处理：只有仍处于 PENDING 的条目能被接受/拒绝/合并。
     * 返回 0 表示已被别人处理（并发双人分诊时第二个人不会覆盖结论）。
     */
    @Update("""
            UPDATE triage_item
            SET status = #{status}, result_ref_id = #{resultRefId}, decided_by = #{operatorId},
                decided_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND status = 'PENDING'
            """)
    int decide(@Param("id") String id,
               @Param("status") String status,
               @Param("resultRefId") String resultRefId,
               @Param("operatorId") String operatorId,
               @Param("now") LocalDateTime now);

    /** 到期未处理且未提醒过的条目 */
    @Select("""
            SELECT * FROM triage_item
            WHERE status = 'PENDING' AND sla_due_at IS NOT NULL AND sla_due_at <= #{now}
              AND sla_notified_at IS NULL
            ORDER BY sla_due_at
            LIMIT #{limit}
            """)
    List<TriageItem> selectDue(@Param("now") LocalDateTime now, @Param("limit") int limit);

    /** 原子占用提醒名额：多实例只会有一个发通知 */
    @Update("""
            UPDATE triage_item SET sla_notified_at = #{now}, updated_at = #{now}
            WHERE id = #{id} AND sla_notified_at IS NULL
            """)
    int markSlaNotified(@Param("id") String id, @Param("now") LocalDateTime now);
}
