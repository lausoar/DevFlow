-- ============================================
-- V11：AI 答案引用溯源（审计报告 §9.8）
-- ============================================
-- 之前 AI 回答只有正文，sources 只在响应里出现一次，前端既不渲染也不保存。
-- 用户无法核对"这句话出自哪篇文档"，企业场景下答案就不能用于决策。
--
-- 这里给两类消息表补 sources 列：存 AI 服务返回的来源数组（JSON 文本，
-- 最多 20 条、每条正文截断 500 字）。旧消息该列为 NULL，前端按"无来源"渲染。

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `ai_chat_message` ADD COLUMN sources TEXT COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''引用来源 JSON 数组''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'ai_chat_message' AND column_name = 'sources');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `chat_message` ADD COLUMN sources TEXT COLLATE utf8mb4_unicode_ci DEFAULT NULL COMMENT ''引用来源 JSON 数组（@AI 回复）''', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'chat_message' AND column_name = 'sources');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;

-- 索引命令要带上文档版本：来源需要能回答"答案基于哪一版"。
SET @ddl := (SELECT IF(COUNT(*) = 0,
    'ALTER TABLE `document_index_outbox` ADD COLUMN document_version INT NOT NULL DEFAULT 0 COMMENT ''索引时的文档版本'' AFTER doc_type', 'DO 0')
  FROM information_schema.columns
  WHERE table_schema = DATABASE() AND table_name = 'document_index_outbox' AND column_name = 'document_version');
PREPARE devflow_stmt FROM @ddl;
EXECUTE devflow_stmt;
DEALLOCATE PREPARE devflow_stmt;
