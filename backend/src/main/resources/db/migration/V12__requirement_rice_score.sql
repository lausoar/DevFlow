-- ============================================
-- V12：需求 RICE 评分（审计报告 §1.8）
-- ============================================
-- `requirement.sort` 一直是个死字段：没有任何接口写入或读取，列表永远按创建时间排序，
-- 演示数据却宣称"支持按价值/成本打分"。这里用四个可解释的输入替代它：
--   reach（触达人数） × impact（影响程度） × confidence（置信度%） ÷ effort（人日）
--
-- rice_score 用 STORED 生成列：写入由数据库保证一致，列表排序与四象限都能走索引，
-- 不需要服务端每次重算，也不会出现"输入改了分数却没变"的漂移。

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN reach INT DEFAULT NULL COMMENT ''RICE 触达人数''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'reach');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN impact DECIMAL(3,2) DEFAULT NULL COMMENT ''RICE 影响程度 0.25-3''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'impact');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN confidence INT DEFAULT NULL COMMENT ''RICE 置信度百分比 1-100''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'confidence');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN effort DECIMAL(4,1) DEFAULT NULL COMMENT ''RICE 工作量（人日）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'effort');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- 三个输入齐全且 effort > 0 才有分数；否则为 NULL，列表里显示"未评分"而不是 0。
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD COLUMN rice_score DECIMAL(12,1) GENERATED ALWAYS AS (CASE WHEN reach IS NULL OR impact IS NULL OR confidence IS NULL OR effort IS NULL OR effort <= 0 THEN NULL ELSE ROUND(reach * impact * (confidence / 100) / effort, 1) END) STORED COMMENT ''RICE 分数（生成列）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND column_name = 'rice_score');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `requirement` ADD INDEX `idx_requirement_rice_score` (`project_id`, `rice_score`)', 'DO 0')
  FROM information_schema.statistics
  WHERE table_schema = DATABASE() AND table_name = 'requirement' AND index_name = 'idx_requirement_rice_score');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;
