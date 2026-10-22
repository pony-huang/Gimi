# DeepSeek 文件能力第一阶段核验

日期：2026-10-22。根据用户最新选择，本阶段临时优先验证 DeepSeek；[OpenAI 验证结果](openai-files-phase1-validation.md)独立保留，不混用两家凭据或能力结论。

## 1. 当前结论

**找到 DeepSeek 官方仓库中的文件上传实现，但还没有获得服务端实测成功证据。默认聊天协议保持 Chat Completions。**

- DeepSeek 官方 `deepseek-harness` 仓库有 Files 实现，使用 Messages 兼容接口下的 `/anthropic/v1/files`，带专用 beta 标头。
- 该实现把文件 ID 用于 Messages 图片输入；这不证明同一 ID 能用于 `/chat/completions` 的 OpenAI 风格 `type=file` 输入。
- 本轮不发送 Messages 模型推理请求，不自动改变默认协议，也不因为上传失败而静默回退到内联发送。
- 当前环境代理拒绝 DeepSeek API／文档主机连接，实际请求未到达 DeepSeek 服务端。用户在聊天中提供的凭据未写入源码、文档、探针或日志，也没有发往其他厂商。

尚不能将 DeepSeek 标为“已支持云文件”，也不能据此断言其 Chat Completions 不支持文件。当前结论是未完成核验。

## 2. 官方源码中的事实

固定参考提交：`deepseek-ai/deepseek-harness@687f282e8625c6b8d11ff58211a90524be2e4317`。

| 项目 | 官方仓库中的实现 | 证据边界 |
| --- | --- | --- |
| Files 根地址 | 官方 Messages 根地址为 `https://api.deepseek.com/anthropic`，追加 `/v1/files` | 不等于 OpenAI 风格 `/v1/files` 已受支持 |
| 请求头 | `anthropic-version: 2023-06-01`；`anthropic-beta: files-api-2025-04-14` | 该实现的 Files 与带 ID 的 Messages 调用均使用 beta 标头 |
| API Key 鉴权 | `x-api-key`，而 Chat Completions 使用 Bearer | 凭据限定同一 DeepSeek 官方源，不跨厂商发送 |
| 过期参数 | `expires_after[anchor]=created_at`、`expires_after[seconds]` | 代码请求期限，不代表服务端已实测接受或按时删除 |
| 元数据 | `type=file`、`mime_type`、`size_bytes`、字符串 `created_at` 等 | 与 OpenAI FileObject 结构不同，不能直接套用其解析类型 |
| 返回期限 | 实现未从响应解析远端到期时间，使用创建时间加请求期限作为本地复用界限 | 不能把本地缓存失效说成远端文件已删除 |
| 代码内限制 | 1 小时～30 天期限、128 MiB 上传上限 | 属于该客户端声明与校验，尚无本轮服务端边界测试 |
| 图片引用 | 实现走 Messages 文件引用 | 尚无 Chat Completions 文件引用证据，文档格式也未核验 |

官方来源：

- [提供方说明](https://github.com/deepseek-ai/deepseek-harness/blob/687f282e8625c6b8d11ff58211a90524be2e4317/packages/llm/llm-deepseek/README.zh.md)
- [Files 传输实现](https://github.com/deepseek-ai/deepseek-harness/blob/687f282e8625c6b8d11ff58211a90524be2e4317/packages/llm/llm-deepseek/src/files-api.ts)
- [Messages 根地址与 beta 常量](https://github.com/deepseek-ai/deepseek-harness/blob/687f282e8625c6b8d11ff58211a90524be2e4317/packages/llm/llm-deepseek/src/messages-api.ts)

这些是官方项目的公开实现证据，不是 API 请求实测结果。源码已保存至 `temp/deepseek-files-validation/` 以便复核。

## 3. 实际连接与本地检查

以下有界请求均未带凭据：

| 地址 | 观察结果 |
| --- | --- |
| `https://api.deepseek.com/models` | 代理 CONNECT 返回 403，未到达 DeepSeek |
| `https://api.deepseek.com/files` | 同上，不能据此判断端点存在与否 |
| `https://api-docs.deepseek.com/` | 同上，当前不能直接读取官方 API 文档 |

检查当时的环境状态显示网络策略已生效，但未放行 DeepSeek 主机；也没有 `DEEPSEEK_API_KEY`、`DEEPSEEK_PROBE_MODEL` 环境绑定。聊天中提供密钥不等同于环境已配置。

独立探针通过 4 项本地检查：

1. OpenAI 兼容候选上传请求的 multipart 编码、原始文件字节与过期字段。
2. 官方 Messages Files 风格 multipart 编码，未错误添加 OpenAI `purpose` 字段。
3. 拒绝重定向，不将凭据跟随到其他源。
4. HTTP 成功但 JSON 非法时保留原 HTTP 状态，标为响应解析错误，不误称传输失败。

这 4 项不是 DeepSeek 官方服务测试。原 OpenAI SDK 的 10 项模拟验证及 ADK 的 3 项边界测试，也不能改名当作 DeepSeek 实测。

## 4. 已准备的最小真实验证

探针：`temp/deepseek-files-validation/probe.py`，独立于生产应用。它使用原始 HTTPS 辨识端点与协议，不是最终 SDK 接入实现。

流程：

1. 先用用户指定模型验证 `/chat/completions` 普通文本请求。基础请求不通过时停止，不继续上传。
2. 分别对 OpenAI 风格候选 `/v1/files` 和官方代码中的 `/anthropic/v1/files` 上传小型 PNG、PDF 合成样本。候选端点不表示已经受支持。
3. 如返回文件 ID，在保持 Chat Completions 不变的前提下，尝试 `type=file`、`file.file_id` 引用。
4. 文件中包含随机标记，提示词不包含标记；只有模型读出正确标记才算该样本读取成功。上传成功或返回 HTTP 200 均不足以单独证明读取成功。
5. 只清理本次创建的文件并记录删除请求结果，不删除账号内其他文件。

凭据只从 `DEEPSEEK_API_KEY` 环境变量获取；模型使用 `DEEPSEEK_PROBE_MODEL`。探针不记录密钥、完整错误正文、响应头或原始远端文件 ID。鉴权、限流或传输失败后中止后续矩阵，不自动重试。

默认最多 1 次普通聊天、4 次上传、4 次文件读取及相应清理；只发送合成样本。真实模型调用有费用。只测试 PNG、PDF 的 OpenAI 风格文件片段；某一形式被拒绝不能直接解释为所有输入形式或所有模型都不支持。

本轮显式运行探针时缺少环境绑定，因此只生成阻塞记录，未发送真实上传或推理请求。

## 5. 继续实测所需条件

- 在环境网络设置中放行 `api.deepseek.com`；查文档另需 `api-docs.deepseek.com`。
- 通过环境密钥设置绑定 `DEEPSEEK_API_KEY`，不在聊天中重发。
- 将 `DEEPSEEK_PROBE_MODEL` 设置为本次要验证的实际模型 ID，不凭源码中的建议目录假定账号可用。

网络放行必须通过环境配置生效；修改本地策略 JSON、移除代理或改用另一个地址不能解决该限制。这里是当前环境的实测前提，不是再次请求用户授权使用 DeepSeek。

## 6. 对正式接入的影响

- 仍以 [逐文件独立状态卡需求](cloud-files-and-attachment-status.md)为交互依据，每个附件独立进度、失败、重试与缩略图。
- 如果能证明上传后的文件 ID 可在 Chat Completions 下读取，再按实际支持的模型与格式接入。
- 如果只在 Messages 下有证据，不能悄悄切换协议来完成需求，应先报告协议差异。
- 图片、PDF 之外的格式、真实到期、自然失效错误、跨会话复用及 SDK／Android 集成仍需后续验证。
- 尚未修改生产 SDK 依赖、默认协议、厂商能力开关或正式 UI。

证据文件：`preflight-results.json`、`local-probe-checks.json`、`live-results.json` 及已保存的官方源码，均位于 `temp/deepseek-files-validation/`。
