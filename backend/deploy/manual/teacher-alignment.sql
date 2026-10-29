-- 仅供人工审阅：本轮没有执行，不由应用自动加载。
-- 前提：备份 app 表，确认选中正确数据库并检查 SHOW COLUMNS FROM app。
-- 以下 ALTER 只适用于现有表尚未包含这些列的情况；已经有列时不要重复执行。
-- MySQL DDL 会隐式提交，不应把事务回滚当作恢复手段。
ALTER TABLE app
    ADD COLUMN deploy_status TINYINT NOT NULL DEFAULT 0 COMMENT '0未发布，1已发布',
    ADD COLUMN app_url VARCHAR(2048) NULL,
    ADD COLUMN create_time DATETIME NULL,
    ADD COLUMN update_time DATETIME NULL,
    ADD COLUMN deploy_time DATETIME NULL;

CREATE TABLE IF NOT EXISTS chat_history (
    id BIGINT NOT NULL AUTO_INCREMENT,
    app_id BIGINT NOT NULL,
    msg_role TINYINT NOT NULL COMMENT '0用户，1助手',
    content MEDIUMTEXT NOT NULL,
    chat_time DATETIME NOT NULL,
    PRIMARY KEY (id),
    INDEX idx_chat_history_app_id_id (app_id, id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4;

-- 若表已存在，仍须人工核对结构；IF NOT EXISTS 不会修复旧表结构。
-- 迁移经授权、备份、验证后，才能另行配置：
-- app.features.extended-schema=true
-- app.features.chat-history=true
