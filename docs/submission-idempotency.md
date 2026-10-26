# 提交幂等防护

普通 `POST /submission/submit` 和比赛 `POST /contest/{contestId}/problem/{problemCode}` 均要求请求体携带完整 UUID `requestId`。重试保留原 UUID 和请求内容；成功返回原 `submissionType`、`submissionId`，不等待评测完成。

## 后端边界

- `SubmissionRequestIdentity` 将 UUID 转为小写，并对固定顺序 JSON 数组计算 SHA-256；代码不裁剪，普通请求包含现有 `contestId` 参数，比赛请求包含比赛 ID 和题号。
- `SubmissionIdempotencyService.execute` 使用 `NOT_SUPPORTED` 在创建事务外协调，先按登录用户与 UUID 查询。已有记录指纹一致则回放，不执行新提交的阶段或频率校验。
- 原普通、比赛 Service 使用 `@Transactional` 原子创建 Submission 和初始 JudgeTask；数据库唯一键为 `(user_id, request_id)`。比赛转普通提交保留原请求身份，加入比赛事务。
- 业务或数据库异常离开事务后，协调层再次查询获胜记录，覆盖唯一键竞争与频率检查竞争。当前项目为单一 MySQL 数据源；未来引入读写分离时这些查询必须走主库。
- `NOT_SUPPORTED` 仍可能通过 Spring 事务同步绑定 MyBatis 会话。`SubmissionMapper.findAcceptedRequest` 每次刷新一级缓存并禁用二级缓存，避免异常后的重新查询复用第一次的空结果。集成测试同时代理协调层和业务层，覆盖此运行时边界。
- 相同 UUID 内容不同返回 HTTP 409；明确业务拒绝且未找到受理记录返回 HTTP 422。未确认的数据库、网络错误不视为未受理；前端保留快照。
- 不缓存失败响应。失败后相同 UUID、相同内容可以再次尝试。不同 UUID 仍执行原相同代码频率限制；本次不改变该频率限制的并发严格性。

## 前端边界

`pendingSubmission.ts` 按用户在 `sessionStorage` 保存一个待确认快照，先保存成功再发送。普通页面刷新后展示“确认原提交”，允许使用原快照恢复普通或比赛请求，即使当前题目或语言列表加载失败也可以确认。

成功或明确 422 业务拒绝仅清除对应 UUID；其他错误保留记录。迟到响应不能清除下一次请求。编辑器草稿不覆盖待确认快照；等待期间修改代码不会被成功后的跳转丢弃。存储损坏或不可用时阻止创建新请求，不静默丢弃旧记录。

范围是同一标签页刷新恢复；不保证关闭标签页后恢复。比赛 API 与共用快照机制已经提供，本次不新建完整比赛提交页面。

## 部署

先在目标数据库执行一次 `sql/migrations/20261026_submission_idempotency.sql`，再部署后端及前端。新库使用更新后的 `sql/schema.sql`。新增字段允许为空以兼容历史和内部验题记录；HTTP 普通与比赛入口不允许缺少 UUID。

迁移脚本已在随机独立测试库验证；2026-10-26 用户执行业务库迁移后，已现场确认两个字段及唯一索引生效。公共 Submission 实体新增字段后，使用该实体的服务也要求数据库先完成迁移。

## 验证

服务端测试在项目根目录运行；MySQL 测试会创建并清理随机 `goj_idempotency_test_` 数据库，不读取业务数据。URL 必须是以 `/` 结尾、不含数据库名或查询参数的测试服务器 URL；账号须有建库、删库权限。

```powershell
$env:GOJ_TEST_MYSQL_SERVER_URL = 'jdbc:mysql://localhost:3306/'
# 密码读取现有 GOJ_MYSQL_PASSWORD；可用 GOJ_TEST_MYSQL_USER 指定用户名，默认 root。
mvn -pl goj-server -am test
```

未设置测试 URL 时 MySQL 集成测试跳过，其他测试照常运行。集成测试使用真实 Mapper、业务 Service 和 Spring 事务代理，题目与报名校验依赖使用 Mock；覆盖并发同键插入、任务失败回滚与重试、比赛及赛后普通提交回放、用户隔离与频率限制。

前端构建使用 `npm.cmd --prefix goj-web run build`。浏览器回归脚本为 `goj-web/tests/submission_idempotency.py`，要求 Python Playwright 和 Chromium；先启动 Vite 到 5179 端口，再运行脚本。可用 `GOJ_TEST_WEB_URL` 指定地址、`GOJ_TEST_BROWSER` 指定本机 Chrome/Edge 可执行文件。

浏览器测试模拟 API 响应，覆盖响应丢失后刷新和原内容重试、成功与 422 清理、比赛快照恢复、用户隔离、迟到清理和损坏记录保护，不代表实际 MQ 与评测机的端到端验证。

## 真实环境验证（2026-10-26）

使用 `goj-web/tests/submission_idempotency_live.py` 运行真实 HTTP → MySQL → RocketMQ → Judge → go-judge 链路。脚本要求 Python requests、mysql 客户端及现有数据库环境变量；会创建并保留带 `idem_e2e_` 标识的测试账号、比赛和提交，密码随机生成、令牌不落盘，结束后退出登录。测试比赛结束为 FINISH。

首次真实并发发现协调层查询复用了 MyBatis 的空结果缓存；将集成测试改为真实协调层代理后稳定复现，增加 Mapper 查询缓存刷新后修复。

修复后的运行标识为 `c0bc3ef397`，测试用户 ID 为 4、5，测试比赛 ID 为 1：

- 普通和比赛入口各 8 路相同请求均返回同一提交 ID；普通提交 ID 为 3，比赛提交 ID 为 6。
- 缺失或非法 UUID 返回 400，内容冲突返回 409，不同 UUID 的相同代码频率限制返回 422。
- 同 UUID 跨用户隔离、大小写 UUID 回放、比赛阶段变化回放、业务失败后同 UUID 重试、赛后转普通提交及回放均通过。
- 提交 3、5、6、7、8 全部为 `Accepted`，对应任务均为 `SUCCEEDED`、`attempt_count=1`，每条提交仅有一条初始任务。
- 明确限制：Judge 日志显示 `GOJ_CONTEST_RESULT` 无主题路由，比赛实时榜通知发送失败。本次确认受理幂等、任务投递、沙箱评测和结果落库通过，不宣称比赛实时榜通知链路通过。

机器可读结果位于 `goj-server/target/idempotency-live-report.json`；Server、Judge 日志分别为 `goj-server/target/idempotency-e2e-server-fixed.log` 和 `goj-judge/target/idempotency-e2e-judge.log`。首次失败运行留下用户 2、3 和已 Accepted 的提交 2，供排查对照。
