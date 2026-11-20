-- ============================================
-- V13：OKR 目标对齐（审计报告 §6.12）
-- ============================================
-- 只做一件事：把项目/需求挂到企业目标上，并能在目标详情页看到投入与产出。
-- 不建公式引擎、不建手动进度条——目标进度完全由已有数据（工时、任务完成、缺陷）
-- 聚合得出，避免再引入一套需要人工维护的数字。

CREATE TABLE IF NOT EXISTS `objective` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标ID',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属企业',
  `program_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目集（可空）',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '目标说明',
  `owner_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标负责人',
  `period` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '周期，如 2026Q4',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT 'ACTIVE/ACHIEVED/ARCHIVED',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_objective_enterprise` (`enterprise_id`, `status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业目标（OKR）';

-- 项目与需求挂目标：列可空，未挂目标的项目行为完全不变。
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD COLUMN objective_id varchar(36) DEFAULT NULL COMMENT ''所属目标（OKR）'' AFTER program_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'objective_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD INDEX `idx_project_objective` (`objective_id`)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'project' AND index_name = 'idx_project_objective');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN objective_id varchar(36) DEFAULT NULL COMMENT ''所属目标（OKR）'' AFTER parent_id', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'objective_id');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD INDEX `idx_requirement_objective` (`objective_id`)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND index_name = 'idx_requirement_objective');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;
