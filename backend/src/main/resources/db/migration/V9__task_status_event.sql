-- V9: immutable task status transitions for accurate burndown and completion trends.
CREATE TABLE IF NOT EXISTS task_status_event (
    id VARCHAR(36) PRIMARY KEY COMMENT '状态事件ID',
    task_id VARCHAR(36) NOT NULL COMMENT '任务ID',
    project_id VARCHAR(36) NOT NULL COMMENT '项目ID',
    from_status VARCHAR(40) DEFAULT NULL COMMENT '变更前状态（回填时为NULL）',
    to_status VARCHAR(40) NOT NULL COMMENT '变更后状态',
    from_category VARCHAR(20) DEFAULT NULL COMMENT '变更前状态分类',
    to_category VARCHAR(20) NOT NULL COMMENT '变更后状态分类',
    actor_id VARCHAR(36) DEFAULT NULL COMMENT '操作人ID（系统/历史回填为空）',
    created_at DATETIME(3) NOT NULL DEFAULT CURRENT_TIMESTAMP(3) COMMENT '状态变更时间',
    KEY idx_task_status_event_task_time (task_id, created_at),
    KEY idx_task_status_event_project_time (project_id, created_at)
) ENGINE=InnoDB COMMENT='任务状态变更事件（追加式，不更新历史）';

-- Existing DONE tasks get one completion event. Earlier reopen intervals cannot be reconstructed.
INSERT INTO task_status_event
    (id, task_id, project_id, from_status, to_status, from_category, to_category, actor_id, created_at)
SELECT REPLACE(UUID(), '-', ''), t.id, t.project_id, NULL, t.status, NULL, 'DONE', NULL, t.completed_at
FROM task t
WHERE t.completed_at IS NOT NULL
  AND NOT EXISTS (SELECT 1 FROM task_status_event e WHERE e.task_id = t.id);
