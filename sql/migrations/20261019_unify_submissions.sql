-- 一次性升级脚本，仅用于仍使用三张提交表的旧库；不要对新建库执行。
-- 执行前停止 API、调度器和全部 Worker，备份数据库；执行器必须遇错即停。
-- 保留旧比赛/验题表作为迁移备份，应用不再读取；不要使用 schema.sql 升级旧库。
-- 原普通提交 ID 不变，比赛和验题分配新 ID；映射表用于追溯旧链接。
ALTER TABLE submission
    ADD COLUMN submission_type VARCHAR(32) NOT NULL DEFAULT 'REGULAR' COMMENT '提交类型',
    ADD COLUMN contest_id BIGINT DEFAULT NULL COMMENT '比赛 id',
    ADD COLUMN version INT NOT NULL DEFAULT 1 COMMENT '测评版本',
    ADD KEY idx_submission_contest (contest_id, submission_time),
    ADD KEY idx_submission_type (submission_type, submission_time);

CREATE TABLE submission_legacy_id_map (
    task_type VARCHAR(32) NOT NULL,
    old_id BIGINT NOT NULL,
    new_id BIGINT NOT NULL,
    PRIMARY KEY (task_type, old_id),
    UNIQUE KEY uk_submission_legacy_new_id (new_id)
) ENGINE=InnoDB DEFAULT CHARSET=utf8mb4 COLLATE=utf8mb4_unicode_ci COMMENT='合表迁移的旧提交 ID 映射';

START TRANSACTION;
SET @regular_max = (SELECT COALESCE(MAX(id), 0) FROM submission);
SET @contest_max = (SELECT COALESCE(MAX(id), 0) FROM contest_submission);
INSERT INTO submission_legacy_id_map
SELECT 'CONTEST_SUBMISSION', id, @regular_max + id FROM contest_submission;
INSERT INTO submission_legacy_id_map
SELECT 'PROBLEM_REVIEW', id, @regular_max + @contest_max + id FROM problem_review_submission;

INSERT INTO submission (id, user_id, submission_type, contest_id, version,
    problem_id, language, source_code, status, score, time_ms, memory_kb, compiler_msg, judge_msg, submission_time, source_sha256, judge_start_time, judge_end_time, create_by, create_time, update_by, update_time, remark)
SELECT m.new_id, s.user_id, 'CONTEST', s.contest_id, s.version,
    s.problem_id, s.language, s.source_code, s.status, s.score, s.time_ms, s.memory_kb, s.compiler_msg, s.judge_msg, s.submission_time, s.source_sha256, s.judge_start_time, s.judge_end_time, s.create_by, s.create_time, s.update_by, s.update_time, s.remark
FROM contest_submission s JOIN submission_legacy_id_map m
    ON m.task_type = 'CONTEST_SUBMISSION' AND m.old_id = s.id;

INSERT INTO submission (id, user_id, submission_type, contest_id, version,
    problem_id, language, source_code, status, score, time_ms, memory_kb, compiler_msg, judge_msg, submission_time, source_sha256, judge_start_time, judge_end_time, create_by, create_time, update_by, update_time, remark)
SELECT m.new_id, s.reviewer_id, 'PROBLEM_REVIEW', NULL, 1,
    s.problem_id, s.language, s.source_code, s.status, s.score, s.time_ms, s.memory_kb, s.compiler_msg, s.judge_msg, s.submission_time, s.source_sha256, s.judge_start_time, s.judge_end_time, s.create_by, s.create_time, s.update_by, s.update_time, s.remark
FROM problem_review_submission s JOIN submission_legacy_id_map m
    ON m.task_type = 'PROBLEM_REVIEW' AND m.old_id = s.id;

-- 同步全部历史任务的业务引用；旧队列消息因 business_id 不匹配而无法领取。
UPDATE judge_task t JOIN submission_legacy_id_map m
    ON t.task_type = m.task_type AND t.business_id = m.old_id
SET t.business_id = m.new_id;

-- 以最新任务版本初始化提交轮次，不改变普通提交的 ID 和派生统计关联。
UPDATE submission s JOIN (
    SELECT business_id, MAX(task_version) AS latest_version FROM judge_task GROUP BY business_id
) t ON s.id = t.business_id
SET s.version = GREATEST(s.version, t.latest_version);

-- 停机后遗留的执行中任务回到待投递状态，重新执行次数上限仍由任务配置控制。
UPDATE judge_task SET status = 'PENDING', lease_owner = NULL, lease_expire_time = NULL,
    next_retry_time = NULL, last_dispatch_time = NULL
WHERE status = 'PROCESSING';
UPDATE judge_task SET last_dispatch_time = NULL WHERE status IN ('PENDING', 'RETRY_WAIT');
COMMIT;

-- 部署新 API/Worker 后再启动调度；不要让旧 Worker 继续执行。
-- 三类记录总数应等于原普通记录数加两张旧表记录数。
SELECT submission_type, COUNT(*) AS row_count FROM submission GROUP BY submission_type;
SELECT COUNT(*) AS migrated_row_count FROM submission_legacy_id_map;
