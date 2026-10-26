-- 已有数据库执行一次；历史提交保持 NULL，不回填随机 UUID。
-- 先完成迁移，再部署要求 requestId 的 API 和对应前端。
ALTER TABLE submission
    ADD COLUMN request_id CHAR(36) DEFAULT NULL COMMENT '逻辑提交 UUID，历史及内部提交可为空',
    ADD COLUMN request_sha256 CHAR(64) DEFAULT NULL COMMENT '原始请求入口及内容的 SHA-256',
    ADD UNIQUE KEY uk_submission_user_request (user_id, request_id);
