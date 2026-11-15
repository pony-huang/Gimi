# 架构门禁与 CI 校验

[English](../../en/ci/architecture-guard.md) · 简体中文

Reviewed: 2026-11-15 · 面向：维护 CI / 架构检查的开发者与编码 agent

> 本文记录 GitHub Actions run `43688678563`（job `132734476863`，PR #37）的失败复盘、证据链与改造方案。
> 「DeepSeek Files API」是 PR #37 的标题与提交 `f1250fd3` 的 commit message。
> 结论先行：这不是该提交引入的功能缺陷，而是 2026-10-04 定稿的架构规则与 11 月模块演进脱节，叠加 CI 触发范围漏洞，使存量架构债在分支首次触发 CI 时集中暴露。

## 1. 问题原因

### 1.1 现象

| 项 | 值 |
| --- | --- |
| Run / Job | `43688678563` / `132734476863` |
| PR / 提交 / 分支 | #37 / `f1250fd38cd34cb52af70735be981b720d300a4c` / `feature/1.0.0-beta.4` |
| Workflow / Job 名 | CI → `verify / Build & Test`（`ci.yml` 调用 `verify.yml`） |
| 失败步骤 | 第 3 步 `Check release tooling and architecture`，2026-11-15 07:09:48 UTC，耗时 < 1 s，exit 1 |
| 连带结果 | Set up JDK 21、Unit tests、Assemble debug APK、Lint 全部 skipped；测试报告与 APK 产物为空 |

该步骤由三条命令组成，实际执行结果：

| 命令 | 结果 |
| --- | --- |
| `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` | 41 tests OK |
| `python3 .github/scripts/check_architecture.py` | **73 条违规，exit 1** |
| `python3 .github/scripts/release_tools.py check --base-ref origin/main` | 因 `bash -e` 未执行；本地单跑通过（exit 0） |

`bash -e` 表示任一命令非零退出即中断整个步骤，因此第三行从未执行，后续 Gradle 步骤也被跳过。第一步的 41 个测试来自 `.github/scripts` 下多个 `test_*.py`，其中架构脚本相关的是 7 个测试函数。

### 1.2 直接原因

`check_architecture.py` 是全量源码级检查：只要仓库中存在任何一条违规就 `sys.exit(1)`，没有「已知存量债」的概念。「生产依赖违规」指 `build.gradle.kts` 中非 `test`/`ksp`/`kapt` 的 `project(":...")` 声明越界；「import 违规」指 `src/main` 下 Kotlin 源码的 import 跨越规则边界。

本次它输出 73 条（5 条生产依赖 + 68 条 import），涉及 4 个模块；其中 27 个文件全部来自 import 违规、分属 3 个模块，另有 2 条生产依赖来自只有依赖声明的 `environment-runtime`：

| 模块 | 生产依赖违规 | import 违规 | 典型内容 |
| --- | --- | --- | --- |
| `:data:agent` | 1（→ `:data:modelaccess`） | 56 | `AgentLLMModelFactory`、`ModelConfig`、`ModelRuntimeMetadata`、`ToolLoadingMode`、realtime 探针与厂商工具 |
| `:core:designsystem` | 1（→ `:domain:voice`） | 10 | `VoicePickerPage` / `VoicePickerDialog` 使用的 `TtsVoice`、`VoiceCategory`、`VoiceGender` 等 |
| `:data:voice` | 1（→ `:data:modelaccess`） | 2 | `speechRequestBaseUrl` |
| `:environment-runtime` | 2（→ `:core:security`、`:domain:environment`） | 0 | 伴随运行时 APK 的依赖声明 |

本地工作区（含 30+ 未提交的 voice 改动）跑同一脚本为 74 条，条数差异来自未提交文件的 import 变化。

### 1.3 根本原因

1. **规则停滞**：`check_architecture.py` 与 `test_check_architecture.py` 最后一次改动都是 2026-10-04 的 `0c0ca096`，之后 42 天未更新；`main` 与 feature 分支上两文件的 blob SHA 完全相同。同期模块数从 75 增至 80。
2. **新模块未登记**：11/14 `6f787f44` 新增 `:environment-runtime`（独立 UID 的伴随运行时 APK）。`allowed_dependency()` 只识别 `app/domain/core/feature/data/plugins` 六种 layer，未知 layer 直接 `return False`，因此它对 `:core:security`、`:domain:environment` 的两条依赖是**必然误报**。脚本与单测中甚至没有 `environment-runtime` 字符串。
3. **已批准例外未同步**：`AGENTS.md`「Compose, UI, i18n, and Style」明确把 `VoicePickerPage` / `VoicePickerDialog` 的 `:domain:voice` api 依赖列为第二例外；`AGENTS.md`「Capabilities」也已定义 `environment-runtime` 的角色。治理文档已批准/已定义，检查脚本却没有对应登记。
4. **真实跨层耦合增长**：11 月把厂商模型接入层拆到 `:data:modelaccess` 后，`:data:agent` 直接引用其实现类（`AgentLLMModelFactory` 是 `class`，`ModelConfig` / `ModelRuntimeMetadata` 是 `data class`），形成 55+ 条同层 import。`data/agent/build.gradle.kts` 里仍写着「运行时只依赖 domain 契约」的旧注释，说明实现与架构意图已经分叉。
5. **CI 覆盖漏洞（放大因素）**：`ci.yml` 只在 `push` 时触发且显式排除 `main`（`'!main'`），也没有 `pull_request` 触发；`feature/1.0.0-beta.4` 此前没有任何 CI 记录。因此上述变化在 11/11–11/14 期间从未被门禁拦下，直到本次 push 才一次性结算。

## 2. 问题分析

### 2.1 证据与复现

所有结论均可用下列方式复现（详见附录 A）：`gh run view` 获取步骤与日志；`git archive <sha> | tar -x` 导出历史快照后运行同一份 `check_architecture.py`，逐个提交比对违规集合；`gh api` 对比分支间文件 SHA。

关键对照：把**当前脚本**分别跑在历史提交快照上（回溯模拟，非当时的 CI 记录；`e377c1a0` 一行同时对应 main 上最后一次真实成功的 CI）：

| 提交 | 日期 | 模块数 | 当前脚本回溯结果 |
| --- | --- | --- | --- |
| `e377c1a0` | 10-16 | 67 | 通过（main 上最后一次成功的 CI） |
| `b5afaaa0` | 10-31 | 75 | 通过 |
| `8e48b0dd` | 11-11 | — | **首次失败，11 条**：`:core:designsystem → :domain:voice` |
| `6f787f44` | 11-14 | — | **72 条**：新增 61 条（55 条 agent→modelaccess、2 条 voice→modelaccess、4 条依赖声明） |
| `e5b67f30` | 11-14 | — | 72 条（本次提交的父提交，同样失败） |
| `f1250fd3` | 11-15 | 80 | **73 条** |

本次提交只新增 1 条：`ConversationGenerateTitlePlugin.kt:17` 引入 `CONVERSATION_TITLE_METADATA_KEY`；另有 8 条差异只是把 `anthropic.llm.*` 的 import 换成 `deepseek.llm.*`，换名前后同属 `:data:modelaccess` 包，`forbidden_import()` 判定相同、数量不变。

**因果判定**：父提交 `e5b67f30` 在同一脚本下已是 72 条并 exit 1，所以 CI 由绿转红的转折点不在本次提交。但本次提交确实新增 1 条违规，Step 1 需一并处理；准确表述是「PR #37 的功能代码不是失败原因，但也不是完全无关」。

### 2.2 规则本身的问题

- `allowed_dependency()` 用「目录层级」表达全部约束，只覆盖六种 layer；新模块、特殊角色模块一律落到 `return False`。
- `forbidden_import()` 对 `core → domain.*`、`data → 其他 data.*` 一刀切禁止，不区分「纯数据模型」「接口契约」「实现类」。
- 41 个 Python 单测全部通过，却没有一个覆盖 `environment-runtime`、新依赖模式或已批准例外——测试与规则一起停在 10 月，因此 CI 第一步「绿」并不能说明规则仍然有效。
- 报错只有 `forbidden import xxx` 一行，没有规则 ID、原因或修复建议；调用方（人或 agent）只能猜测。

### 2.3 规模评估

仓库当前 80 个模块（`domain` 22 / `data` 24 / `feature` 16 / `core` 9 / `plugins` 6 / `app` 1 / `environment-runtime` 1 / `plugin-api` 1），1212 个受版本控制的 Kotlin 文件、约 15.7 万行。**模块规模本身合适**：按能力域切分，对 Gradle 并行编译友好；73 条违规高度局部化（4 个模块、27 个文件），不是整体架构崩坏。

不合适的是**规则的表达力**：用 6 个目录层描述 80 个模块。24 个 `data` 模块里同时存在仓储、厂商适配、编排、协议实现；`core` 里 `designsystem`（Compose UI）与 `common`（纯工具）性质不同；独立进程 APK 根本没有角色。规则无法区分这些角色，于是把「编排 → 适配」「UI 组件 → 纯展示模型」这类合法协作一并判死。

### 2.4 与 agent 编程的适配性

若「agent 编程」指用编码 agent 高频改代码：**方向正确、实现不合适**。

- 正确的一面：确定性、可机读、秒级、无需 Android SDK 的硬门禁，正是 agent 修改后最需要的反馈。
- 冲突一，**噪音淹没信号**：73 条存量违规常驻，agent 无法判断「是不是我这轮改坏的」，没有增量判定就等于没有信号。
- 冲突二，**不可解释**：报错无 rule_id / 原因 / 修法，agent 容易用改包名、反射、加抑制注释甚至修改检查脚本的方式绕过。
- 冲突三，**反馈太晚**：只在 CI 跑、排除 main、无 PR 触发；本地迭代拿不到信号。
- 冲突四，**规则是隐式知识**：约定藏在 Python 的 if/else 中，agent 新增模块或依赖时不会自动想到同步——`environment-runtime` 就是例证。

若指项目内的 Agent 运行时架构：规则把「编排层 → 适配层」一刀切禁止，恰好挡住 agent 组合模型能力所需的协作；正确方向是依赖抽象契约、由适配层实现，而不是禁止依赖。

### 2.5 风险提示

架构检查修好后，CI 才会第一次真正执行 `jvmTest`、`:app:assembleDebug`、`:app:lintDebug`。本地工作记录显示 `:app:testDebugUnitTest` 存在 2 个与本问题无关的预存失败（`ManagedDirectoryArchitectureTest` 涉及 `AppNavigation.kt`、`CompanionEnvironmentRepository.kt`、`ModelAccessModule.kt`），尚未在 CI 上验证。需预期「门禁转绿后暴露下一层问题」，不要把它误判为本次修复的回归。

## 3. 解决方案

### 3.1 方案对比

| 方案 | 改动量 | 优点 | 缺点 | 结论 |
| --- | --- | --- | --- | --- |
| 一刀切放宽 `data→data` / `core→domain` | 改 2 行 | 最快 | 门禁实质失效，无法拦截新增越界 | 不推荐 |
| 一次性重构消除 73 条 | 27 文件、跨 4 模块 | 架构最干净 | 与未提交的 voice 改动冲突，回归面大 | 不作为第一步 |
| 删除 / 跳过检查 | 1 行 | 最快 | 失去确定性架构反馈 | 禁止 |
| **基线 + 角色登记 + 触发修正 → 分模块收敛** | 4 个文件，后续按模块拆 PR | CI 立即恢复、保留增量信号、债可逐步还、不动业务代码 | 需维护基线文件 | **推荐** |

### 3.2 Step 0 · 止血（不动任何 Kotlin 代码，可回滚）

改动集中在 4 个文件、不含业务代码，`git revert` 即可恢复原行为。**执行顺序有强制要求**：先登记角色与例外，再生成基线，否则基线会固化本不该存在的误报。

1. 改 `.github/scripts/check_architecture.py`：
   - 新增 `MODULE_ROLES`：`environment-runtime` → `role = runtime`（允许 `:core:*` / `:domain:*`）；`:core:designsystem` → 引用 `AGENTS.md` 第二例外，允许 voice-picker 文件 import `domain.voice.model.*`；
   - 加载基线，只把 `当前结果 − 基线` 的**新增违规**视为失败；基线内条目减少视为「还债」，输出提示但不报错；
   - 每条报错附 `rule_id` 与一句修复建议。
   登记后违规数由 73 降为 **60**（消除 2 条 runtime 依赖误报、1 条 designsystem 依赖、10 条 designsystem import）。
2. 新增 `.github/scripts/architecture-baseline.txt`：**用上一步改完的脚本生成**这 60 条签名。格式为「规则类型 | 规范化路径 | 目标符号」，**不含行号**，避免无关编辑使基线失效。示例：
   ```text
   dep|:environment-runtime|:core:security
   import|data/agent/src/main/.../AgentFactory.kt|github.ponyhuang.gimi.data.modelaccess.AgentLLMModelFactory
   ```
3. 改 `.github/scripts/test_check_architecture.py`：补 4 个测试函数——基线只拦新增、runtime 角色、已批准例外、未登记新模块必须报错。`.github/scripts` 下 41 个测试来自多个 `test_*.py`，其中架构脚本相关的是 7 个测试函数；新增后应为 45 个测试。
4. 改 `.github/workflows/ci.yml`：增加 `pull_request` 触发（或让 `main` 也跑 `verify.yml`），消除「主分支与 PR 不校验」的盲区。此项属配套治理，不影响本次门禁转绿。
5. 同步本文与英文对照；如规则语义变化，同步 `AGENTS.md` 的依赖方向段落。

### 3.3 Step 1 · 分模块收敛（按模块拆 PR，逐条减基线）

Step 0 已完成「登记」；本节只处理**基线内剩余的 60 条**与例外元数据，不重复登记。

| 模块 | 定性 | 处理 |
| --- | --- | --- |
| `:environment-runtime` | Step 0 已登记 | 无后续动作 |
| `:core:designsystem` | Step 0 已登记 | 补齐 `AGENTS.md`「Mandatory boundaries」要求的 owner / removal condition 文字（写在例外登记注释中），不改动代码；**不需要把 voice 模型下沉**——它是治理文档已批准的纯展示模型例外，为迎合表达力不足的规则去动业务代码方向错误 |
| `:data:voice` | 3 条（1 条 → `:data:modelaccess` 依赖 + 2 条 `speechRequestBaseUrl` import） | 把配置常量挪到 `:core:common`，随后移除 `build.gradle.kts` 中的 modelaccess 依赖；或登记窄例外 |
| `:data:agent` | 唯一需要治理的真实耦合 | 两条路：①（推荐）把 `ModelConfig` / `ModelRuntimeMetadata` 等 DTO 迁到契约模块，并把 `AgentLLMModelFactory` 抽成接口，放到 `:domain:modelaccess` 或 `:plugin-api`，实现留 `:data:modelaccess` 并由 Hilt 绑定；② 若短期保留，按 `AGENTS.md`「Document any temporary exception with its owner and removal condition」登记临时例外并设定清除条件 |

`:data:agent` 的取舍需要项目所有者定调：契约下沉更干净但涉及 20+ 文件；登记临时例外更快但会固化同层耦合。

### 3.4 Step 2 · 让规则跟上工程节奏

- 把 `allowed_dependency()` 的 if/else 换成声明式 `architecture.toml`（模块 → role + 允许依赖 + 理由 + owner），新增模块未登记即报错，把「同步」变成强制动作。
- 输出结构化 JSON（`rule_id` / `file` / `line` / `reason` / `fix_hint`），供编码 agent 直接读取与自动修复。
- 本地命令与 CI 共用同一份配置；把检查前移到提交前或 agent 任务收尾步骤，让「delta = 0」成为唯一信号。
- role 建议：`contract` / `orchestrator` / `adapter` / `ui-kit` / `runtime` / `feature`；最低成本版本是推广 `:plugin-api` 这类契约模块。

### 3.5 验收标准

1. `python3 -m unittest discover -s .github/scripts -p 'test_*.py'` 全绿（45 个测试）。
2. `python3 .github/scripts/check_architecture.py` 在当前源码上 exit 0，输出「基线内 60 条 / 新增 0 条」。
3. 增量判定自检：临时给任一 `:core:designsystem` 的非 voice-picker 文件加一条 `import github.ponyhuang.gimi.data.*`，重跑同一命令必须 exit 1 且列出该条；验证后撤销改动。
4. `python3 .github/scripts/release_tools.py check --base-ref origin/main` 通过。
5. push 到分支触发 CI，确认 Unit tests（`jvmTest`）/ Assemble debug APK / Lint（`:app:lintDebug`）首次真正执行；若出现新失败，单独归因，不与架构门禁修复混为一谈。
6. 双语 spec 同步，`AGENTS.md` 索引表包含本文。

### 3.6 只求「让 CI 变绿」的最小路径

最小必要动作只有 3.2 的第 1、2 步：让 `check_architecture.py` 在当前源码上 exit 0。第 3–5 步（单测、触发范围、文档同步）属治理配套，可同 PR 或另开 PR。

但「门禁转绿」不等于「整条 CI 转绿」：见 2.5，`:app:testDebugUnitTest` 有 2 个与本问题无关的预存失败，首次真正执行单测时可能仍红。建议单独开 PR 修 `ManagedDirectoryArchitectureTest` 涉及的 3 个文件，或经评估后调整该测试；不要把它算作 Step 0 的回归，也不要为让 CI 变绿而跳过单测步骤。

### 3.7 执行记录（2026-11-15）

Step 0 与 Step 1 已全部落地，架构违规从 73 条（工作区快照 74 条）收敛到 **6 条**：

| 项 | 结果 |
| --- | --- |
| `check_architecture.py` | 新增 `MODULE_ROLES`（runtime 角色）、`APPROVED_DEPENDENCIES` / `APPROVED_IMPORT_PREFIXES`（designsystem voice-picker 已批准例外）、基线增量判定（`--update-baseline` 再生成）与 `ARCH001–006` 规则编号及逐条修复提示 |
| `architecture-baseline.txt` | 新文件，固化 6 条 ADK 边界残留；签名不含行号 |
| `test_check_architecture.py` | 7 → 11 个用例（runtime 角色、例外窄化、基线签名与只拦新增、未知层默认拒绝） |
| `.github/workflows/ci.yml` | 补 `pull_request` 触发（`main` 的 push 仍排除） |
| `:domain:modelaccess`（新模块） | `ModelConfig` / `ModelRuntimeMetadata` / `toRuntimeMetadata()`、`AgentLlmModelSource` 选择契约、`ToolLoadingMode` 与 `DEFERRED_CATALOG` / `INLINE_DEFINITIONS` / `TOOL_SEARCH_NAME` / `CONVERSATION_TITLE_METADATA_KEY` 协议常量、`isOfficialDeepSeekAnthropicEndpoint` 门控 |
| `:domain:voice` | 新增 `VoiceRealtimeContracts.kt`（`VoiceAudioMarkers` / `VoiceSentenceBuffer` / `RealtimeFunctionCallSink`）；`SpeechRequestEndpoints` 自 `:data:modelaccess` 下沉至此（B 组） |
| `:data:agent` | 约 24 个文件切换 import；新增 `AgentModelRuntime` 门面收敛 ADK `Model` 创建；`OfficialToolFactory` 的厂商分支反转为 `:data:modelaccess` 的 `VendorLocalToolBinder`；probe / voice-support 的 DI 绑定随实现迁回 modelaccess |
| 残留 6 条 | 全部是 domain 无法承载的 ADK 类型边界（`adk.Model` / `adk.Content` / `adk.BaseTool`）：`AgentModelRuntime`、`VendorLocalToolBinder`、`VoiceLiveModelFactory` ×2、`RealtimeManualInputSink` 及其依赖声明。已按 `AGENTS.md`「Mandatory boundaries」登记例外（owner + removal condition）并进基线 |

本地验证：`unittest` 全绿；`check_architecture.py` exit 0（81 模块，基线内 6 / 新增 0）；故意注入越界 import 即报 `2 new` 并 exit 1，恢复后 exit 0；`release_tools.py check` 通过；`:app:compileDebugKotlin` 全量编译通过（Hilt 依赖图完整）。CI 侧首次真跑单测时仍可能碰到 2.5 所述的 2 个预存失败，按 3.6 处理。

## 4. 已决策项（2026-11-15 执行）

- `:data:agent → :data:modelaccess`：采用「契约下沉 + ADK 边界例外」，见 3.7 与 `AGENTS.md`「Mandatory boundaries」。
- 基线清理：不设硬性期限；文件头注明只能由 `--update-baseline` 再生成、靠修代码缩减，禁止手改。
- `ci.yml`：补 `pull_request` 触发；`main` 的 push 仍排除，发布流水线不受影响。

## 附录 A · 复现命令

以下命令在仓库根目录执行；`gh` 需已登录并对 `pony-huang/Gimi` 有读权限。CI 使用 `python3`，Windows 本地若只有 `python` 可直接替换。

```bash
# 1. 查看失败 run、步骤与日志
gh run view 43688678563 --repo pony-huang/Gimi --json status,conclusion,jobs
gh run view --log --job 132734476863 --repo pony-huang/Gimi

# 2. 验证规则未同步：分支间脚本 SHA 与最后改动日期
gh api repos/pony-huang/Gimi/contents/.github/scripts/check_architecture.py?ref=main --jq .sha
gh api repos/pony-huang/Gimi/contents/.github/scripts/check_architecture.py?ref=feature/1.0.0-beta.4 --jq .sha
git log -1 --format='%h %ad %s' --date=short -- .github/scripts/check_architecture.py

# 3. 本地复现架构检查与发布工具检查
python3 .github/scripts/check_architecture.py
python3 .github/scripts/release_tools.py check --base-ref origin/main
python3 .github/scripts/check_architecture.py 2>&1 | wc -l

# 4. 历史快照回溯（脚本用 __file__ 定位仓库根，可从任意 cwd 运行）
for c in e377c1a0 b5afaaa0 8e48b0dd 6f787f44 e5b67f30 f1250fd3; do
  d="/tmp/gimi-$c"; mkdir -p "$d"
  git archive "$c" | tar -x -C "$d"
  printf '%s ' "$c"
  python3 "$d/.github/scripts/check_architecture.py" 2>&1 | head -1
done

# 5. 对比两个提交的违规集合
diff <(python3 /tmp/gimi-e5b67f30/.github/scripts/check_architecture.py 2>&1) \
     <(python3 /tmp/gimi-f1250fd3/.github/scripts/check_architecture.py 2>&1)

# 6. 规模统计
git ls-files '*.kt' | wc -l
python3 -c "import re,pathlib; print('modules:', len(re.findall(r'include\\(\\s*\"(:[^\"]+)\"\\s*\\)', pathlib.Path('settings.gradle.kts').read_text())))"
```

## 附录 B · 规则与治理文档索引

| 位置 | 内容 |
| --- | --- |
| `.github/scripts/check_architecture.py` | `allowed_dependency()`、`forbidden_import()`、`imported_module()`、`cycles()`、`MODULE_ROLES`、`APPROVED_DEPENDENCIES` / `APPROVED_IMPORT_PREFIXES`、基线增量判定 |
| `.github/scripts/architecture-baseline.txt` | 已知违规签名（无行号）；仅由 `--update-baseline` 再生成 |
| `.github/scripts/test_check_architecture.py` | 11 个测试函数：分层方向、domain 禁用前缀、runtime 角色、已批准例外、基线增量、未知层默认拒绝 |
| `.github/workflows/verify.yml` | 第 3 步三条命令的顺序与 `bash -e` 行为 |
| `.github/workflows/ci.yml` | push 触发（排除 `main`）+ `pull_request` 触发 |
| `AGENTS.md` | 模块地图、依赖方向、强制边界、designsystem 例外、例外登记要求、双语 spec 规范 |
| `spec/en/releasing.md` / `spec/zh/releasing.md` | CI/CD 与发布流程的权威说明 |
