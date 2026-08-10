USE `goj`;

-- 开发环境初始化数据。首次登录后请修改管理员密码。
INSERT IGNORE INTO `sys_role` (`id`, `status`, `role_name`, `role_code`) VALUES
    (1, 1, '超级管理员', 'SUPER_ADMIN'),
    (2, 1, '普通用户', 'USER');

INSERT IGNORE INTO `sys_permission` (`id`, `permission_name`, `permission_code`) VALUES
    (1, '用户管理', 'system:user:manage'),
    (2, '角色管理', 'system:role:manage'),
    (3, '权限管理', 'system:permission:manage'),
    (4, '查看题目', 'problem:view'),
    (5, '题目管理', 'problem:manage'),
    (6, '提交代码', 'submission:create'),
    (7, '查看自己的提交', 'submission:view:self'),
    (8, '提交记录管理', 'submission:manage');

-- 用户名：admin
-- 密码：Admin@123456
INSERT IGNORE INTO `sys_user` (
    `id`, `username`, `password`, `status`, `email`,
    `create_by`, `create_time`, `remark`
) VALUES (
    1,
    'admin',
    '$2a$10$e6diKfs0Y8Z5ynS9buzAducKjh4TPLG.FKC6MMV60Yl5zsS2WAa6m',
    1,
    'admin@goj.local',
    'system',
    CURRENT_TIMESTAMP,
    '开发环境初始管理员'
);

-- 根据用户名和角色编码查询真实 ID，再建立用户与角色的关联。
INSERT IGNORE INTO `sys_user_role` (`user_id`, `role_id`)
SELECT u.`id`, r.`id`
FROM `sys_user` u
JOIN `sys_role` r ON r.`role_code` = 'SUPER_ADMIN'
WHERE u.`username` = 'admin';

-- 给超级管理员分配当前已有的全部权限。
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
CROSS JOIN `sys_permission` p
WHERE r.`role_code` = 'SUPER_ADMIN';

-- 给普通用户分配查看题目、提交代码和查看本人提交的权限。
INSERT IGNORE INTO `sys_role_permission` (`role_id`, `permission_id`)
SELECT r.`id`, p.`id`
FROM `sys_role` r
JOIN `sys_permission` p
    ON p.`permission_code` IN (
        'problem:view',
        'submission:create',
        'submission:view:self'
    )
WHERE r.`role_code` = 'USER';

-- =========================
-- 题目标签初始化数据
-- =========================

-- 在全新执行 schema.sql 后，标签 ID 会按下面的值固定为 1-8。
INSERT IGNORE INTO `tag` (`id`, `tag_name`, `create_by`, `remark`) VALUES
    (1, '数据结构', 'system', '开发环境初始化标签'),
    (2, '动态规划', 'system', '开发环境初始化标签'),
    (3, '模拟', 'system', '开发环境初始化标签'),
    (4, '字符串', 'system', '开发环境初始化标签'),
    (5, '暴力', 'system', '开发环境初始化标签'),
    (6, '构造', 'system', '开发环境初始化标签'),
    (7, '入门', 'system', '开发环境初始化标签'),
    (8, '数学', 'system', '开发环境初始化标签');

-- =========================
-- 简单题目初始化数据
-- =========================

-- 这组数据用于全新初始化，题目 ID 固定为 1-6，管理员 ID 固定为 1。
-- 难度 1 表示简单，状态 1 表示已发布。
INSERT IGNORE INTO `problem` (
    `id`, `problem_name`, `time_limit`, `memory_limit`,
    `description`, `input_description`, `output_description`,
    `input_example`, `output_example`, `example_note`,


    `difficulty`, `author_id`, `status`, `create_by`, `remark`
) VALUES
    (
        1, 'A+B Problem', 1000, 131072,
        '给定两个整数 A 和 B，计算它们的和。整数范围为 -1000000 到 1000000。',
        '一行输入两个整数 A 和 B，中间用一个空格分隔。',
        '输出 A+B 的结果。',
        '1 2', '3', '1+2=3。',
        1, 1, 1, 'system', '开发环境初始化题目'
    ),
    (
        2, '数组求和', 1000, 131072,
        '给定 n 个整数，计算它们的总和。',
        '一行输入 n 和 n 个整数，所有整数之间用空格分隔。1<=n<=1000。',
        '输出这 n 个整数的总和。',
        '5 1 2 3 4 5', '15', '1+2+3+4+5=15。',
        1, 1, 1, 'system', '开发环境初始化题目'
    ),
    (
        3, '反转字符串', 1000, 131072,
        '给定一个只包含小写英文字母的字符串，将字符串反转后输出。',
        '输入一行字符串 S，字符串长度不超过 1000。',
        '输出反转后的字符串。',
        'codex', 'xedoc', '将 codex 从后向前排列得到 xedoc。',
        1, 1, 1, 'system', '开发环境初始化题目'
    ),
    (
        4, '斐波那契数', 1000, 131072,
        '斐波那契数列满足 F1=1、F2=1、Fn=F(n-1)+F(n-2)，请计算第 n 项。',
        '输入一个整数 n，1<=n<=40。',
        '输出斐波那契数列的第 n 项。',
        '5', '5', '数列前五项为 1、1、2、3、5。',
        1, 1, 1, 'system', '开发环境初始化题目'
    ),
    (
        5, '机器人移动', 1000, 131072,
        '机器人初始位于数轴原点。指令 L 表示向左移动一格，指令 R 表示向右移动一格，请计算执行全部指令后机器人的位置。',
        '输入一行只包含字符 L 和 R 的指令串，长度不超过 1000。',
        '输出一个整数，表示机器人最终所在的位置。',
        'LRRRL', '1', '机器人共向左移动 2 格、向右移动 3 格，最终位于 1。',
        1, 1, 1, 'system', '开发环境初始化题目'
    ),
    (
        6, '奇数在前偶数在后', 1000, 131072,
        '给定正整数 n，请构造一个 1 到 n 的排列，使所有奇数按升序排在前面，所有偶数按升序排在后面。',
        '输入一个整数 n，1<=n<=1000。',
        '输出 n 个整数，相邻整数之间用一个空格分隔。',
        '5', '1 3 5 2 4', '1、3、5 是升序奇数，2、4 是升序偶数。',
        1, 1, 1, 'system', '开发环境初始化题目'
    );

-- =========================
-- 题目与标签的关联数据
-- =========================

-- problem_id 是题目 ID，tag_id 是标签 ID。
-- 例如 (1, 1, 8) 表示：关联记录 1，把题目 1 关联到标签 8（数学）。
-- =========================
-- problem_test_data seed metadata
-- =========================
-- The hidden test files live under storage_path/test1..testN.
-- archive_name is a logical development dataset name; no archive hash is set
-- because the current seed uses the extracted directory directly.
INSERT IGNORE INTO `problem_test_data` (
    `id`, `problem_id`, `version`, `test_node_count`,
    `archive_name`, `storage_path`, `archive_sha256`,
    `status`, `active`, `create_by`, `remark`
) VALUES
    (1, 1, 1, 3, 'p1-testdata-v1', '/srv/goj-data/testData/p1', NULL, 'READY', 1, 'system', 'seed test data directory'),
    (2, 2, 1, 3, 'p2-testdata-v1', '/srv/goj-data/testData/p2', NULL, 'READY', 1, 'system', 'seed test data directory'),
    (3, 3, 1, 3, 'p3-testdata-v1', '/srv/goj-data/testData/p3', NULL, 'READY', 1, 'system', 'seed test data directory'),
    (4, 4, 1, 3, 'p4-testdata-v1', '/srv/goj-data/testData/p4', NULL, 'READY', 1, 'system', 'seed test data directory'),
    (5, 5, 1, 3, 'p5-testdata-v1', '/srv/goj-data/testData/p5', NULL, 'READY', 1, 'system', 'seed test data directory'),
    (6, 6, 1, 3, 'p6-testdata-v1', '/srv/goj-data/testData/p6', NULL, 'READY', 1, 'system', 'seed test data directory');

INSERT IGNORE INTO `problem_tag` (`id`, `problem_id`, `tag_id`, `create_by`, `remark`) VALUES
    (1, 1, 8, 'system', 'A+B Problem -> 数学'),
    (2, 1, 7, 'system', 'A+B Problem -> 入门'),
    (3, 2, 1, 'system', '数组求和 -> 数据结构'),
    (4, 2, 5, 'system', '数组求和 -> 暴力'),
    (5, 2, 7, 'system', '数组求和 -> 入门'),
    (6, 3, 4, 'system', '反转字符串 -> 字符串'),
    (7, 3, 3, 'system', '反转字符串 -> 模拟'),
    (8, 3, 7, 'system', '反转字符串 -> 入门'),
    (9, 4, 2, 'system', '斐波那契数 -> 动态规划'),
    (10, 4, 7, 'system', '斐波那契数 -> 入门'),
    (11, 5, 3, 'system', '机器人移动 -> 模拟'),
    (12, 5, 4, 'system', '机器人移动 -> 字符串'),
    (13, 5, 7, 'system', '机器人移动 -> 入门'),
    (14, 6, 6, 'system', '奇数在前偶数在后 -> 构造'),
    (15, 6, 7, 'system', '奇数在前偶数在后 -> 入门');
