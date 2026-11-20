package com.devflow.mapper;

import org.apache.ibatis.annotations.Mapper;
import org.apache.ibatis.annotations.Param;
import org.apache.ibatis.annotations.Select;

import java.util.List;
import java.util.Map;

/**
 * 统计聚合查询：把"按维度计数"下推给数据库。
 *
 * 为什么单独建这个 mapper：`StatisticsServiceImpl` 原先一律 `selectList` 把整个企业的
 * 任务/Bug/需求加载进内存，再几十个 `stream().filter()` 逐条计数（全文件无一条
 * GROUP BY）。万级任务时是数十 MB 堆内存 + 秒级 GC，每次前端刷新还会重算一遍。
 *
 * ## 为什么按 (维度, 状态) 分组，而不是 "SUM(CASE WHEN status IN (doneCodes))"
 *
 * 一开始写的是后者：把各项目的 DONE 状态码取并集塞进 `IN (...)`。但**每个项目
 * 可以有自己的工作流**：A 项目的完成态叫 DONE，B 项目叫 FINISHED。并集塞进
 * 一条 SQL 后，如果 A 项目恰好也有一个叫 FINISHED 但属于"进行中"分类的状态，
 * 它就会被误算成完成 —— 一个只在多项目自定义工作流时才会出现的静默错账。
 *
 * 按 (project_id, status) 分组返回原始计数，由 Java 侧用各项目自己的
 * status→category 映射去归类，语义与逐条过滤完全一致。
 * 返回行数上限是「项目数 × 状态数」，与任务总量无关。
 */
@Mapper
public interface StatisticsAggregateMapper {

    /**
     * 按 (项目, 状态) 统计任务。
     * overdue 是"已过期"的子计数 —— 是否算逾期还要看该状态是否属于完成分类，
     * 这个判断留给调用方（同样因为工作流按项目而异）。
     */
    @Select("<script>"
            + "SELECT project_id AS projectId, status AS status, COUNT(*) AS n, "
            + "SUM(CASE WHEN due_date IS NOT NULL AND due_date &lt; NOW() THEN 1 ELSE 0 END) AS overdue "
            + "FROM task WHERE deleted = 0 AND project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "GROUP BY project_id, status"
            + "</script>")
    List<Map<String, Object>> taskCountByProjectAndStatus(@Param("projectIds") List<String> projectIds);

    /** 按 (项目, 严重级别, 状态) 统计 Bug */
    @Select("<script>"
            + "SELECT project_id AS projectId, severity AS severity, status AS status, COUNT(*) AS n "
            + "FROM bug WHERE deleted = 0 AND project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "GROUP BY project_id, severity, status"
            + "</script>")
    List<Map<String, Object>> bugCountByProjectSeverityStatus(@Param("projectIds") List<String> projectIds);

    /** 按 (项目, 状态) 统计需求 */
    @Select("<script>"
            + "SELECT project_id AS projectId, status AS status, COUNT(*) AS n "
            + "FROM requirement WHERE deleted = 0 AND project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "GROUP BY project_id, status"
            + "</script>")
    List<Map<String, Object>> requirementCountByProjectAndStatus(@Param("projectIds") List<String> projectIds);

    /** 每个项目的成员数（替代"每个项目查一次"的 N+1） */
    @Select("<script>"
            + "SELECT project_id AS projectId, COUNT(*) AS n FROM project_member WHERE project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "GROUP BY project_id"
            + "</script>")
    List<Map<String, Object>> memberCountByProject(@Param("projectIds") List<String> projectIds);

    /** 企业内去重后的成员数（一次替代 N 次查询） */
    @Select("<script>"
            + "SELECT COUNT(DISTINCT user_id) FROM project_member WHERE project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach>"
            + "</script>")
    Integer distinctMemberCount(@Param("projectIds") List<String> projectIds);

    /** 最近 N 天按 (项目, 日期) 统计新建任务数；缺数据的日期由调用方补零 */
    @Select("<script>"
            + "SELECT project_id AS projectId, DATE(created_at) AS d, COUNT(*) AS n FROM task "
            + "WHERE deleted = 0 AND project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "AND created_at &gt;= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY project_id, DATE(created_at)"
            + "</script>")
    List<Map<String, Object>> taskCreatedPerDay(@Param("projectIds") List<String> projectIds,
                                                @Param("days") int days);

    /**
     * 最近 N 天按 (项目, 事件日期) 统计进入 DONE 分类的次数。
     * 事件保存了发生时的分类，不能用任务当前 status/completed_at 反推历史；
     * DONE 分类内部的状态改名也不算一次新的完成。
     */
    @Select("<script>"
            + "SELECT e.project_id AS projectId, DATE(e.created_at) AS d, COUNT(*) AS n "
            + "FROM task_status_event e "
            + "WHERE e.to_category = 'DONE' "
            + "AND (e.from_category IS NULL OR e.from_category != 'DONE') "
            + "AND e.project_id IN "
            + "<foreach collection='projectIds' item='p' open='(' separator=',' close=')'>#{p}</foreach> "
            + "AND e.created_at &gt;= DATE_SUB(CURDATE(), INTERVAL #{days} DAY) "
            + "GROUP BY e.project_id, DATE(e.created_at)"
            + "</script>")
    List<Map<String, Object>> taskCompletedPerDay(@Param("projectIds") List<String> projectIds,
                                                  @Param("days") int days);

    /** 项目内按 (负责人, 状态) 统计任务（成员工作量） */
    @Select("SELECT assignee_id AS userId, status AS status, COUNT(*) AS n, "
            + "SUM(CASE WHEN due_date IS NOT NULL AND due_date < NOW() THEN 1 ELSE 0 END) AS overdue, "
            + "SUM(CASE WHEN priority IN ('CRITICAL','HIGH') THEN 1 ELSE 0 END) AS criticalCount "
            + "FROM task WHERE deleted = 0 AND project_id = #{projectId} AND assignee_id IS NOT NULL "
            + "GROUP BY assignee_id, status")
    List<Map<String, Object>> taskCountByAssigneeAndStatus(@Param("projectId") String projectId);

    /** 项目内按 (负责人, 状态) 统计 Bug */
    @Select("SELECT assignee_id AS userId, status AS status, COUNT(*) AS n "
            + "FROM bug WHERE deleted = 0 AND project_id = #{projectId} AND assignee_id IS NOT NULL "
            + "GROUP BY assignee_id, status")
    List<Map<String, Object>> bugCountByAssigneeAndStatus(@Param("projectId") String projectId);
}
