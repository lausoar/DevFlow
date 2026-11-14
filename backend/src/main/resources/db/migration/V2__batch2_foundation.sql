-- ============================================
-- V2：第 2 批地基改造 —— 新建表部分
-- ============================================
-- 说明：本文件只包含**幂等的新建表**语句（CREATE TABLE IF NOT EXISTS），
-- 因此无论表是否已由 schema.sql 建好，重复执行都安全。
--
-- 本批涉及的 **列级变更**（ALTER TABLE ADD COLUMN）暂仍放在 schema.sql 末尾执行：
-- 那是全项目既有的迁移方式（spring.sql.init + continue-on-error），
-- 在一次"导出基线 + 清空历史表"的切换完成前，两边同时存在才有过渡空间。
-- 切换步骤见 db/migration/V1__baseline.sql 顶部注释。

-- 可配置工作流
CREATE TABLE IF NOT EXISTS workflow (
    id VARCHAR(36) PRIMARY KEY COMMENT '工作流ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目；NULL 表示全局默认模板',
    name VARCHAR(100) NOT NULL COMMENT '工作流名称',
    issue_type VARCHAR(20) NOT NULL COMMENT '适用问题类型: TASK/BUG/REQUIREMENT',
    is_default TINYINT NOT NULL DEFAULT 0 COMMENT '是否该项目该类型的默认工作流',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project_type (project_id, issue_type)
) ENGINE=InnoDB COMMENT='工作流定义';

CREATE TABLE IF NOT EXISTS workflow_status (
    id VARCHAR(36) PRIMARY KEY COMMENT '状态ID',
    workflow_id VARCHAR(36) NOT NULL COMMENT '所属工作流',
    code VARCHAR(40) NOT NULL COMMENT '状态码（存进业务表 status 字段的值）',
    name VARCHAR(40) NOT NULL COMMENT '展示名',
    category VARCHAR(20) NOT NULL COMMENT '归一分类: TODO/IN_PROGRESS/DONE（报表口径依赖它）',
    color VARCHAR(20) DEFAULT NULL COMMENT '看板列颜色',
    sort INT NOT NULL DEFAULT 0 COMMENT '看板列顺序',
    is_initial TINYINT NOT NULL DEFAULT 0 COMMENT '是否新建时的初始状态',
    is_terminal TINYINT NOT NULL DEFAULT 0 COMMENT '是否终态',
    UNIQUE KEY uk_workflow_code (workflow_id, code),
    KEY idx_workflow_sort (workflow_id, sort)
) ENGINE=InnoDB COMMENT='工作流状态';

CREATE TABLE IF NOT EXISTS workflow_transition (
    id VARCHAR(36) PRIMARY KEY COMMENT '流转ID',
    workflow_id VARCHAR(36) NOT NULL COMMENT '所属工作流',
    from_status VARCHAR(40) NOT NULL COMMENT '起始状态码',
    to_status VARCHAR(40) NOT NULL COMMENT '目标状态码',
    name VARCHAR(60) DEFAULT NULL COMMENT '流转动作名',
    UNIQUE KEY uk_transition (workflow_id, from_status, to_status),
    KEY idx_workflow (workflow_id)
) ENGINE=InnoDB COMMENT='工作流流转规则';

-- 版本 / 发布
CREATE TABLE IF NOT EXISTS project_release (
    id VARCHAR(36) PRIMARY KEY COMMENT '版本ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(100) NOT NULL COMMENT '版本名称',
    version VARCHAR(40) NOT NULL COMMENT '版本号（项目内唯一）',
    status VARCHAR(20) NOT NULL DEFAULT 'UNRELEASED' COMMENT '状态: UNRELEASED/RELEASED/ARCHIVED',
    description TEXT DEFAULT NULL COMMENT '版本说明',
    release_date DATE DEFAULT NULL COMMENT '计划发布日期',
    released_at DATETIME DEFAULT NULL COMMENT '实际发布时间',
    released_by VARCHAR(36) DEFAULT NULL COMMENT '发布操作人',
    release_notes TEXT DEFAULT NULL COMMENT '发布说明（Markdown）',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    UNIQUE KEY uk_project_version (project_id, version, deleted),
    KEY idx_project_status (project_id, status)
) ENGINE=InnoDB COMMENT='项目版本/发布';

-- 测试计划与轮次
CREATE TABLE IF NOT EXISTS test_plan (
    id VARCHAR(36) PRIMARY KEY COMMENT '测试计划ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(200) NOT NULL COMMENT '计划名称',
    description TEXT DEFAULT NULL COMMENT '计划说明',
    status VARCHAR(20) NOT NULL DEFAULT 'PLANNING' COMMENT '状态: PLANNING/ACTIVE/COMPLETED',
    owner_id VARCHAR(36) DEFAULT NULL COMMENT '负责人',
    release_id VARCHAR(36) DEFAULT NULL COMMENT '关联版本',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='测试计划';

CREATE TABLE IF NOT EXISTS test_cycle (
    id VARCHAR(36) PRIMARY KEY COMMENT '测试轮次ID',
    plan_id VARCHAR(36) NOT NULL COMMENT '所属测试计划',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    name VARCHAR(200) NOT NULL COMMENT '轮次名称',
    status VARCHAR(20) NOT NULL DEFAULT 'NOT_STARTED' COMMENT '状态: NOT_STARTED/IN_PROGRESS/COMPLETED',
    start_date DATE DEFAULT NULL COMMENT '开始日期',
    end_date DATE DEFAULT NULL COMMENT '结束日期',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_plan (plan_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='测试轮次';

CREATE TABLE IF NOT EXISTS test_cycle_case (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    cycle_id VARCHAR(36) NOT NULL COMMENT '测试轮次',
    case_id VARCHAR(36) NOT NULL COMMENT '测试用例',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    assignee_id VARCHAR(36) DEFAULT NULL COMMENT '本轮执行人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '加入时间',
    UNIQUE KEY uk_cycle_case (cycle_id, case_id),
    KEY idx_case (case_id)
) ENGINE=InnoDB COMMENT='测试轮次包含的用例';

CREATE TABLE IF NOT EXISTS test_case_version (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    case_id VARCHAR(36) NOT NULL COMMENT '用例ID',
    version INT NOT NULL COMMENT '版本号',
    title VARCHAR(200) NOT NULL COMMENT '标题快照',
    precondition TEXT COMMENT '前置条件快照',
    steps TEXT COMMENT '步骤快照',
    expected_result TEXT COMMENT '预期结果快照',
    editor_id VARCHAR(36) NOT NULL COMMENT '编辑人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '快照时间',
    UNIQUE KEY uk_case_version (case_id, version)
) ENGINE=InnoDB COMMENT='测试用例版本快照';

-- 设计稿协作
CREATE TABLE IF NOT EXISTS design_asset (
    id VARCHAR(36) PRIMARY KEY COMMENT '设计稿ID',
    project_id VARCHAR(36) NOT NULL COMMENT '所属项目',
    requirement_id VARCHAR(36) DEFAULT NULL COMMENT '关联需求',
    task_id VARCHAR(36) DEFAULT NULL COMMENT '关联任务',
    provider VARCHAR(30) NOT NULL DEFAULT 'OTHER' COMMENT '来源: FIGMA/LANHU/MASTERGO/OTHER',
    title VARCHAR(200) NOT NULL COMMENT '设计稿标题',
    file_key VARCHAR(200) DEFAULT NULL COMMENT '外部文件标识',
    node_id VARCHAR(200) DEFAULT NULL COMMENT '外部节点标识',
    embed_url VARCHAR(1000) DEFAULT NULL COMMENT '嵌入/预览链接',
    thumbnail_url VARCHAR(1000) DEFAULT NULL COMMENT '缩略图链接',
    is_primary TINYINT NOT NULL DEFAULT 0 COMMENT '是否主设计稿',
    status VARCHAR(30) NOT NULL DEFAULT 'DESIGNING' COMMENT 'DESIGNING/IN_REVIEW/CHANGES_REQUESTED/APPROVED',
    current_version INT NOT NULL DEFAULT 1 COMMENT '当前版本号',
    sort INT NOT NULL DEFAULT 0 COMMENT '排序',
    created_by VARCHAR(36) NOT NULL COMMENT '创建人',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_requirement (requirement_id),
    KEY idx_project (project_id)
) ENGINE=InnoDB COMMENT='设计稿（外部工具指针）';

CREATE TABLE IF NOT EXISTS design_asset_version (
    id VARCHAR(36) PRIMARY KEY COMMENT 'ID',
    asset_id VARCHAR(36) NOT NULL COMMENT '设计稿ID',
    version INT NOT NULL COMMENT '版本号',
    snapshot_url VARCHAR(1000) DEFAULT NULL COMMENT '该版本的预览/快照链接',
    note VARCHAR(500) DEFAULT NULL COMMENT '版本说明',
    editor_id VARCHAR(36) NOT NULL COMMENT '提交人',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    UNIQUE KEY uk_asset_version (asset_id, version)
) ENGINE=InnoDB COMMENT='设计稿版本快照';

-- 保存的筛选器
CREATE TABLE IF NOT EXISTS saved_filter (
    id VARCHAR(36) PRIMARY KEY COMMENT '筛选器ID',
    project_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目；NULL 表示跨项目',
    owner_id VARCHAR(36) NOT NULL COMMENT '创建人',
    name VARCHAR(100) NOT NULL COMMENT '筛选器名称',
    entity VARCHAR(20) NOT NULL COMMENT '查询对象: TASK/BUG/REQUIREMENT',
    expression VARCHAR(2000) NOT NULL COMMENT '结构化查询表达式',
    is_shared TINYINT NOT NULL DEFAULT 0 COMMENT '是否共享给项目成员',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_project_entity (project_id, entity),
    KEY idx_owner (owner_id)
) ENGINE=InnoDB COMMENT='保存的筛选器';
