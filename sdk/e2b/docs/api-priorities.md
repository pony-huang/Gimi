# E2B 接口功能与实现优先级

## 核对范围

盘点基于 [e2b-dev/e2b](https://github.com/e2b-dev/e2b) 提交 [`df6c368152b067512702fc677952c0fc4e13c2f0`](https://github.com/e2b-dev/e2b/tree/df6c368152b067512702fc677952c0fc4e13c2f0)，核对日期 2026-10-11。依据 `spec/openapi.yml`、`spec/openapi-volumecontent.yml`、`spec/envd/{process,filesystem}/*.proto` 和 `packages/python-sdk/e2b` 的同步 SDK 实现。

用户给出的 [API reference](https://docs.e2b.dev/api-reference) 在当前执行环境被代理以 403 拒绝，未读取该页面。这里记录的是该提交的官方仓库契约，不声称覆盖之后的新接口或网页独有扩展。同步/异步 SDK 功能相同，不重复计算；operation 标记 `deprecated: true` 的 REST 操作及 SDK 废弃别名均排除，响应模型的废弃字段（如 clientID）不暴露。

[完整非废弃 REST 清单](api-inventory.md) 覆盖 control plane 和 volume content；下面补齐并不在 control-plane OpenAPI 中的 envd 操作与 SDK 高层功能。官方仓库还包含平台管理接口，它们不是普通应用必须实现的 SDK 功能。

## 推荐顺序

| 优先级 | 功能 | 原因与首版状态 |
| --- | --- | --- |
| P0 | 沙箱创建、连接/恢复、详情/状态、分页列表、续期、销毁 | 构成创建→使用→复用→清理闭环；已实现 |
| P0 | 命令前台执行、stdout/stderr、exit code、超时、后台句柄、进程连接/列表/终止、stdin/EOF | Agent 执行工具和安装依赖的基础；已实现，首版 await 汇总输出 |
| P0 | 文件文本/二进制上传下载、列表/stat/exists、mkdir/remove/move | Agent 输入输出和工作文件交互；已实现，首版内存缓冲 |
| P0 | 认证、不同超时语义、错误/限流、host 获取、资源关闭 | 每个功能的必要契约；已实现，不自动重试 |
| P1 | 沙箱暂停，恢复使用 connect | 节省成本、保存会话；暂停已实现并提前纳入首版 |
| P1 | 命令输出 callback、真正可取消的 Kotlin suspend/Flow 扩展、流式大文件/批量文件上传 | 交互式 Agent 和大文件的下一步，尚未实现；Java 同步核心保留独立 |
| P1 | 网络规则、auto-resume/lifecycle、IAM/MCP/volume 挂载创建参数 | 网络控制和高级运行环境；首版仅支持 allowInternetAccess 和 autoPause；完整配置尚未实现 |
| P1 | 沙箱/团队 metrics、沙箱日志、refresh | 长会话管理、容量观测和排错，尚未实现；已有 setTimeout 可满足基础续期 |
| P1 | 模板列举/构建列表、别名/标签查询、标签分配/删除 | 应用选取和管理预构建环境；首版可直接使用 template ID/alias 创建沙箱，管理 API 尚未实现 |
| P2 | 模板创建、构建启动/状态/日志、文件上传 URL、更新/删除、Template 构建 DSL | 构建链较复杂，前期可用官方 CLI 预构建；尚未实现 |
| P2 | PTY 创建/重连/resize/输入/终止 | 交互终端体验才需要；普通 commands 不等价于 PTY，尚未实现 |
| P2 | 文件 watcher、文件扩展属性 metadata、压缩传输、签名上传/下载 URL | 文件同步和优化；尚未实现 |
| P2 | snapshot 创建/列表/删除、fork、filesystem-only pause 与自动恢复高级组合 | 分支沙箱和持久会话；首版仅支持 pause(memory)，其余尚未实现 |
| P2 | Volume 创建/查询/列举/删除、卷正文读写/stat/move/mkdir/remove | 跨沙箱持久数据；管理和独立 volume-content API 均尚未实现 |
| P2 | Secret CRUD、sandbox workload IAM、MCP token/服务 URL | 专项安全与工具集成；尚未实现 |
| P2 | Git clone/init/status/branch/add/commit/reset/restore/pull/push/config/auth 等高层工具 | 官方本身是 commands 的便捷封装，初期可通过 commands 执行；尚未增加类型化包装 |
| P3 | 沙箱事件、Webhook CRUD/投递记录/统计、API key CRUD、团队列举/容量统计 | 平台运维/事件驱动需求出现时实现；尚未实现 |
| 不进入通用 SDK | nodes、admin、cluster rigs/capacity/instances/errors | 自建 E2B 平台管理员接口，部署/权限相关；只盘点，不作为应用 SDK 路线图 |

P0 优先面向当前项目最小执行闭环，P1 为成本、体验和可观测性，P2 为专用高级能力，P3 为平台工作流。优先级是本项目建议，不是官方对 API 稳定性或产品套餐的承诺。

## envd 与 SDK 功能完整分类

| 范围 | 具体操作 | 取舍 |
| --- | --- | --- |
| `process.Process` | List、Start、Connect、SendInput、CloseStdin、SendSignal | 首版 commands 使用这些操作；Start/Connect 是服务器流 |
| `process.Process` | Update、StreamInput | Update 用于 PTY resize；StreamInput 是有序客户端输入流。推迟，不把普通 POST JSON 当作它们的协议实现 |
| `filesystem.Filesystem` | Stat、MakeDir、Move、ListDir、Remove | 首版已实现 |
| `filesystem.Filesystem` | WatchDir、CreateWatcher、GetWatcherEvents、RemoveWatcher | 流式 watcher 与轮询 watcher；推迟 |
| envd HTTP `/files` | GET 下载、POST 上传（multipart/octet-stream），压缩、metadata | 首版 GET + multipart POST；推迟优化、流式文件和 metadata 参数 |
| `Sandbox` | getHost、isRunning、files、commands、PTY、Git、MCP token、snapshot/fork/network/lifecycle | 前四项已实现；其余按上表分阶段 |
| `Template` | 镜像/Dockerfile、用户/目录/环境变量、文件复制、命令、依赖安装、启动/ready 命令等构建 DSL；同步/后台 build 与状态、exists/alias、标签管理 | 是 SDK 高层组合能力，需要文件打包、上传及构建轮询，不只包一层 REST |
| `Volume` | create/get/list/delete、mount、files 正文与元数据 | 独立资源类和传输，推迟 |
| `Secret` | create/get/list/update/delete | 独立资源类，推迟 |
| `Git` | clone/attemptClone、init、remoteAdd/Get、status、branches、create/checkout/deleteBranch、add/commit/reset/restore、push/pull、set/getConfig、configureUser、认证 | 基于 commands 的类型化工具层，推迟 |
| SDK 配置 | API key/domain、API/envd URL、超时、用户、调试/自建配置、日志、重试策略 | 首版实现基础配置和用户；不提供官方所有配置参数的兼容别名 |

`Code Interpreter` 的 `run_code`、执行上下文、图表/结构化结果属于另一个官方项目 [e2b-dev/code-interpreter](https://github.com/e2b-dev/code-interpreter)，不是本次核对的 `e2b` 基础 SDK。若项目实际需求是执行 Python/JS 代码并拿到 notebook 式结果，建议下一阶段将独立 `e2b-code-interpreter` 模块升为 P1；不能将 `commands.run("python ...")` 宣称为完整替代。此扩展与 Desktop/browser 等产品专属 SDK 不包含在本次基础 API 盘点内。

## 不做的兼容工作

只选择当前非废弃的创建、列表、连接和模板管理路径，不实现旧 REST operation、旧 `beta_*` 别名或废弃字段。首版设计不追求 Python 的动态 overload 和字典参数完全照搬：Java 采用 getter、构造重载和 builder；命令等待命名为 `await()`，避免与 Java Object 的 `wait()` 冲突；列表返回显式 page，避免隐含可变分页状态。

具体的发布、依赖方向和迁出步骤见 [设计文档](design.md)。
