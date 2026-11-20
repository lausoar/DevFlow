-- ============================================
-- V14：分诊收件箱（审计报告 §6.11）
-- ============================================
-- 游客反馈通道、站内建议与 Git webhook 都是"外部输入"，但之前它们直接落进
-- Bug 列表/反馈列表，没有任何闸门：pending 的游客 Bug 会出现在看板统计里，
-- 建议没有"接受/拒绝/合并"的统一动作，也无法度量首次响应时间。
--
-- triage_item 只是"待分诊条目"的索引：源数据（bug / project_feedback）保持不动，
-- 分诊动作作用于源记录并在这里记录结论与首响时间，重复提交靠唯一键去重。

CREATE TABLE IF NOT EXISTS `triage_item` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '分诊条目ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '企业ID（冗余，便于跨项目过滤）',
  `source` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '来源: GUEST_BUG/GUEST_FEEDBACK/USER_FEEDBACK',
  `source_ref_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '源记录ID（bug.id / project_feedback.id）',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题快照',
  `submitter_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交人展示名',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING'
      COMMENT 'PENDING/ACCEPTED/DECLINED/MERGED',
  `result_ref_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL
      COMMENT '接受/合并后的 Bug ID',
  `decided_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '处理人',
  `decided_at` datetime(3) DEFAULT NULL COMMENT '处理时间',
  `sla_due_at` datetime(3) DEFAULT NULL COMMENT '首次响应截止时间',
  `sla_notified_at` datetime(3) DEFAULT NULL COMMENT '超时提醒发送时间（原子占用，避免重复提醒）',
  `created_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '进入收件箱时间',
  `updated_at` datetime(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3)
      ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_triage_source` (`source`, `source_ref_id`),
  KEY `idx_triage_project_status` (`project_id`, `status`, `created_at`),
  KEY `idx_triage_due` (`status`, `sla_due_at`, `sla_notified_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='分诊收件箱';

-- 首次响应时限（小时）：0 或 NULL 表示不限时。
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project` ADD COLUMN triage_sla_hours INT DEFAULT NULL COMMENT ''分诊首次响应时限（小时）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'project' AND column_name = 'triage_sla_hours');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- 待确认筛选是热路径（列表与统计都按项目过滤 pending）
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `bug` ADD INDEX `idx_bug_project_pending` (`project_id`, `pending`)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'bug' AND index_name = 'idx_bug_project_pending');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `project_feedback` ADD INDEX `idx_feedback_project_status` (`project_id`, `status`)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'project_feedback' AND index_name = 'idx_feedback_project_status');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- 存量：仍在待确认队列里的游客 Bug 与未处理的建议补建分诊条目（幂等）。
INSERT IGNORE INTO triage_item
  (id, project_id, enterprise_id, source, source_ref_id, title, submitter_name, status,
   sla_due_at, created_at, updated_at)
SELECT REPLACE(UUID(), '-', ''), b.project_id, COALESCE(p.enterprise_id, ''),
       'GUEST_BUG', b.id, b.title, b.guest_nickname, 'PENDING',
       NULL, b.created_at, b.created_at
FROM bug b JOIN project p ON p.id = b.project_id
WHERE b.pending = 1 AND b.deleted = 0;

INSERT IGNORE INTO triage_item
  (id, project_id, enterprise_id, source, source_ref_id, title, submitter_name, status,
   sla_due_at, created_at, updated_at)
SELECT REPLACE(UUID(), '-', ''), f.project_id, COALESCE(p.enterprise_id, ''),
       CASE WHEN f.submitter_type = 'GUEST' THEN 'GUEST_FEEDBACK' ELSE 'USER_FEEDBACK' END,
       f.id, f.title, f.guest_nickname, 'PENDING',
       NULL, f.created_at, f.created_at
FROM project_feedback f JOIN project p ON p.id = f.project_id
WHERE f.status = 'OPEN';
