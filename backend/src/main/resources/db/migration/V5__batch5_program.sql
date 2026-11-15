
-- ============================================
-- 第 5 批：项目集（Program）与备份说明（见 docs/BATCH5_ENTERPRISE_SCALE.md）
-- ============================================

-- 项目集：企业下的一组项目（产品线/业务线），支持跨项目里程碑与资源视图
CREATE TABLE IF NOT EXISTS program (
    id VARCHAR(36) PRIMARY KEY COMMENT '项目集ID',
    enterprise_id VARCHAR(36) NOT NULL COMMENT '所属企业',
    name VARCHAR(100) NOT NULL COMMENT '项目集名称',
    description TEXT DEFAULT NULL COMMENT '说明',
    owner_id VARCHAR(36) DEFAULT NULL COMMENT '负责人',
    status VARCHAR(20) NOT NULL DEFAULT 'ACTIVE' COMMENT '状态: ACTIVE/ARCHIVED',
    deleted TINYINT NOT NULL DEFAULT 0 COMMENT '逻辑删除',
    created_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) COMMENT '创建时间',
    updated_at DATETIME(3) DEFAULT CURRENT_TIMESTAMP(3) ON UPDATE CURRENT_TIMESTAMP(3) COMMENT '更新时间',
    KEY idx_enterprise (enterprise_id)
) ENGINE=InnoDB COMMENT='项目集';

-- 项目挂项目集（一个项目至多属于一个项目集）
ALTER TABLE project ADD COLUMN program_id VARCHAR(36) DEFAULT NULL COMMENT '所属项目集（program.id）';
ALTER TABLE project ADD KEY idx_program (program_id);
