-- ============================================
-- V6：回填缺失的 Git Webhook 密钥
-- ============================================
-- 背景：
--   V4 里有过一次 webhook 密钥回填，但它被 `schema_migration` 标记包住 ——
--   标记一旦写入，这段 UPDATE 就再也不会执行。而创建项目的代码路径
--   当时没有生成密钥（已在 ProjectController 里修好），于是：
--     · 老库：只有迁移那一刻存在的项目补到了密钥；
--     · 之后新建的项目：git_webhook_secret 一直是 NULL；
--     · 跨库恢复/导入的数据：同样为 NULL。
--   NULL 的后果是前端拼出的 webhook 地址为 `?secret=null`，
--   代码托管平台配好 webhook 也永远 401，Git 集成对该项目完全不可用。
--
-- 这里不再用标记：条件本身就是幂等的（只有 NULL 才写），
-- 每次迁移执行都会把当时缺失的补齐。写入的仍是 32 位十六进制，
-- 与 project.git_webhook_secret 的 varchar(32) 一致。
-- ============================================

UPDATE project
SET git_webhook_secret = LOWER(REPLACE(UUID(), '-', ''))
WHERE git_webhook_secret IS NULL;
