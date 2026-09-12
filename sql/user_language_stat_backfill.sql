USE `goj`;

-- 根据 submission 原始历史重建用户语言统计。
-- 该脚本只统计已经进入终态的普通提交，可以重复执行。
INSERT INTO `user_language_stat` (`user_id`,
                                  `language`,
                                  `submission_count`,
                                  `accepted_count`,
                                  `create_by`)
SELECT `user_id`,
       `language`,
       COUNT(*)                                               AS `submission_count`,
       SUM(CASE WHEN `status` = 'Accepted' THEN 1 ELSE 0 END) AS `accepted_count`,
       'system'
FROM `submission`
WHERE `status` IN (
                   'Compile Error',
                   'Wrong Answer',
                   'Accepted',
                   'Time Limit Exceeded',
                   'Memory Limit Exceeded',
                   'Runtime Error',
                   'system error'
    )
GROUP BY `user_id`, `language`
ON DUPLICATE KEY UPDATE `submission_count` = VALUES(`submission_count`),
                        `accepted_count`   = VALUES(`accepted_count`),
                        `update_by`        = 'system',
                        `update_time`      = CURRENT_TIMESTAMP;
