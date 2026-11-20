package com.devflow.mapper;

import com.baomidou.mybatisplus.core.mapper.BaseMapper;
import com.devflow.entity.Objective;
import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.time.LocalDate;
import java.util.List;
import java.util.Map;

/**
 * 目标聚合查询：与 StatisticsAggregateMapper 同样的取舍——GROUP BY 下推到数据库，
 * 目标跨越多个项目时不会把整库任务加载进内存。
 */
@Mapper
public interface ObjectiveAggregateMapper extends BaseMapper<Objective> {

    /** 按项目汇总工时（可限定日期区间；两参数都可空） */
    @Select("<script>"
            + "SELECT t.project_id AS projectId, COALESCE(SUM(w.hours), 0) AS hours "
            + "FROM task_worklog w JOIN task t ON w.task_id = t.id "
            + "WHERE t.deleted = 0 AND t.project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "<if test='from != null'> AND w.work_date &gt;= #{from}</if>"
            + "<if test='to != null'> AND w.work_date &lt;= #{to}</if>"
            + " GROUP BY t.project_id"
            + "</script>")
    List<Map<String, Object>> worklogHoursByProject(@Param("projectIds") List<String> projectIds,
                                                    @Param("from") LocalDate from,
                                                    @Param("to") LocalDate to);

    /** 按项目统计"进入完成分类"的事件数（V9 的追加式事件，忽略 DONE→DONE 噪声） */
    @Select("<script>"
            + "SELECT project_id AS projectId, COUNT(*) AS n FROM task_status_event "
            + "WHERE to_category = 'DONE' AND (from_category IS NULL OR from_category &lt;&gt; 'DONE') "
            + "AND project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "<if test='from != null'> AND created_at &gt;= #{from}</if>"
            + "<if test='to != null'> AND created_at &lt; #{to} + INTERVAL 1 DAY</if>"
            + " GROUP BY project_id"
            + "</script>")
    List<Map<String, Object>> completedTasksByProject(@Param("projectIds") List<String> projectIds,
                                                      @Param("from") LocalDate from,
                                                      @Param("to") LocalDate to);

    /** 目标下直接挂的需求（按状态） */
    @Select("<script>"
            + "SELECT project_id AS projectId, status AS status, COUNT(*) AS n FROM requirement "
            + "WHERE deleted = 0 AND objective_id = #{objectiveId} "
            + "GROUP BY project_id, status"
            + "</script>")
    List<Map<String, Object>> requirementCountByObjective(@Param("objectiveId") String objectiveId);

    /** 项目的负责人与名称（目标详情页展示用，一次取齐避免 N+1） */
    @Select("<script>"
            + "SELECT id, name, owner_id AS ownerId, status FROM project WHERE deleted = 0 AND objective_id = #{objectiveId}"
            + "</script>")
    List<Map<String, Object>> projectsOfObjective(@Param("objectiveId") String objectiveId);
}
