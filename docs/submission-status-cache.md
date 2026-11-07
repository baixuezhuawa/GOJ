# 判题状态 CDC 缓存

## 范围与调用链

普通及比赛提交支持状态缓存；管理员验题、提交列表缓存、排行榜不在本功能范围。

Judge 提交 MySQL 事务 → binlog → Canal TCP 实例 → `SubmissionCanalConsumer` → `SubmissionStatusService` 按 ID 查询主库结果摘要 → Redis。

消费者与查询回填共用 256 个固定分段锁，保证同一 ID 在单实例内串行读取并写入。事件用于通知变化，不把历史行镜像直接覆盖缓存；一批中相同 ID 去重。重放读取主库当前结果，可能跳过短暂中间状态。

**只支持一个启用缓存的 goj-server 实例写入同一组缓存。** 扩容前必须增加跨实例协调或可比较的数据库变更版本。MySQL 数据源必须指向主库。当前 `version` 是重判轮次，不是每次更新的序号。

## 接口与页面

- 完整详情：`GET /submission/{id}`、`GET /contest/{contestId}/submission/{id}`。
- 状态摘要：`GET /submission/{id}/status`、`GET /contest/{contestId}/submission/{id}/status`。
- 比赛页面：`/contests/{contestId}/submissions/{id}`，要求登录；普通提交接口不能读取比赛及验题记录。
- 比赛状态接口核对比赛阶段及提交归属，仍可能读取比赛表。比赛题目页面尚未接入，所以本页面不提供跳往普通题目的链接。
- 首次完整详情同时刷新状态摘要，之后每 2.5 秒轮询摘要；终态后停止。终态后另行重判需要手动刷新或重新打开页面。
- 轮询出错保留当前展示并提供重试；离开页面取消请求和定时器。

## 缓存语义

键为 `goj:submission:result:{id}`，保存归属、版本、状态、得分、耗时、内存、编译信息、判题信息及判题起止时间，不保存源码和题目名称。

- 非终态有效期 30 秒。
- 终态有效期 1200 秒加 0～120 秒随机时间。
- 命中不续期；相同结果再次核验或事件重放通过 Redis `SET ... KEEPTTL` 保留原期限（需要 Redis 6.0 及以上）。
- 有访问时，超过 30 秒未核验的快照会重新查询主库，仍不续期。这用于兜底 Canal Server 到 MySQL 静默断线：客户端连接正常或拉到空批次不能证明上游健康。
- 连接故障、消费失败或已观察到的积压超过 30 秒时绕过缓存。允许正常 CDC 延迟，核验周期不是故障检测的严格实时保证。
- Redis 不可用时查询返回主库结果；消费者写入失败不确认批次。每次写完整结果，重判后的 NULL 会清除旧字段。
- 历史记录按需回填，不进行全库预热。删除事件触发主库检查后删除缓存。
- 除 CREATE 外的提交表 DDL 会阻止批次确认，避免静默遗漏 TRUNCATE 等变化。变更表结构时停消费、完成兼容检查、清理结果缓存并规划新的消费起点后恢复；不能只重启来跳过错误批次。

## 本机启动

安装目录由 `GOJ_CANAL_HOME` 环境变量指定，版本 1.1.7。客户端使用同版本的 `canal.client` 与 `canal.protocol`。

首次配置时，将实际安装目录保存为当前 Windows 用户的环境变量，并设置当前终端的值：

```powershell
[Environment]::SetEnvironmentVariable('GOJ_CANAL_HOME', 'D:\Work\Infrastructure\Canal', 'User')
$env:GOJ_CANAL_HOME = 'D:\Work\Infrastructure\Canal'
```

用户环境变量会持久保存，无需配置系统级环境变量。已打开的终端和 IDE 需要重启，或在其进程环境中显式设置该变量。启动脚本和真实 CDC 测试共用此变量，不再使用固定安装目录。

本机运行值已核验为 `log_bin=ON`、`binlog_format=ROW`、`binlog_row_image=FULL`、`server_id=1`；复制账号为 `canal@127.0.0.1`。无需为了本功能重启 MySQL。

Canal 1.1.7 的 TSDB 初始化会先枚举可见数据库的表，再按订阅规则过滤。本机已补充以下单表权限，解决 `show full tables from performance_schema` 被拒绝的问题；没有授予全库 SELECT：

```sql
GRANT SELECT ON performance_schema.global_variables TO 'canal'@'127.0.0.1';
```

保留安装目录现有密码，不提交到仓库。`conf/canal.properties` 需使用：

```properties
canal.serverMode=tcp
canal.port=11111
canal.destinations=goj
canal.instance.global.spring.xml=classpath:spring/file-instance.xml
```

`conf/goj/instance.properties` 需使用（密码沿用本机文件）：

```properties
canal.instance.mysql.slaveId=1001
canal.instance.master.address=127.0.0.1:3306
canal.instance.dbUsername=canal
canal.instance.filter.regex=goj\\.submission
```

项目根目录执行：

```powershell
.\scripts\start-canal.ps1
```

重启使用 `-Restart`；临时指定目录可使用 `-CanalHome '实际安装目录'`，优先于环境变量。脚本隐藏启动 Java，不使用安装包旧的调试参数；只管理自身 PID 文件记录的进程。查看安装目录下的 `logs\goj\goj.log` 和 `logs\canal\canal.log`。启动脚本不会修改实例配置或重置位点。

启动 goj-server 的进程环境增加 `GOJ_SUBMISSION_CACHE_ENABLED=true`；默认关闭时状态接口仍可用，但直接查数据库，不消费 Canal、不写缓存。IDE 启动需要在运行配置设置该变量。前端仍按原方式启动，Java 和 Canal 配置变更均需重启相应进程，前端开发服务器支持热更新。

保留 Canal `conf/goj` 中的位点及 TSDB 文件。MySQL binlog 保留时间必须覆盖预期停机时长；位点指向已清理的日志时不能静默跳到最新，应安排恢复及缓存重建。

## 验证

服务端普通测试：

```powershell
mvn -pl goj-server -am test
```

真实 CDC 测试使用随机独立数据库、负数测试提交 ID 和独立 Canal 端口 11120～11122；不修改业务库或安装目录配置。使用已有 `GOJ_MYSQL_PASSWORD` 和本机 Canal 配置中的专用账号，在 finally 中清理测试库、临时授权、测试 Redis 键及临时密码文件。

```powershell
$env:GOJ_TEST_CDC='true'
mvn -pl goj-server -am test '-Dtest=SubmissionCdcIntegrationTests' '-Dsurefire.failIfNoSpecifiedTests=false'
```

前端浏览器验证使用 Python Playwright 和本机 Edge；接口由脚本模拟，不能等同真实后端 E2E：

```powershell
# 先在 goj-web 执行 npm run build，再执行 npm run preview -- --host 127.0.0.1 --port 5179
# 使用生产构建，避免开发模式 StrictMode 的额外初始化影响请求次数断言。
python -X utf8 scripts/test-submission-status-ui.py
```

截图位于 `goj-web/test-results/submission-status`。真实判题、MQ、浏览器与 CDC 全链路验收需另行区分记录。

2026-10-26 验证结果：服务端 69 项测试全部通过，包含真实独立 MySQL/Canal/Redis 测试及 4 项 MySQL 幂等回归。前端生产构建通过；模拟 API 浏览器验证通过，已检查普通桌面、比赛桌面与比赛手机页面。没有执行真实沙箱到浏览器的完整端到端判题。

## 轻量真实判题联调（2026-10-26）

复用本机原有 Canal、MySQL、Redis 和已运行的 MQ、沙箱；临时启动 Server（最大堆 256 MB）和 Judge（最大堆 192 MB），串行执行一条普通 A+B Python 提交，不启动浏览器或压测。启动前可用内存约 1.6 GB，两个临时 Java 进程启动后的工作集各约 255 MB，这只是采样值，不是峰值。

- 测试用户 `cdc_light_f9b576c6`（ID 6），提交 ID 9，任务 ID 7；账号密码随机生成且未保存，测试结束已退出登录。
- 在调用任何详情或状态查询接口之前，仅读取 Redis，约 1.06 秒观察到 `in queue`，约 4.09 秒观察到 `Accepted`，排除查询回填造成的假阳性。
- 主库结果 `Accepted`，任务 `SUCCEEDED`、`attempt_count=1`、无错误；状态接口与数据库及 Redis 一致。
- 终态剩余 TTL 1212922 ms，查询后 1212897 ms，符合 20 分钟加抖动且命中不续期。
- 联调后关闭本轮临时 Server、Judge，保留原有组件；保留这条真实提交和相关统计作为验证记录，没有删除业务关联数据。
- 证据：`goj-server/target/cdc-light-report.json`、`cdc-light-server.log` 和 `goj-judge/target/cdc-light-judge.log`。本轮验证普通提交 API → MQ → Judge/沙箱 → MySQL → Canal → Redis → 状态 API；未覆盖比赛提交流程或真实浏览器 E2E。
