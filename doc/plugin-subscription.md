# 插件列表与插件订阅源（整体报告）

- 日期：2026-10-20
- 分支/worktree：`feature/a2ui` @ `async-foraging-barto`
- 状态：实现完成，单元测试、Debug 打包、架构检查、真机验证均通过

## 1. 背景与需求演进

最初目标是“MCP 商店”。经过多轮确认，最终落地为**插件列表 + 多订阅源**，不再有独立商店页面。关键决策：

| 阶段 | 结论 |
| --- | --- |
| 数据来源 | 官方 MCP Registry 仅作为选品/命名/简介参考，不进入运行时 |
| 豆包数据 | 不爬取。无公开目录 API、登录态数据，存在 ToS/版权/稳定性风险；如有官方授权接口再接入 |
| 商品形态 | APK 插件（可原生登录、WebView、任意 API，绕开手机端 MCP 的 stdio/OAuth 限制） |
| 商店页面 | 最终移除；插件列表本身改为卡片式列表 |
| 分类/精选 | 移除 |
| 版本/工具数量 | 从列表与详情中移除 |
| 详情入口 | 卡片右上角菜单新增“详细信息”，位于“卸载插件”上方 |
| 详情内容 | 上一版商店详情布局；移除 SHA-256/签名安全提示 |
| 订阅地址 | 支持追加多个 HTTPS 订阅源，默认官方源 |
| 签名校验 | 按需求移除；保留 SHA-256 下载校验与系统安装器确认 |

## 2. 最终功能

### 2.1 插件列表

- 单列卡片列表（`LazyColumn`），每项包含：
  - 插件图标（已安装优先使用 APK 真实图标，未安装使用内置矢量图）
  - 名称、简介（来自订阅目录；已安装但不在目录中的插件只显示名称）
  - 已安装：`已安装` 文案 + 启停开关
  - 未安装：安装按钮 / 下载进度 / 失败重试
  - 右上角三点菜单：`详细信息`、`卸载插件`（红色，仅已安装）
- 顶部搜索框：匹配名称、简介、标签与 key。
- 订阅源加载失败时显示提示条；部分失败与全部失败使用不同文案。
- 下拉刷新：重新发现已安装插件并强制刷新全部订阅源。
- 右上角 RSS 图标进入“插件订阅源”页面。

### 2.2 详细信息页

- 顶部卡片：图标、名称、简介。
- `插件介绍`、`能力`、`在 GitHub 查看`。
- 已安装：`管理插件` 进入插件配置页。
- 未安装：直接安装。
- 不显示版本、工具数量、SHA-256、签名信息。
- 已安装但不在订阅目录中的插件显示“该插件未在订阅源中提供详细介绍”。

### 2.3 插件订阅源

- 统一使用设置页组件：`PreferenceScaffold`、`PreferencePageContainer`、`PreferenceSectionTitle`、`PreferenceGroupCard`、`PreferenceListItem`、`PreferenceSectionNote`。
- 已添加的源逐行展示：
  - 官方源标题为“官方订阅源”，自定义源标题为域名。
  - 副标题为完整 URL。
  - 右侧红色删除按钮，可单独移除。
- `添加订阅源` 打开对话框，输入 HTTPS 地址后追加；重复地址、非 HTTPS、含空白字符、超长地址会被拒绝。
- `恢复默认源`：仅保留官方源。
- 列表允许为空；为空时仅使用 APK 内置目录，不显示错误。
- 多个源并行拉取，按 key 合并，**先添加的源优先**；条目最终按 `sortOrder`、标题排序。

## 3. 架构与模块

| 模块 | 主要改动 |
| --- | --- |
| `:domain:plugin` | 新增目录/Release/安装/订阅契约与 `InstallPluginUseCase`；`PluginCatalogEntry`、`PluginRelease`、`PluginInstallResult` 等模型 |
| `:data:plugin` | 内置目录 asset、订阅目录解析与多源合并、订阅地址持久化、GitHub Release 网关、APK 下载与校验、受管目录、Hilt 绑定 |
| `:feature:plugin` | 插件列表、详情页、订阅源页、两个 ViewModel、卡片/图标组件、文案 |
| `:core:apk`（新增） | 从 `:data:appupdate` 抽出 `ApkInstaller`，应用自更新与插件安装共用 |
| `:data:appupdate` | 改为依赖 `:core:apk`，更新 import；自更新仍保留签名一致性检查 |
| `:app` | 导航无需改动，`PluginEntryProvider` 已在导航链中 |

依赖方向符合项目规范：feature → domain/core，data → domain/core，core → core；`check_architecture.py` 通过。

## 4. 数据流

### 4.1 订阅源状态

`PluginSubscriptionStore`（`:domain:plugin`）：

```kotlin
interface PluginSubscriptionStore {
    val urls: StateFlow<List<String>>
    val defaultUrl: String
    fun add(url: String)
    fun remove(url: String)
    fun reset()
}
```

实现：`SharedPreferencesPluginSubscriptionStore`

- SharedPreferences：`plugin_subscription`，key `urls_v1`
- 以换行分隔保存 URL 列表
- 首次启动：仅官方默认源
- key 存在但为空：表示用户主动清空，保持空列表
- 默认地址：
  `https://raw.githubusercontent.com/pony-huang/Gimi/main/plugin-subscription.json`

### 4.2 目录读取与合并

`SubscribedPluginCatalogRepository.load(force)`：

1. 读取 `subscriptionStore.urls`。
2. 列表为空 → 使用内置 asset `plugin_catalog.json`，`source=BUNDLED`、`error=false`。
3. 非空 → 并行请求所有 HTTPS 源：
   - 单个响应上限 2 MiB
   - 拒绝 HTTPS 降级重定向
   - 单条无效数据由解析器跳过
4. 合并：
   - 按添加顺序合并，同一 key 只保留先出现的源
   - 最终按 `sortOrder`、标题排序
5. 结果：
   - 至少一个源成功：`source=REMOTE`，`error = 是否有源失败`
   - 全部失败：回退内置目录，`source=BUNDLED`、`error=true`
6. 进程内缓存 5 分钟，按 URL 列表区分；`force=true` 时重新请求。

### 4.3 已安装插件合并

`PluginSettingsViewModel` 将：

- 订阅目录条目
- `PluginRepository.plugins`（已加载插件）

合并为 `PluginListItemUi`：

- 目录条目关联 `installed`
- 目录中不存在的已安装插件单独保留
- 排序：已安装优先 → `sortOrder` → 标题

### 4.4 安装流程

`InstallPluginUseCase`：

1. 条目有 `apkUrl` → 直接构造 `PluginReleaseAsset`（可选 `sha256`、`sizeBytes`）。
2. 否则 → 请求 GitHub 最新 Release，按 `releaseAssetPrefix` 匹配 `.apk`。
3. `AndroidPluginPackageInstaller`：
   - 仅 HTTPS
   - 单包上限 50 MiB
   - 下载到 `shareable/plugin/packages`（`ManagedDirectorySpec`：CACHE / TEMPORARY / FILE_PROVIDER）
   - 下载前清理旧包
   - 校验最终文件大小与 SHA-256（若提供）
   - **不再校验签名**
4. ViewModel 通过 `LaunchInstaller(contentUri)` effect 调起系统安装器。
5. 安装完成返回后刷新权限、已安装插件与目录。

失败原因：`NETWORK`、`CHECKSUM_MISMATCH`、`NO_RELEASE_ASSET`、`PACKAGE_TOO_LARGE`、`STORAGE`、`UNKNOWN`。

## 5. 订阅源文件格式

默认文件：仓库根目录 `plugin-subscription.json`。

```json
{
  "schemaVersion": 1,
  "name": "Gimi 官方插件源",
  "signingCertificateSha256": null,
  "entries": [
    {
      "key": "zhihu",
      "packageName": "github.ponyhuang.gimi.plugin.zhihu",
      "title": "知乎",
      "summary": { "zh": "站内搜索、热榜、直答与知识库", "en": "..." },
      "description": { "zh": "...", "en": "..." },
      "highlights": [
        { "zh": "站内与全网搜索", "en": "On-site and web search" }
      ],
      "sortOrder": 10,
      "pluginVersion": 2,
      "releaseAssetPrefix": "Gimi-plugin-zhihu-v",
      "repoUrl": "https://github.com/pony-huang/Gimi",
      "iconKey": "zhihu",
      "tags": ["知乎", "问答", "热榜"],
      "apkUrl": null,
      "sha256": null,
      "sizeBytes": null
    }
  ]
}
```

说明：

- `name`、`signingCertificateSha256` 当前解析器会忽略；保留未知字段是为了兼容未来扩展。
- 官方源使用 `releaseAssetPrefix`，从 GitHub 最新 Release 解析 APK。
- 自定义源可直接提供：
  - `apkUrl`：HTTPS APK 直链
  - `sha256`：64 位十六进制
  - `sizeBytes`：字节数
- 未提供 `sha256` 时跳过摘要校验。
- `iconKey` 对应宿主内置图标；未知 key 回退通用插件图标。

## 6. 安全与信任模型

- 订阅 URL 仅接受 HTTPS。
- 单个订阅响应上限 2 MiB；拒绝 HTTPS 降级重定向。
- APK 仅接受 HTTPS；单包上限 50 MiB。
- 下载后校验大小与 SHA-256（源提供时）。
- **已按需求移除 APK 签名校验**：
  - 安装信任来自用户选择的订阅源 + HTTPS + 可选 SHA-256 + 系统安装器二次确认。
  - 自定义源由用户自行信任。
  - 系统安装器仍会执行 Android 自身的签名一致性约束（例如覆盖安装不同签名的同包名 APK 会被系统拒绝）。
- 不做静默安装，始终由系统安装器让用户确认。

## 7. 国际化与视觉

- `feature/plugin/src/main/res/values/strings.xml`（中文默认）与 `values-en/strings.xml` 已补齐：
  - 列表、安装状态、订阅源、添加对话框、详情页。
- 图标：
  - 内置 5 个第一方插件矢量图
  - 通用插件图标
  - V2EX 夜间变体 `drawable-night/ic_plugin_v2ex.xml`，避免深色单色图标不可见
- 主题：全部使用 Material 3/design-system token，不硬编码颜色。

## 8. 主要文件清单

新增：

- `core/apk/build.gradle.kts`
- `core/apk/src/main/java/github/ponyhuang/gimi/core/apk/ApkInstaller.kt`（从 appupdate 移入）
- `domain/plugin/.../model/PluginStoreModels.kt`
- `domain/plugin/.../repository/PluginCatalogRepository.kt`
- `domain/plugin/.../repository/PluginPackageInstaller.kt`
- `domain/plugin/.../repository/PluginReleaseRepository.kt`
- `domain/plugin/.../repository/PluginInstallEnvironment.kt`
- `domain/plugin/.../repository/PluginSubscriptionStore.kt`
- `domain/plugin/.../usecase/InstallPluginUseCase.kt`
- `data/plugin/src/main/assets/plugin_catalog.json`
- `data/plugin/.../catalog/PluginCatalogDto.kt`
- `data/plugin/.../catalog/PluginCatalogParser.kt`
- `data/plugin/.../catalog/SubscribedPluginCatalogRepository.kt`
- `data/plugin/.../remote/PluginReleaseDto.kt`
- `data/plugin/.../remote/GitHubPluginReleaseRepository.kt`
- `data/plugin/.../install/AndroidPluginPackageInstaller.kt`
- `data/plugin/.../install/DefaultPluginInstallEnvironment.kt`
- `data/plugin/.../subscription/SharedPreferencesPluginSubscriptionStore.kt`
- `feature/plugin/.../PluginDetailScreen.kt`
- `feature/plugin/.../PluginListComponents.kt`
- `feature/plugin/.../PluginSubscriptionScreen.kt`
- `feature/plugin/.../PluginSubscriptionViewModel.kt`
- `feature/plugin/src/main/res/drawable/ic_plugin_*.xml`
- `feature/plugin/src/main/res/drawable-night/ic_plugin_v2ex.xml`
- `plugin-subscription.json`（默认官方订阅源）

修改：

- `settings.gradle.kts`（`:core:apk`）
- `data/appupdate/build.gradle.kts`
- `data/appupdate/.../DefaultAppUpdateRepository.kt`
- `data/appupdate/.../UpdateActionReceiver.kt`
- `data/plugin/build.gradle.kts`
- `data/plugin/.../PluginStorage.kt`
- `data/plugin/.../di/PluginModule.kt`
- `feature/plugin/.../PluginContract.kt`
- `feature/plugin/.../PluginNavigation.kt`
- `feature/plugin/.../PluginSettingsScreen.kt`
- `feature/plugin/.../PluginSettingsViewModel.kt`
- `feature/plugin/src/main/res/values/strings.xml`
- `feature/plugin/src/main/res/values-en/strings.xml`
- `README.md`
- `README.zh-CN.md`

测试：

- `data/plugin/.../catalog/PluginCatalogParserTest.kt`
- `data/plugin/.../remote/GitHubPluginReleaseRepositoryTest.kt`
- `domain/plugin/.../usecase/InstallPluginUseCaseTest.kt`
- `feature/plugin/.../PluginSettingsViewModelTest.kt`
- `feature/plugin/.../PluginSubscriptionViewModelTest.kt`

## 9. 测试与验证

已执行：

```powershell
.\gradlew.bat :domain:plugin:test :data:plugin:testDebugUnitTest `
  :feature:plugin:testDebugUnitTest :data:appupdate:testDebugUnitTest `
  :app:assembleDebug
```

结果：全部通过。

- 新增/覆盖用例：
  - 目录解析：5
  - GitHub Release 网关：5
  - 安装用例：7
  - 插件列表 ViewModel：13
  - 订阅 ViewModel：6
- `PYTHONUTF8=1 python .github/scripts/check_architecture.py`：68 个模块通过。
- `git diff --check`：通过（仅 Git 的 LF/CRLF 提示，无空白错误）。

真机验证（SM-S9310）：

- 仅安装了调试包 `github.ponyhuang.gimi.debug`；正式版 `github.ponyhuang.gimi` 与数据未动。
- 验证内容：列表、卡片菜单（详细信息/卸载）、详情页、订阅页、添加对话框、追加/删除订阅源。
- 默认 `raw.githubusercontent.com` 在当前手机网络不可达，App 正确回退内置目录并显示提示；添加自定义源可正常工作。
- 测试用订阅源已删除，夜间模式已恢复为 `auto`。

截图（`build/`，git-ignored）：

- `final-list.png`
- `final-menu.png`
- `final-detail.png`
- `multi-subscription.png`
- `multi-subscription-dialog.png`
- `multi-subscription-added2.png`

验证环境说明：Jugg CLI 的项目状态绑定在主 checkout，无法编译当前 worktree，因此按项目规范回退到 worktree 内的 Gradle 命令。

## 10. 后续建议

1. **提交并推送 `plugin-subscription.json`**：默认订阅 URL 指向 `main` 分支，未推送前手机端只能使用内置回退目录。
2. **发布自动化**：如果希望订阅源在每次 Release 自动带上 APK 直链与 SHA-256，可在 `release_tools.py`/`release.yml` 中生成订阅文件并作为 Release 资产上传，默认 URL 改为 `releases/latest/download/plugin-subscription.json`。
3. **镜像/代理**：国内网络访问 `raw.githubusercontent.com` 可能不稳定，可提供镜像地址或在订阅页添加可达源。
4. **可选签名固定**：当前按需求移除了签名校验；若后续允许第三方订阅源，建议重新引入“源级证书指纹”或“发布者证书固定”。
5. **插件更新检测**：当前已安装插件只显示启用开关，不做版本更新提示；后续可基于订阅目录的 `pluginVersion` 增加“可更新”状态。
6. **详情页元数据**：当前详情页不显示版本/工具数量；如需要可在订阅源中保留但不在列表展示。
