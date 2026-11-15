-- ============================================
-- V1：完整基线表结构
-- ============================================
-- 本文件是该库**唯一的表结构权威定义**。内容由 `mysqldump --no-data` 从
-- "执行完第 9 批 schema.sql 的库"导出，因此与历史 schema.sql 逐列等价。
--
-- 为什么要迁到 Flyway（审计报告 §C11）：
--   - schema.sql 里的 ALTER 靠 continue-on-error 吞错，第一次因故失败后
--     约束永久缺失且无人知晓；
--   - 没有版本号，无法回答"这个库执行到哪一步了"；
--   - 多实例启动时 N 个实例并发执行 DDL。
--
-- 为什么全部用 CREATE TABLE IF NOT EXISTS：
--   已有部署的库里表都已存在（历史 schema.sql 建的），首次接入 Flyway 时
--   配了 baseline-on-migrate，V1 在这些库上是空操作；全新库则靠它一次建全。
--   同一个文件对两种库都安全。

CREATE TABLE IF NOT EXISTS `activity_log` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '日志ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作用户ID',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属企业ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目ID',
  `action` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '操作类型',
  `target_type` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标类型',
  `target_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '目标ID',
  `detail` text COLLATE utf8mb4_unicode_ci COMMENT '操作详情',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `prev_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上一条审计记录的哈希（链式防篡改）',
  `record_hash` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '本条记录的哈希',
  `ip` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作来源 IP',
  `user_agent` varchar(300) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '操作来源 UA',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_enterprise_created` (`enterprise_id`,`created_at`),
  KEY `idx_activity_ent_created` (`enterprise_id`,`created_at`),
  KEY `idx_enterprise_id_created` (`enterprise_id`,`id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='操作日志表';
CREATE TABLE IF NOT EXISTS `ai_chat_message` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属用户ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'user' COMMENT '角色: user/assistant',
  `kind` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'CHAT' COMMENT '行为类型: CHAT/REPORT/SUMMARY/TASKS/AGENT/RAG/REVIEW',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `model` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '生成模型（assistant 消息可选）',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_created` (`user_id`,`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='AI 助手消息历史表';
CREATE TABLE IF NOT EXISTS `automation_rule` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '规则名称',
  `trigger_type` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '触发器: BUG_CREATED/BUG_FAIL/CASE_FAIL/TASK_DONE',
  `assign_to_user_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '自动指派给（可空）',
  `notify_user_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '通知给（可空）',
  `enabled` tinyint DEFAULT '1' COMMENT '是否启用',
  `creator_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自动化规则';
CREATE TABLE IF NOT EXISTS `bug` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Bug ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Bug标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT 'Bug描述',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目ID',
  `assignee_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人ID',
  `reporter_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '报告人ID',
  `reporter_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'USER' COMMENT '报告人类型: USER/GUEST',
  `guest_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '游客ID（reporter_type=GUEST时）',
  `guest_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '游客昵称',
  `pending` tinyint NOT NULL DEFAULT '0' COMMENT '游客提交待确认: 0-否 1-待确认',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NEW' COMMENT '状态: NEW/ASSIGNED/DOING/RESOLVED/REOPENED/CLOSED',
  `priority` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级',
  `severity` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MAJOR' COMMENT '严重程度: BLOCKER/CRITICAL/MAJOR/MINOR/TRIVIAL',
  `environment` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '环境信息',
  `fix_version_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修复版本（project_release.id）',
  `screenshots` text COLLATE utf8mb4_unicode_ci COMMENT '截图（JSON 数组，存储 URL 列表）',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观锁版本号（每次更新+1）',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_status` (`status`),
  KEY `idx_assignee` (`assignee_id`),
  KEY `idx_bug_fix_version` (`fix_version_id`),
  KEY `idx_bug_assignee` (`assignee_id`),
  FULLTEXT KEY `ft_bug` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bug表';
CREATE TABLE IF NOT EXISTS `bug_comment` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论ID',
  `bug_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Bug ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID（冗余，便于可见性过滤）',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论人',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_bug` (`bug_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Bug评论';
CREATE TABLE IF NOT EXISTS `chat_channel` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道名称',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型: PROJECT/DEPARTMENT/ENTERPRISE/PRIVATE',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联项目ID',
  `department_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联部门ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天频道表';
CREATE TABLE IF NOT EXISTS `chat_message` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID',
  `channel_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道ID',
  `sender_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发送者ID',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息内容',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEXT' COMMENT '类型: TEXT/IMAGE/FILE/CODE/SYSTEM',
  `reply_to_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复的消息ID',
  `is_edited` tinyint DEFAULT '0' COMMENT '是否已编辑',
  `is_deleted` tinyint DEFAULT '0' COMMENT '是否已删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间（毫秒精度）',
  `edited_at` datetime(3) DEFAULT NULL COMMENT '编辑时间',
  `recalled_at` datetime(3) DEFAULT NULL COMMENT '撤回时间',
  `recalled_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '撤回操作人',
  `original_content` text COLLATE utf8mb4_unicode_ci COMMENT '撤回前的原文（仅审计可见）',
  PRIMARY KEY (`id`),
  KEY `idx_channel_id` (`channel_id`),
  KEY `idx_sender_id` (`sender_id`),
  KEY `idx_created_at` (`created_at`),
  KEY `idx_channel_created` (`channel_id`,`created_at`),
  KEY `idx_msg_channel_created` (`channel_id`,`created_at`),
  FULLTEXT KEY `ft_chat_message` (`content`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='聊天消息表';
CREATE TABLE IF NOT EXISTS `chat_reaction` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `message_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '消息ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `emoji` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '表情（Unicode 字符或短码）',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_message_user_emoji` (`message_id`,`user_id`,`emoji`),
  KEY `idx_message` (`message_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='消息表情回应';
CREATE TABLE IF NOT EXISTS `chat_read_state` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `channel_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '频道ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `last_read_at` datetime(3) NOT NULL COMMENT '已读至该时间点（含）',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_channel_user` (`channel_id`,`user_id`),
  KEY `idx_user` (`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='频道已读位置';
CREATE TABLE IF NOT EXISTS `checklist_item` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '检查项',
  `completed` tinyint DEFAULT '0' COMMENT '是否完成',
  `sort` int DEFAULT '0' COMMENT '排序号',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务检查清单';
CREATE TABLE IF NOT EXISTS `custom_field` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `issue_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用对象: TASK/BUG/REQUIREMENT',
  `field_key` varchar(60) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段标识（项目内同一对象类型唯一）',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型: TEXT/TEXTAREA/NUMBER/DATE/SELECT/MULTI_SELECT/USER/CHECKBOX',
  `options` text COLLATE utf8mb4_unicode_ci COMMENT '选项 JSON 数组（SELECT/MULTI_SELECT 用）',
  `required` tinyint NOT NULL DEFAULT '0' COMMENT '是否必填',
  `default_value` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '默认值',
  `sort` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '字段说明（表单提示）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否启用（停用不删数据）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_type_key` (`project_id`,`issue_type`,`field_key`),
  KEY `idx_project_type` (`project_id`,`issue_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自定义字段定义';
CREATE TABLE IF NOT EXISTS `custom_field_value` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `field_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '字段ID',
  `issue_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作项ID',
  `issue_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作项类型',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID（冗余，便于按项目清理）',
  `value` text COLLATE utf8mb4_unicode_ci COMMENT '值（文本/数字/日期原样存；多选存 JSON 数组）',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_field_issue` (`field_id`,`issue_id`),
  KEY `idx_issue` (`issue_id`,`issue_type`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='自定义字段值';
CREATE TABLE IF NOT EXISTS `department` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门名称',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属企业ID',
  `parent_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '上级部门ID',
  `leader_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部门负责人ID',
  `sort` int DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_parent_id` (`parent_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门表';
CREATE TABLE IF NOT EXISTS `department_member` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `department_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部门ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属企业ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_dept_user` (`department_id`,`user_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_enterprise` (`enterprise_id`),
  KEY `idx_dept_member_ent` (`enterprise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部门成员关联表';
CREATE TABLE IF NOT EXISTS `deploy_environment` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '环境ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '环境名称（如 测试环境/预发/生产）',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TEST' COMMENT '类型: DEV/TEST/STAGING/PROD',
  `requires_approval` tinyint NOT NULL DEFAULT '0' COMMENT '部署是否需要审批',
  `approval_roles` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '可审批的项目角色（逗号分隔，空=OWNER/MANAGER）',
  `url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '环境访问地址',
  `sort` int NOT NULL DEFAULT '0' COMMENT '展示顺序',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署环境';
CREATE TABLE IF NOT EXISTS `deployment` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '部署记录ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `environment_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标环境',
  `release_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '部署的版本（project_release.id）',
  `version` varchar(40) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本号快照（版本被删也不丢历史）',
  `commit_sha` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '对应提交',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING_APPROVAL' COMMENT '状态: PENDING_APPROVAL/APPROVED/DEPLOYING/SUCCEEDED/FAILED/ROLLED_BACK/CANCELLED',
  `triggered_by` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '发起人',
  `approved_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批人',
  `approved_at` datetime DEFAULT NULL COMMENT '审批时间',
  `approval_note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '审批意见',
  `started_at` datetime DEFAULT NULL COMMENT '开始部署时间',
  `finished_at` datetime DEFAULT NULL COMMENT '结束时间',
  `rollback_of` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回滚目标部署记录（本次回滚的是哪次部署）',
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明/失败原因',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  `first_commit_at` datetime DEFAULT NULL COMMENT '本版本首个提交时间（变更前置时间起点）',
  PRIMARY KEY (`id`),
  KEY `idx_project_env` (`project_id`,`environment_id`),
  KEY `idx_project_finished` (`project_id`,`finished_at`),
  KEY `idx_release` (`release_id`),
  KEY `idx_env_finished` (`environment_id`,`finished_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='部署记录';
CREATE TABLE IF NOT EXISTS `design_asset` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设计稿ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `requirement_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联需求',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联任务',
  `provider` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OTHER' COMMENT '来源: FIGMA/LANHU/MASTERGO/OTHER',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设计稿标题',
  `file_key` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部文件标识',
  `node_id` varchar(200) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '外部节点标识',
  `embed_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '嵌入/预览链接',
  `thumbnail_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缩略图链接',
  `is_primary` tinyint NOT NULL DEFAULT '0' COMMENT '是否主设计稿',
  `status` varchar(30) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DESIGNING' COMMENT 'DESIGNING/IN_REVIEW/CHANGES_REQUESTED/APPROVED',
  `current_version` int NOT NULL DEFAULT '1' COMMENT '当前版本号',
  `sort` int NOT NULL DEFAULT '0' COMMENT '排序',
  `created_by` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_requirement` (`requirement_id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_status` (`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设计稿（外部工具指针）';
CREATE TABLE IF NOT EXISTS `design_asset_version` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `asset_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '设计稿ID',
  `version` int NOT NULL COMMENT '版本号',
  `snapshot_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '该版本的预览/快照链接',
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '版本说明',
  `editor_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_asset_version` (`asset_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='设计稿版本快照';
CREATE TABLE IF NOT EXISTS `design_walkthrough` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '走查单ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `requirement_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联需求',
  `design_asset_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '走查依据的设计稿',
  `design_version` int DEFAULT NULL COMMENT '走查依据的设计稿版本（锁定基线）',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '走查单标题',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/CLOSED',
  `created_by` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_requirement` (`requirement_id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI 走查单';
CREATE TABLE IF NOT EXISTS `design_walkthrough_item` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '走查项ID',
  `walkthrough_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属走查单',
  `title` varchar(300) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '走查项（如「按钮间距与设计稿一致」）',
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PENDING' COMMENT '结果: PENDING/PASS/FAIL/NA',
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `screenshot_url` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '截图/证据链接',
  `bug_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '失败项转出的缺陷',
  `sort` int NOT NULL DEFAULT '0' COMMENT '顺序',
  `checked_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '检查人',
  `checked_at` datetime DEFAULT NULL COMMENT '检查时间',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_walkthrough` (`walkthrough_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='UI 走查项';
CREATE TABLE IF NOT EXISTS `document` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档标题',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '文档内容（Markdown）',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目ID',
  `author_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作者ID',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DOC' COMMENT '类型: DOC/WIKI/MEETING/REPORT',
  `parent_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父文档ID',
  `sort` int DEFAULT '0' COMMENT '排序号',
  `is_published` tinyint DEFAULT '0' COMMENT '是否发布',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `version` int DEFAULT '1' COMMENT '版本号（每次保存+1）',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属企业（全局文档按企业隔离）',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_author_id` (`author_id`),
  KEY `idx_parent` (`parent_id`),
  KEY `idx_doc_parent` (`parent_id`),
  FULLTEXT KEY `ft_document` (`title`,`content`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档表';
CREATE TABLE IF NOT EXISTS `document_comment` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论ID',
  `document_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论人',
  `content` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档评论';
CREATE TABLE IF NOT EXISTS `document_comments` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论ID',
  `document_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论人',
  `content` varchar(1000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档评论';
CREATE TABLE IF NOT EXISTS `document_version` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本ID',
  `document_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `version` int NOT NULL COMMENT '版本号（保存前的内容状态）',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题快照',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '内容快照',
  `editor_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编辑人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_doc_ver` (`document_id`,`version`),
  KEY `idx_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档版本快照';
CREATE TABLE IF NOT EXISTS `document_versions` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本ID',
  `document_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文档ID',
  `version` int NOT NULL COMMENT '版本号',
  `title` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题快照',
  `content` longtext COLLATE utf8mb4_unicode_ci COMMENT '内容快照',
  `editor_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编辑人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_doc` (`document_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文档版本快照';
CREATE TABLE IF NOT EXISTS `enterprise` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '企业ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '企业名称',
  `logo` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '企业Logo URL',
  `invite_code` varchar(16) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邀请码',
  `owner_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '企业所有者ID',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除: 0-正常, 1-已删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `wecom_webhook` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '企业微信群机器人 Webhook 地址（空=不推送）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_invite_code` (`invite_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='企业表';
CREATE TABLE IF NOT EXISTS `file_item` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件ID',
  `name` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件名',
  `size` bigint NOT NULL COMMENT '文件大小（字节）',
  `mime_type` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'MIME类型',
  `url` varchar(500) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '文件URL',
  `thumbnail_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '缩略图URL',
  `uploader_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '上传者ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目ID',
  `version` int DEFAULT '1' COMMENT '版本号',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_uploader_id` (`uploader_id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_url` (`url`(191)),
  KEY `idx_file_url` (`url`(191))
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='文件表';
CREATE TABLE IF NOT EXISTS `git_commit` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `sha` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '提交SHA',
  `message` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交说明',
  `author_name` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '作者',
  `web_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '提交链接',
  `pushed_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '推送时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_sha` (`project_id`,`sha`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Git 提交记录';
CREATE TABLE IF NOT EXISTS `notification` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '接收用户ID',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '类型: COMMENT/MENTION/TASK_UPDATE/BUG_UPDATE/SPRINT_UPDATE/SYSTEM/AI',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '通知内容',
  `is_read` tinyint DEFAULT '0' COMMENT '是否已读',
  `action_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '跳转链接',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_is_read` (`is_read`),
  KEY `idx_user_read_created` (`user_id`,`is_read`,`created_at`),
  KEY `idx_user_unread` (`user_id`,`is_read`),
  KEY `idx_notification_created` (`created_at`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知表';
CREATE TABLE IF NOT EXISTS `notification_preference` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '通知类型（与 notification.type 对应）',
  `enabled` tinyint NOT NULL DEFAULT '1' COMMENT '是否接收该类型通知',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_type` (`user_id`,`type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='通知类型偏好';
CREATE TABLE IF NOT EXISTS `program` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目集ID',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属企业',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目集名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '说明',
  `owner_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_enterprise` (`enterprise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目集';
CREATE TABLE IF NOT EXISTS `project` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目名称',
  `project_key` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目标识',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '项目描述',
  `icon` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目图标',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属企业ID',
  `owner_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目负责人ID',
  `invite_code` varchar(16) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '项目邀请码',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLANNING' COMMENT '状态: ACTIVE/ARCHIVED/PLANNING',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `git_repo_url` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Git 仓库地址',
  `git_webhook_secret` varchar(32) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'Git Webhook 密钥',
  `program_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目集（program.id）',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_key` (`enterprise_id`,`project_key`),
  UNIQUE KEY `uk_project_invite_code` (`invite_code`),
  KEY `idx_enterprise_id` (`enterprise_id`),
  KEY `idx_program` (`program_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目表';
CREATE TABLE IF NOT EXISTS `project_feedback` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'SUGGESTION' COMMENT '类型: SUGGESTION/IMPROVEMENT/OTHER',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '内容',
  `submitter_type` varchar(10) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'GUEST' COMMENT '提交者类型: USER/GUEST',
  `submitter_user_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '站内用户ID（submitter_type=USER时）',
  `guest_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '游客ID（submitter_type=GUEST时）',
  `guest_nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '游客昵称',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN' COMMENT '状态: OPEN/ACCEPTED/REJECTED/CONVERTED',
  `converted_bug_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '转化后的Bug ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目建议反馈表';
CREATE TABLE IF NOT EXISTS `project_guest` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '游客ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `token_hash` varchar(128) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '链接token哈希(SHA-256，明文不落库)',
  `nickname` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '游客昵称（提交时自报）',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '可选邮箱',
  `expires_at` datetime DEFAULT NULL COMMENT '过期时间（空=长期有效）',
  `revoked` tinyint DEFAULT '0' COMMENT '是否撤销: 0-有效 1-已撤销',
  `created_by` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '生成人ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_token_hash` (`token_hash`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目游客协作者';
CREATE TABLE IF NOT EXISTS `project_member` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEMBER' COMMENT '项目角色: OWNER/MANAGER/MEMBER',
  `joined_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_user` (`project_id`,`user_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目成员表';
CREATE TABLE IF NOT EXISTS `project_release` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本名称',
  `version` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '版本号（项目内唯一）',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'UNRELEASED' COMMENT '状态: UNRELEASED/RELEASED/ARCHIVED',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '版本说明',
  `release_date` date DEFAULT NULL COMMENT '计划发布日期',
  `released_at` datetime DEFAULT NULL COMMENT '实际发布时间',
  `released_by` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '发布操作人',
  `release_notes` text COLLATE utf8mb4_unicode_ci COMMENT '发布说明（Markdown）',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_project_version` (`project_id`,`version`,`deleted`),
  KEY `idx_project_status` (`project_id`,`status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='项目版本/发布';
CREATE TABLE IF NOT EXISTS `requirement` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需求ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需求标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '需求描述',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目ID',
  `requester_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需求提出人ID',
  `assignee_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '需求负责人',
  `approver_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '当前审批人ID',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'OPEN' COMMENT '状态: 取值来自项目工作流配置（默认 OPEN/COMPLETED/CLOSED）',
  `design_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NONE' COMMENT '设计状态',
  `priority` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级: CRITICAL/HIGH/MEDIUM/LOW',
  `source` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '需求来源: CUSTOMER/INTERNAL/MARKET/TECHNICAL',
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '需求分类: FEATURE/IMPROVEMENT/BUGFIX/OTHER',
  `estimated_hours` decimal(6,1) DEFAULT NULL COMMENT '预估工时（小时，支持小数）',
  `fix_version_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '交付版本（project_release.id）',
  `approved_at` datetime DEFAULT NULL COMMENT '审批通过时间',
  `rejected_at` datetime DEFAULT NULL COMMENT '审批拒绝时间',
  `reject_reason` text COLLATE utf8mb4_unicode_ci COMMENT '拒绝原因',
  `parent_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '父需求ID（用于需求层级）',
  `sort` int DEFAULT '0' COMMENT '排序号',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_requester_id` (`requester_id`),
  KEY `idx_approver_id` (`approver_id`),
  KEY `idx_status` (`status`),
  KEY `idx_priority` (`priority`),
  KEY `idx_parent_id` (`parent_id`),
  KEY `idx_requirement_fix_version` (`fix_version_id`),
  KEY `idx_requirement_assignee` (`assignee_id`),
  FULLTEXT KEY `ft_requirement` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求表';
CREATE TABLE IF NOT EXISTS `requirement_approval` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批记录ID',
  `requirement_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需求ID',
  `approver_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批人ID',
  `approval_level` int NOT NULL DEFAULT '1' COMMENT '审批层级（1,2,3...）',
  `action` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '审批动作: PENDING/APPROVE/REJECT',
  `comment` text COLLATE utf8mb4_unicode_ci COMMENT '审批意见',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_requirement_id` (`requirement_id`),
  KEY `idx_approver_id` (`approver_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求审批记录表';
CREATE TABLE IF NOT EXISTS `requirement_change_log` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '记录ID',
  `requirement_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需求ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `field` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '变更字段',
  `old_value` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '旧值',
  `new_value` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '新值',
  `editor_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '修改人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '变更时间',
  PRIMARY KEY (`id`),
  KEY `idx_req` (`requirement_id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求变更记录';
CREATE TABLE IF NOT EXISTS `requirement_task` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关联ID',
  `requirement_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '需求ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_requirement_task` (`requirement_id`,`task_id`),
  KEY `idx_task_id` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求任务关联表';
CREATE TABLE IF NOT EXISTS `requirement_template` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '模板名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '模板描述',
  `content` text COLLATE utf8mb4_unicode_ci COMMENT '模板内容（JSON格式）',
  `category` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '模板分类',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属企业ID（为空表示系统模板）',
  `is_system` tinyint DEFAULT '0' COMMENT '是否系统模板',
  `sort` int DEFAULT '0' COMMENT '排序号',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_enterprise_id` (`enterprise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='需求模板表';
CREATE TABLE IF NOT EXISTS `saved_filter` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '筛选器ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目；NULL 表示跨项目',
  `owner_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '筛选器名称',
  `entity` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '查询对象: TASK/BUG/REQUIREMENT',
  `expression` varchar(2000) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结构化查询表达式',
  `is_shared` tinyint NOT NULL DEFAULT '0' COMMENT '是否共享给项目成员',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_entity` (`project_id`,`entity`),
  KEY `idx_owner` (`owner_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='保存的筛选器';
CREATE TABLE IF NOT EXISTS `schema_migration` (
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '迁移标识（带日期，唯一）',
  `applied_at` datetime NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '执行时间',
  PRIMARY KEY (`name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='一次性数据迁移登记（保证迁移只执行一次）';
CREATE TABLE IF NOT EXISTS `sprint` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Sprint ID',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'Sprint名称',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目ID',
  `goal` text COLLATE utf8mb4_unicode_ci COMMENT 'Sprint目标',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING/ACTIVE/COMPLETED',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='Sprint表';
CREATE TABLE IF NOT EXISTS `subscription` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '关注人',
  `target_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象类型: TASK/BUG/REQUIREMENT/DOCUMENT',
  `target_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '对象ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目（便于按项目清理）',
  `auto` tinyint NOT NULL DEFAULT '0' COMMENT '是否自动关注（被指派/被提及/创建人）',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '关注时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_user_target` (`user_id`,`target_type`,`target_id`),
  KEY `idx_target` (`target_type`,`target_id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作项关注关系';
CREATE TABLE IF NOT EXISTS `sys_permission` (
  `code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限点，如 bug:create',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限点名称',
  `scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域: ENTERPRISE/PROJECT',
  `description` varchar(255) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '说明',
  `sort` int DEFAULT '0' COMMENT '排序',
  PRIMARY KEY (`code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='权限点注册表';
CREATE TABLE IF NOT EXISTS `sys_role_permission` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT '*' COMMENT '企业ID，* 为全局默认模板',
  `scope` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '作用域: ENTERPRISE/PROJECT',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '角色（企业7级角色 / 项目角色）',
  `permission_code` varchar(64) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '权限点',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_role_perm` (`enterprise_id`,`scope`,`role`,`permission_code`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='角色-权限点映射（INSERT IGNORE 幂等）';
CREATE TABLE IF NOT EXISTS `sys_user` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户ID',
  `username` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用户名',
  `real_name` varchar(50) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '真实姓名（展示用，空则回退用户名）',
  `email` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '邮箱',
  `password` varchar(255) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '密码（BCrypt加密）',
  `avatar` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '头像URL',
  `phone` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '手机号',
  `role` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEMBER' COMMENT '角色: SUPER_ADMIN/ADMIN/MANAGER/MEMBER',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/INACTIVE/SUSPENDED',
  `enterprise_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属企业ID',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `totp_secret` varchar(64) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT 'TOTP 密钥（Base32，启用后才写入）',
  `totp_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用双因素认证',
  `totp_bound_at` datetime DEFAULT NULL COMMENT '绑定时间',
  `must_change_password` tinyint NOT NULL DEFAULT '0' COMMENT '下次登录必须修改密码',
  `password_updated_at` datetime DEFAULT NULL COMMENT '上次改密时间（密码过期策略用）',
  `dnd_start` time DEFAULT NULL COMMENT '免打扰开始时间',
  `dnd_end` time DEFAULT NULL COMMENT '免打扰结束时间',
  `dnd_enabled` tinyint NOT NULL DEFAULT '0' COMMENT '是否启用免打扰',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_username` (`username`),
  UNIQUE KEY `uk_email` (`email`),
  KEY `idx_enterprise_id` (`enterprise_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='用户表';
CREATE TABLE IF NOT EXISTS `task` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务标题',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '任务描述',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目ID',
  `sprint_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属Sprint ID',
  `fix_version_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '修复版本（project_release.id）',
  `assignee_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人ID',
  `reporter_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人ID',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'TODO' COMMENT '状态: TODO/DOING/TESTING/DONE',
  `design_status` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '设计状态',
  `priority` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'MEDIUM' COMMENT '优先级: CRITICAL/HIGH/MEDIUM/LOW',
  `story_points` int DEFAULT NULL COMMENT '故事点',
  `due_date` datetime DEFAULT NULL COMMENT '截止时间',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  `completed_at` datetime DEFAULT NULL COMMENT '完成时间（进入 DONE 分类时写入）',
  `version` int NOT NULL DEFAULT '1' COMMENT '乐观锁版本号（每次更新+1）',
  PRIMARY KEY (`id`),
  KEY `idx_project_id` (`project_id`),
  KEY `idx_sprint_id` (`sprint_id`),
  KEY `idx_assignee_id` (`assignee_id`),
  KEY `idx_status` (`status`),
  KEY `idx_task_fix_version` (`fix_version_id`),
  FULLTEXT KEY `ft_task` (`title`,`description`) /*!50100 WITH PARSER `ngram` */ 
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务表';
CREATE TABLE IF NOT EXISTS `task_checklist` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '清单项ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '内容',
  `done` tinyint DEFAULT '0' COMMENT '是否完成',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务清单项';
CREATE TABLE IF NOT EXISTS `task_comment` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论者ID',
  `content` text COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '评论内容',
  `reply_to_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '回复的评论ID（为空表示顶级评论）',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
  `updated_at` datetime DEFAULT CURRENT_TIMESTAMP ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_task_id` (`task_id`),
  KEY `idx_user_id` (`user_id`),
  KEY `idx_reply_to_id` (`reply_to_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务评论表';
CREATE TABLE IF NOT EXISTS `task_dependency` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '依赖记录ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID（被阻塞方）',
  `depends_on_task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '前置任务ID（阻塞方）',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_dep` (`task_id`,`depends_on_task_id`),
  KEY `idx_dep_on` (`depends_on_task_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务依赖';
CREATE TABLE IF NOT EXISTS `task_tag` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `tag_name` varchar(50) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标签名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_task_tag` (`task_id`,`tag_name`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务标签表';
CREATE TABLE IF NOT EXISTS `task_worklog` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工时记录ID',
  `task_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '任务ID',
  `user_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '登记人',
  `hours` decimal(5,2) NOT NULL COMMENT '工时（小时，两位小数）',
  `work_date` date NOT NULL COMMENT '工作日期',
  `note` varchar(500) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '备注',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  PRIMARY KEY (`id`),
  KEY `idx_task` (`task_id`),
  KEY `idx_user_date` (`user_id`,`work_date`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='任务工时';
CREATE TABLE IF NOT EXISTS `test_case` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用例ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用例标题',
  `precondition` text COLLATE utf8mb4_unicode_ci COMMENT '前置条件',
  `steps` text COLLATE utf8mb4_unicode_ci COMMENT '步骤 JSON [{action, expected}]',
  `expected_result` text COLLATE utf8mb4_unicode_ci COMMENT '预期结果',
  `version` int NOT NULL DEFAULT '1' COMMENT '用例版本号',
  `review_status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'DRAFT' COMMENT '评审状态',
  `priority` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT 'MEDIUM' COMMENT '优先级',
  `requirement_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联需求',
  `creator_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '创建人',
  `deleted` tinyint DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_review_status` (`review_status`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例';
CREATE TABLE IF NOT EXISTS `test_case_version` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `case_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用例ID',
  `version` int NOT NULL COMMENT '版本号',
  `title` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '标题快照',
  `precondition` text COLLATE utf8mb4_unicode_ci COMMENT '前置条件快照',
  `steps` text COLLATE utf8mb4_unicode_ci COMMENT '步骤快照',
  `expected_result` text COLLATE utf8mb4_unicode_ci COMMENT '预期结果快照',
  `editor_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '编辑人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '快照时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_case_version` (`case_id`,`version`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试用例版本快照';
CREATE TABLE IF NOT EXISTS `test_cycle` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试轮次ID',
  `plan_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属测试计划',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '轮次名称',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED/IN_PROGRESS/COMPLETED',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_plan` (`plan_id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试轮次';
CREATE TABLE IF NOT EXISTS `test_cycle_case` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT 'ID',
  `cycle_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试轮次',
  `case_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试用例',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `assignee_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '本轮执行人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_cycle_case` (`cycle_id`,`case_id`),
  KEY `idx_case` (`case_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试轮次包含的用例';
CREATE TABLE IF NOT EXISTS `test_execution` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行记录ID',
  `case_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '用例ID',
  `case_version` int DEFAULT NULL COMMENT '执行时的用例版本号',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '项目ID',
  `cycle_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属测试轮次',
  `result` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '结果: PASS/FAIL/BLOCKED/SKIP',
  `note` varchar(1000) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '执行备注',
  `bug_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联缺陷（失败转缺陷时回填）',
  `executor_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '执行人',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '执行时间',
  PRIMARY KEY (`id`),
  KEY `idx_case` (`case_id`),
  KEY `idx_project` (`project_id`),
  KEY `idx_execution_cycle` (`cycle_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试执行记录';
CREATE TABLE IF NOT EXISTS `test_plan` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '测试计划ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属项目',
  `name` varchar(200) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '计划名称',
  `description` text COLLATE utf8mb4_unicode_ci COMMENT '计划说明',
  `status` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING/ACTIVE/COMPLETED',
  `owner_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '负责人',
  `release_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '关联版本',
  `start_date` date DEFAULT NULL COMMENT '开始日期',
  `end_date` date DEFAULT NULL COMMENT '结束日期',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project` (`project_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='测试计划';
CREATE TABLE IF NOT EXISTS `workflow` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流ID',
  `project_id` varchar(36) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '所属项目；NULL 表示全局默认模板',
  `name` varchar(100) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '工作流名称',
  `issue_type` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '适用问题类型: TASK/BUG/REQUIREMENT',
  `is_default` tinyint NOT NULL DEFAULT '0' COMMENT '是否该项目该类型的默认工作流',
  `deleted` tinyint NOT NULL DEFAULT '0' COMMENT '逻辑删除',
  `created_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
  `updated_at` datetime(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
  PRIMARY KEY (`id`),
  KEY `idx_project_type` (`project_id`,`issue_type`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流定义';
CREATE TABLE IF NOT EXISTS `workflow_status` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态ID',
  `workflow_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属工作流',
  `code` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '状态码（存进业务表 status 字段的值）',
  `name` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '展示名',
  `category` varchar(20) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '归一分类: TODO/IN_PROGRESS/DONE（报表口径依赖它）',
  `color` varchar(20) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '看板列颜色',
  `sort` int NOT NULL DEFAULT '0' COMMENT '看板列顺序',
  `is_initial` tinyint NOT NULL DEFAULT '0' COMMENT '是否新建时的初始状态',
  `is_terminal` tinyint NOT NULL DEFAULT '0' COMMENT '是否终态',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_workflow_code` (`workflow_id`,`code`),
  KEY `idx_workflow_sort` (`workflow_id`,`sort`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流状态';
CREATE TABLE IF NOT EXISTS `workflow_transition` (
  `id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '流转ID',
  `workflow_id` varchar(36) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '所属工作流',
  `from_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '起始状态码',
  `to_status` varchar(40) COLLATE utf8mb4_unicode_ci NOT NULL COMMENT '目标状态码',
  `name` varchar(60) COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT '流转动作名',
  PRIMARY KEY (`id`),
  UNIQUE KEY `uk_transition` (`workflow_id`,`from_status`,`to_status`),
  KEY `idx_workflow` (`workflow_id`)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='工作流流转规则';
