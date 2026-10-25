-- 排行榜归档：历史提交序号是比赛内按 submission.id 离散化得到的顺序。
CREATE TABLE IF NOT EXISTS contest_submission_history (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contest_id BIGINT NOT NULL COMMENT '比赛 id',
    sequence_no BIGINT NOT NULL COMMENT '比赛内从 1 开始的连续提交序号',
    submission_id BIGINT NOT NULL COMMENT '原始提交 id',
    user_id BIGINT NOT NULL,
    problem_id BIGINT NOT NULL,
    elapsed_millis BIGINT NOT NULL COMMENT '距离比赛开始的毫秒数',
    status VARCHAR(50) NOT NULL COMMENT '最终判题结果',
    UNIQUE KEY uk_history_sequence (contest_id, sequence_no),
    UNIQUE KEY uk_history_submission (contest_id, submission_id),
    KEY idx_history_time (contest_id, elapsed_millis, sequence_no)
) COMMENT='比赛最终历史提交';

CREATE TABLE IF NOT EXISTS contest_rank_snapshot (
    id BIGINT NOT NULL AUTO_INCREMENT PRIMARY KEY,
    contest_id BIGINT NOT NULL,
    last_sequence BIGINT NOT NULL COMMENT '快照已应用的历史提交序号，0 为初始榜单',
    elapsed_millis BIGINT NOT NULL COMMENT '最后一份历史提交的相对毫秒数',
    rows_json LONGTEXT NOT NULL COMMENT '整张榜单，题格按 sortOrder 排列',
    UNIQUE KEY uk_snapshot_sequence (contest_id, last_sequence)
) COMMENT='比赛排行榜完整快照';
