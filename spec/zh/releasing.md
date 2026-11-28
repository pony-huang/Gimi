# 开发、验证与发布

[English](../en/releasing.md) · 简体中文

## 工作流职责

`.github/workflows/` 下的工作流文件是实际行为的依据：

- `ci.yml` 将开发分支验证交给 `verify.yml`，在 Ubuntu / JDK 21 上运行发布工具测试、架构检查、JVM 单测、App Debug 打包、插件编译和 `app:lintDebug`。触发排除规则见下文“日常开发”。
- `release.yml` 采用 NewPipe 式发版：推送 `v*` tag 触发发布，手动派发已有 tag 仅用于失败补跑。从准确的 tag 提交构建并校验正式签名的 App/插件 APK，完整上传到 Draft 后公开为不可变的 GitHub Release（预发布自动带 prerelease 标记），不发布到 Google Play。
- `prepare_release.py`（可选辅助脚本，本地运行）在当前分支写入 `version.txt`、递增 `releaseVersionCode` 并追加 `CHANGELOG.md` 段落，版本提升随普通开发提交走。版本号由人显式输入，脚本只执行不推算。
- App 和插件通过 `gimi.android.application` 共享 `version.txt` 和 `gradle.properties` 中的 `releaseVersionCode`。

## 首次启用

版本号唯一真源是 `version.txt`（同时记录于 `CHANGELOG.md`）；发布脚本会下载语义版本最高的已公开 Release 的主 App APK，验证覆盖升级所需的递增关系，不沿用工作流运行次数。发布使用默认 `GITHUB_TOKEN`，无需 PAT。

配置四个仓库 Actions Secrets，使用与已分发 APK 相同的正式密钥：

- `RELEASE_KEYSTORE_BASE64`：keystore 的 Base64 内容。
- `RELEASE_KEYSTORE_PASSWORD`：keystore 密码。
- `RELEASE_KEY_ALIAS`：签名别名。
- `RELEASE_KEY_PASSWORD`：私钥密码。

在 main 的分支规则中要求 PR，禁止直接推送。CI 在 PR 和合并进 main 后的 push 上运行；开发分支推送不触发 CI，已有 PR 的分支不会被验证两遍。为 `v*` tag 配置禁止更新和删除的规则；tag 由发版人本地创建。这些 GitHub 设置需要仓库管理员配置，修改 YAML 不会自动启用。

## 日常开发

1. 从 main 建功能分支，使用 `feat`、`fix`、`perf` 等 Conventional Commits。使用 squash merge 时，PR 标题就是最终提交标题，应描述完整的用户变化。
2. 本地运行受影响模块的编译与行为测试；涉及资源、DI、构建配置时组装 Debug App。UI 工作遵循 AGENTS.md 中先预览、后实现的要求。
3. 推送开发分支仅用于共享代码，不触发 CI。提交 PR 后运行发布脚本测试、架构检查、JVM 单测、App Debug 打包、插件编译和 Lint；本地 commit 不会触发远程 CI。
4. 提交 PR，说明改动与实际验证结果。指向 main 的 PR 会运行 `Build & Test` 检查。

## 版本提升与更新日志

唯一的版本名称文件是 `version.txt`，App 和所有插件通过公共 convention plugin 读取相同的版本名称与 `releaseVersionCode`。`version.txt` 是版本号唯一真源——任何工具不得推算或覆盖版本号，只能读取它，或按发版人显式输入写入。

版本提升是发版分支上的普通开发提交（NewPipe 式——没有版本 PR、没有机器人）。在分支上运行 `python3 .github/scripts/prepare_release.py <版本号>`：脚本校验版本推进且 tag 不存在后，更新 `version.txt`、递增 `releaseVersionCode`（现值 + 1），并按可达最高版本 tag 之后的 Conventional Commits 生成 `CHANGELOG.md` 段落（`feat` → 新功能与体验，`fix` → 修复；杂项不进正文）。审查三个文件，把提交摘要整理为中文用户说明，补充权限、插件、协议和数据重置等注意事项，然后随发版分支一起提交。

合并发版分支前：

1. 确认 `gradle.properties` 的 `releaseVersionCode` 已递增。发布时会检查覆盖升级所需的安装版本号递增关系。
2. 等待 PR 的 `Build & Test` 检查通过，完成需要的真机安装、核心功能和插件验证。
3. 合并进 main。

Release 正文在构建时从对应版本的 CHANGELOG 段落提取，覆盖自动正文。GitHub Release 与应用内更新弹窗读取同一份说明。

## 正式构建与发布

发布工作流只允许 `vX.Y.Z`、`vX.Y.Z-beta.N[.M...]` 与 `vX.Y.Z-rc.N[.M...]`，检查 tag 与 `version.txt` 一致，且 tag 指向 main 历史中的准确提交。发布 checkout 已解析的 commit SHA。

发版不重复执行完整 CI，直接构建主 App 和五个插件，并保留版本、签名和产物校验。缺少正式签名配置时停止；密码通过 Gradle 环境变量传递，不拼入命令。每个 APK 都检查 applicationId、versionName、versionCode 和签名证书，主 App 检查上一已公开版本的安装版本号。

全部产物通过后上传到 Draft Release，核对附件名称和大小，最后公开。附件包含 arm64-v8a / universal App APK、五个插件 APK、`SHA256SUMS` 和包含 commit / 版本 / 证书指纹的 `release-metadata.json`。主 App 文件名保留 ABI 后缀，适配应用内更新选择逻辑。R8 mapping 作为内部 Actions Artifact 保存 90 天，应在过期前另行归档长期需要的文件。

正式版本设为 latest；beta 与 RC 标记为 prerelease，不影响应用的 `/releases/latest` 更新通道。发布后真机检查下载、覆盖安装和核心功能；重要版本优先通过预发布验证。

## 打 tag、预发布和失败重试

发版分支合并 main 后，在合并提交上打 tag 并推送——**push tag 就是发布触发器**（NewPipe 式）。可先跑本地预检，覆盖 tag 形态、tag 与 `version.txt` 一致、tag 存在且位于 main 历史内、CHANGELOG 段落就绪、版本超过全部已发布版本：

```sh
python3 .github/scripts/release_tools.py pretag --tag v1.0.0-beta.6
git tag v1.0.0-beta.6
git push origin v1.0.0-beta.6   # 触发 release.yml
```

预发布序号从 1 起；正式版本与每个预发布使用各自递增的 versionCode。预发布层级按 `beta` 早于 `rc`、`rc` 早于正式版本排序，因此 `1.0.0-beta.1` 早于 `1.0.0-rc.1`，后者早于 `1.0.0`。

发布失败会保留未公开 Draft。修复环境或密钥配置后，在 Actions → Publish Android Release → Run workflow 填入已有 tag 补跑；补跑不会构建手动选择分支上的其他源码，也不会新建 tag。下一次合并发版分支前应把未完成的发布补跑完，避免多个发布同时悬空。

同版本发布串行执行，Draft 可覆盖不完整附件。已公开的 Release 不允许重写附件或正文，重跑会明确失败；代码问题应发布新的补丁版本。不能移动 tag 后补跑；GitHub tag 规则负责保证 tag 不可变。
