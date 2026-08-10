CREATE DATABASE IF NOT EXISTS `goj`
    DEFAULT CHARACTER SET utf8mb4
    COLLATE utf8mb4_unicode_ci;

USE `goj`;

-- =========================
-- 系统与 RBAC 表
-- =========================

DROP TABLE IF EXISTS `sys_role`;
CREATE TABLE `sys_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色 id',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `role_name` VARCHAR(64) NOT NULL COMMENT '角色名称',
    `role_code` VARCHAR(64) NOT NULL COMMENT '角色编码',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_role_code` (`role_code`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统角色表';

DROP TABLE IF EXISTS `sys_permission`;
CREATE TABLE `sys_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '权限 id',
    `permission_name` VARCHAR(128) NOT NULL COMMENT '权限名称',
    `permission_code` VARCHAR(128) NOT NULL COMMENT '权限编码',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_permission_permission_code` (`permission_code`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统权限表';

DROP TABLE IF EXISTS `sys_user`;
CREATE TABLE `sys_user` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户 id',
    `username` VARCHAR(64) NOT NULL COMMENT '账号',
    `password` VARCHAR(255) NOT NULL COMMENT '加密后的密码',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 1 COMMENT '状态：1 启用，0 禁用',
    `email` VARCHAR(255) DEFAULT NULL COMMENT '邮箱地址',
    `gender` TINYINT UNSIGNED DEFAULT NULL COMMENT '性别编码',
    `birthdate` DATETIME DEFAULT NULL COMMENT '出生日期',
    `phone_number` VARCHAR(32) DEFAULT NULL COMMENT '手机号',
    `avatar` VARCHAR(512) DEFAULT NULL COMMENT '头像地址',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_username` (`username`),
    KEY `idx_sys_user_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '系统用户表';

DROP TABLE IF EXISTS `sys_user_role`;
CREATE TABLE `sys_user_role` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '用户角色关联 id',
    `user_id` BIGINT NOT NULL COMMENT '用户 id',
    `role_id` BIGINT NOT NULL COMMENT '角色 id',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_user_role_user_role` (`user_id`, `role_id`),
    KEY `idx_sys_user_role_role_id` (`role_id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '用户角色关联表';

DROP TABLE IF EXISTS `sys_role_permission`;
CREATE TABLE `sys_role_permission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '角色权限关联 id',
    `role_id` BIGINT NOT NULL COMMENT '角色 id',
    `permission_id` BIGINT NOT NULL COMMENT '权限 id',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_sys_role_permission_role_permission` (`role_id`, `permission_id`),
    KEY `idx_sys_role_permission_permission_id` (`permission_id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '角色权限关联表';


-- =========================
-- 题目与提交表
-- =========================

DROP TABLE IF EXISTS `problem`;
CREATE TABLE `problem` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目 id',
    `problem_name` VARCHAR(128) NOT NULL COMMENT '题目名称',
    `time_limit` INT UNSIGNED NOT NULL DEFAULT 1000 COMMENT '时间限制，单位毫秒',
    `memory_limit` INT UNSIGNED NOT NULL DEFAULT 262144 COMMENT '空间限制，单位 KB',
    `description` TEXT NOT NULL COMMENT '题面描述',
    `input_description` TEXT NOT NULL COMMENT '输入描述',
    `output_description` TEXT NOT NULL COMMENT '输出描述',
    `input_example` TEXT DEFAULT NULL COMMENT '公开输入样例',
    `output_example` TEXT DEFAULT NULL COMMENT '公开输出样例',
    `example_note` TEXT DEFAULT NULL COMMENT '样例说明',
    `difficulty` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '题目难度分',
    `author_id` BIGINT NOT NULL COMMENT '出题人用户 id',
    `status` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '题目状态：0 草稿，1 已发布，2 已停用',

    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_problem_status` (`status`),
    KEY `idx_problem_author_id` (`author_id`),
    KEY `idx_problem_difficulty` (`difficulty`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '题目表';

DROP TABLE IF EXISTS `problem_case`;
CREATE TABLE `problem_case` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试用例 id',
    `problem_id` BIGINT NOT NULL COMMENT '题目 id',
    `input_data` LONGTEXT NOT NULL COMMENT '测试输入',
    `expected_output` LONGTEXT NOT NULL COMMENT '预期输出',
    `is_sample` TINYINT UNSIGNED NOT NULL DEFAULT 0 COMMENT '是否公开样例：1 是，0 否',
    `sort_order` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '展示和执行顺序',
    `score` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '该测试点分值',
    `explanation` TEXT DEFAULT NULL COMMENT '样例说明，可为空',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_problem_case_problem_id` (`problem_id`),
    KEY `idx_problem_case_sample_order` (`problem_id`, `is_sample`, `sort_order`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '题目样例和隐藏测试用例表';


DROP TABLE IF EXISTS `problem_test_data`;
CREATE TABLE `problem_test_data` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '测试数据集 id',
    `problem_id` BIGINT NOT NULL COMMENT '题目 id',
    `version` INT UNSIGNED NOT NULL COMMENT '测试数据版本',
    `test_node_count` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '测试点数量',
    `archive_name` VARCHAR(255) NOT NULL COMMENT '原始压缩包名称',
    `storage_path` VARCHAR(512) NOT NULL COMMENT '解压后的存储路径',
    `archive_sha256` CHAR(64) DEFAULT NULL COMMENT '压缩包 SHA-256',
    `status` VARCHAR(16) NOT NULL DEFAULT 'UPLOADING'
        COMMENT '状态：UPLOADING、READY、INVALID、RETIRED',
    `active` TINYINT UNSIGNED NOT NULL DEFAULT 0
        COMMENT '是否为当前启用版本：1 是，0 否',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_test_data_version` (`problem_id`, `version`),
    KEY `idx_problem_test_data_problem_id` (`problem_id`),
    KEY `idx_problem_test_data_active` (`problem_id`, `active`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
    COMMENT = '题目测试数据集表';


DROP TABLE IF EXISTS `tag`;
CREATE TABLE `tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '标签 id',
    `tag_name` VARCHAR(64) NOT NULL COMMENT '标签名称',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_tag_tag_name` (`tag_name`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '题目标签表';

DROP TABLE IF EXISTS `problem_tag`;
CREATE TABLE `problem_tag` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '题目标签关联 id',
    `problem_id` BIGINT NOT NULL COMMENT '题目 id',
    `tag_id` BIGINT NOT NULL COMMENT '标签 id',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    UNIQUE KEY `uk_problem_tag_problem_tag` (`problem_id`, `tag_id`),
    KEY `idx_problem_tag_tag_id` (`tag_id`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '题目标签关联表';

DROP TABLE IF EXISTS `submission`;
CREATE TABLE `submission` (
    `id` BIGINT NOT NULL AUTO_INCREMENT COMMENT '提交 id',
    `user_id` BIGINT NOT NULL COMMENT '提交用户 id',
    `problem_id` BIGINT NOT NULL COMMENT '题目 id',
    `language` VARCHAR(32) NOT NULL COMMENT '编程语言，例如 JAVA',
    `source_code` LONGTEXT NOT NULL COMMENT '提交的源代码',
    `status` VARCHAR(32) NOT NULL DEFAULT 'QUEUED' COMMENT '评测状态',
    `score` INT UNSIGNED NOT NULL DEFAULT 0 COMMENT '评测得分',
    `time_ms` INT UNSIGNED DEFAULT NULL COMMENT '运行耗时，单位毫秒',
    `memory_kb` INT UNSIGNED DEFAULT NULL COMMENT '运行内存，单位 KB',
    `compiler_msg` TEXT DEFAULT NULL COMMENT '编译器输出信息',
    `judge_msg` TEXT DEFAULT NULL COMMENT '评测或运行信息',
    `submission_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '提交时间',
    `judge_start_time` DATETIME DEFAULT NULL COMMENT '评测开始时间',
    `judge_end_time` DATETIME DEFAULT NULL COMMENT '评测结束时间',
    `create_by` VARCHAR(64) DEFAULT NULL COMMENT '创建人',
    `create_time` DATETIME NOT NULL DEFAULT CURRENT_TIMESTAMP COMMENT '创建时间',
    `update_by` VARCHAR(64) DEFAULT NULL COMMENT '更新人',
    `update_time` DATETIME DEFAULT NULL ON UPDATE CURRENT_TIMESTAMP COMMENT '更新时间',
    `remark` VARCHAR(500) DEFAULT NULL COMMENT '备注',
    PRIMARY KEY (`id`),
    KEY `idx_submission_user_time` (`user_id`, `submission_time`),
    KEY `idx_submission_problem_time` (`problem_id`, `submission_time`),
    KEY `idx_submission_status` (`status`)
) ENGINE = InnoDB
  DEFAULT CHARACTER SET = utf8mb4
  COLLATE = utf8mb4_unicode_ci
  COMMENT = '代码提交和评测结果表';
