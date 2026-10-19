# 管理员题目与测试数据管理

题目创建、编辑、状态设置、测试数据管理和验题统一要求 `SUPER_ADMIN`。普通用户只保留公开题目查询和正常代码提交。用户投稿、我的题目、审批、驳回、重新提交接口已删除。

## 题目

创建只提交 JSON 题目信息，不要求测试数据。创建、编辑共用 `ProblemSaveDto`，测试数据通过独立接口维护。

题目状态只有 `DRAFT=0`、`PUBLISH=1`、`DISABLE=2`。管理员可以直接设置目标状态，不限制原状态，不检查作者身份。发布时必须存在一个已启用且文件可读的数据版本。后续停用或删除数据不会自动修改题目状态。

## 接口

以下路径统一以 `/admin/problem-manager` 开头。

| 操作 | 方法和路径 | 参数 |
| --- | --- | --- |
| 创建题目 | `POST /problems` | JSON：`ProblemSaveDto` |
| 编辑题目 | `PUT /problems/{problemId}` | JSON：`ProblemSaveDto` |
| 设置题目状态 | `PUT /{problemId}/set-status/{status}` | 状态为 0、1、2 |
| 全部题目列表 | `GET /problems` | 原分页筛选参数 |
| 完整题目与数据版本 | `GET /problems/{problemId}` | 无 |
| 新增或覆盖数据版本 | `PUT /problems/{problemId}/test-data/{version}` | multipart：`file`；可选 `remark` |
| 启用或停用数据 | `PUT /problems/{problemId}/test-data/{version}/active` | `active=true/false` |
| 删除数据版本 | `DELETE /problems/{problemId}/test-data/{version}` | 无 |
| 下载原始 ZIP | `GET /problems/{problemId}/test-data/{version}/archive` | 无 |
| 提交验题代码 | `POST /problems/{problemId}/judge-submissions` | JSON：`language`、`sourceCode` |
| 查询验题结果 | `GET /judge-submissions/{reviewSubmissionId}` | 无 |

原管理员难度、标签接口保留。创建接口已从 multipart 改为 JSON。题目创建和上传数据是两个独立操作，不提供重复的组合接口。

## 测试数据

版本号由管理员指定，为同一道题下唯一的正整数。首次保存默认不启用；保存已有版本直接替换内容，保留数据记录 ID 和启用标记。启用一个版本会关闭同题其他版本。删除不检查启用状态、比赛引用或验题引用。

数据库不再保存 `UPLOADING/UPLOADED/EXTRACTED/READY/INVALID/RETIRED`，只有 `active` 启用标记。上传失败不产生新数据记录，也不破坏覆盖前的有效内容。

正式目录示例：

```text
goj.data-root/
  testData/p123/v7-<本次上传标识>/
    data.zip
    cases/
      test1/input.txt
      test1/output.txt
      test2/input.txt
      test2/output.txt
```

原始 ZIP 保存在 `archive_path`，解压目录保存在 `storage_path`；新记录都使用相对路径。独立的本次上传目录直接位于正式目录，成功后不再搬动，没有暂存区或审核区。目录标识用于让失败覆盖保留原内容，与管理员版本号不是同一个概念。

`TestDataStorage` 集中负责 ZIP 保存、解压、结构校验、完整原始文件的 SHA-256、下载与清理。`StoredTestData` 只承载处理结果。业务服务不拼接文件目录；Judge 继续通过 `TestDataRef` 和 `LocalFileTestCaseProvider` 读取解压目录。

当前结构仍要求从 `test1` 连续编号，每个目录只有 `input.txt`、`output.txt`，内容可按 UTF-8 文本读取。保留目录越界、解压大小、文件数量检查，避免损坏文件影响服务器。

## 覆盖、删除与正在运行的判题

验题不再指定数据版本；Worker 执行时按版本号降序读取最新上传版本，允许尚未启用，不依赖题目发布状态。普通、比赛和验题均保存到 `submission`，通过 `submission_type` 区分；任务类型和调度优先级保持不变。

覆盖同版本保留数据 ID，因此比赛再次加载绑定 ID 时读取新内容；验题执行时重新选择最新版本。历史已保存的判题结果不会自动改写，也不自动触发重测。普通提交仍在执行时选择当前启用版本。

管理员允许覆盖或删除任何版本。已打开旧目录的运行中任务，可能因旧文件被删除而失败；删除后保留的历史引用也可能失效。系统不阻止操作、不自动修复，由管理员安排操作和重测。删除后重新创建同版本会得到新 ID，不会自动修复旧引用。

数据库写入以题目行锁串行处理版本保存、启用、删除和发布检查，防止正常并发请求产生多个启用版本。文件操作不属于数据库事务：文件先校验，数据库成功后清理旧文件；数据库失败清理新文件。进程中断或清理失败可能留下未被引用的文件，当前不增加后台清理任务。

## 代码变化入口

- `ProblemServiceImpl`：创建、编辑和设置题目状态。
- `ProblemTestDataServiceImpl`：版本元信息与启用标记。
- `TestDataStorage`：文件格式、布局和校验的唯一入口。
- `ProblemReviewJudgeServiceImpl` / `JudgeServiceImpl`：创建验题提交并使用最新上传的数据版本。
- `ProblemInfoVo`：显式声明公开字段，避免未来给 `Problem` 增加标准程序等内部字段时自动公开。

以后增加标准检测程序字段，按实际需要修改实体、SQL、管理员保存 DTO 和 Judge 请求组装即可，不再贯穿用户投稿和审批链路。上传方式变化主要修改存储类；只有变化影响 Judge 实际读取格式时才需要调整读取器。

## 已有数据迁移

SQL 目录维护完整表结构和开发初始化数据。本次提交合表另提供 `sql/migrations/20261019_unify_submissions.sql`，用法见 [统一提交记录](submission-unification.md)；更早的题目管理结构升级仍需依据实际结构处理，Server 和 Judge 应一起更新。

- 全新环境使用更新后的 `sql/schema.sql`、`sql/data.sql`。`schema.sql` 包含删表语句，不能用于保留数据的升级。
- 已有环境先停止 Server 和 Judge、备份，再对照当前 `schema.sql` 检查需要调整的结构，不能直接重跑初始化脚本。
- 旧暂存目录、无版本号、未完成上传记录需要先由管理员迁移到正式目录并补齐正式版本，或在备份后移除，不把失败上传当成有效数据。
- 若仍使用旧审核结构，需要将题目状态 3、4、5 映射为不公开的草稿 0，保留作者信息；测试数据移除 `status`，增加可空的 `archive_path`。当前 `schema.sql` 已反映最终结构。
- 旧实现没有保存原始 ZIP，无法恢复原始字节。旧正式数据可继续判题；要下载原包，需要管理员重新上传同版本。
- 旧种子目录仍可使用，重新上传后转为新布局。迁移不能靠 SQL 验证磁盘文件是否完整，需要管理员实际检查。

## 验证

无需数据库的定向测试覆盖原包下载、完整摘要、ZIP 和结构错误、覆盖失败保留旧数据、同版本覆盖保留 ID、管理员删除已启用版本、启用切换、无数据创建与发布校验，以及 Spring 方法权限和 Judge 验题加载。

```powershell
mvn -B '-Dtest=TestDataStorageTests,ProblemTestDataServiceTests,ProblemManagementTests,ProblemManagementSecurityTests,JudgeServiceTests,UnifiedSubmissionTests' '-Dsurefire.failIfNoSpecifiedTests=false' test
```

这些测试不等同于真实 MySQL、Redis、沙箱和 HTTP 联调；迁移脚本也需要在实际数据库升级时验证。
