-- 已有数据库的一次性结构迁移。先停止 Server 和 Judge，备份数据库和数据目录。
-- 不要用 schema.sql 迁移已有数据库，该文件包含 DROP TABLE。
-- 本脚本不删除数据、不移动文件；旧暂存数据需要管理员先处理，见 docs/problem-management.md。
USE `goj`;

-- 只允许已经拥有正式版本的数据进入无生命周期状态的新结构。
-- DDL 会隐式提交，因此预检必须在 ALTER TABLE 之前完成。
DELIMITER $$
DROP PROCEDURE IF EXISTS `migrate_problem_admin_data`$$
CREATE PROCEDURE `migrate_problem_admin_data`()
BEGIN
    IF EXISTS (
        SELECT 1 FROM `problem_test_data`
        WHERE `version` IS NULL OR `version` <= 0
           OR `status` NOT IN ('READY', 'RETIRED')
           OR `storage_path` IS NULL OR `storage_path` LIKE 'staging/%'
    ) THEN
        SIGNAL SQLSTATE '45000'
            SET MESSAGE_TEXT = '请先处理未完成上传、无版本号或暂存目录中的旧测试数据，再执行迁移';
    END IF;

    ALTER TABLE `problem_test_data`
        ADD COLUMN `archive_path` VARCHAR(512) DEFAULT NULL COMMENT '原始 ZIP 的相对路径，历史数据可为空' AFTER `archive_name`,
        MODIFY COLUMN `version` INT UNSIGNED NOT NULL COMMENT '管理员指定的测试数据版本',
        DROP COLUMN `status`;

    -- 待审核、已退回、准备中都归入不公开的草稿，原作者信息保留。
    UPDATE `problem` SET `status` = 0 WHERE `status` IN (3, 4, 5);
    ALTER TABLE `problem`
        MODIFY COLUMN `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '题目状态：0 草稿，1 已发布，2 已停用';
    ALTER TABLE `problem_review_submission`
        MODIFY COLUMN `problem_id` BIGINT NOT NULL COMMENT '题目 id';
END$$
DELIMITER ;

CALL `migrate_problem_admin_data`();
DROP PROCEDURE `migrate_problem_admin_data`;
