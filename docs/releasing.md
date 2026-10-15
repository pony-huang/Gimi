# 开发、验证与发布

## 首次启用

发布基线已按 GitHub 最新正式版校准为 `0.10.2`，同步记录在 `version.txt`、`.release-please-manifest.json` 和 `CHANGELOG.md`；旧代码中的 `0.6.x` 不再作为版本依据。`bootstrap-sha` 指向 v0.10.2 对应提交 `c53a311cfc95adc2553a610ccccf9e778c436f93`，在找不到历史 Release 时作为提交扫描起点。后续版本 PR 从已发布版本继续推进，不重新发布 v0.10.2。

已检查 GitHub v0.10.2 的 universal 主 App APK：`versionName=0.10.2`、`versionCode=54`。`gradle.properties` 已与该安装版本对齐，下一版本 PR 自动递增到 55。发布脚本还会下载语义版本最高的已公开 Release 的主 App APK，验证覆盖升级所需的递增关系；不会沿用工作流运行次数。

仓库 Settings → Actions → General 中允许 GitHub Actions 创建 Pull Request，并允许工作流所声明的写权限。release-please 使用默认 `GITHUB_TOKEN`，无需 PAT。它创建的版本 PR 不会触发普通 `pull_request` 事件，因此 Prepare Release 显式通过 `workflow_dispatch` 启动 CI；它创建的 tag 也通过可复用工作流显式发布。

配置四个仓库 Actions Secrets，使用与已分发 APK 相同的正式密钥：

- `RELEASE_KEYSTORE_BASE64`：keystore 的 Base64 内容。
- `RELEASE_KEYSTORE_PASSWORD`：keystore 密码。
- `RELEASE_KEY_ALIAS`：签名别名。
- `RELEASE_KEY_PASSWORD`：私钥密码。

在 main 的分支规则中要求 PR 和 `Build & Test` 检查通过，禁止直接推送；为 `v*` tag 配置禁止更新和删除的规则，允许发布机器人创建 tag。这些 GitHub 设置需要仓库管理员配置，修改 YAML 不会自动启用。

## 日常开发

1. 从 main 建功能分支，使用 `feat`、`fix`、`perf` 等 Conventional Commits。使用 squash merge 时，PR 标题就是最终提交标题，应描述完整的用户变化。
2. 本地运行受影响模块的编译与行为测试；涉及资源、DI、构建配置时组装 Debug App。UI 工作遵循 AGENTS.md 中先预览、后实现的要求。
3. 提交 PR，说明改动与实际验证结果。CI 运行发布脚本测试、架构检查、JVM 单测、App Debug 打包、插件编译和 Lint。
4. Review 与必要检查通过后合并 main。main 再次运行 CI，Prepare Release 根据提交维护版本 PR。

## 版本 PR 与更新日志

唯一的版本名称文件是 `version.txt`，App 和所有插件通过公共 convention plugin 读取相同的版本名称与 `releaseVersionCode`。

release-please 自动更新 `version.txt`、`.release-please-manifest.json` 和 `CHANGELOG.md`，按提交分类生成“新增 / 修复 / 优化”。内部重构、文档、CI、测试和维护默认不显示；影响用户的此类变化应在版本 PR 中主动补充。

机器人根据 main 的安装版本号为版本 PR 自动分配 `versionCode + 1`，提交到该分支后再触发 CI。同一个 PR 重复更新不会连续增加；发布重试也不增加，因此日常发版不需要手动编辑 `gradle.properties`。

合并版本 PR 前：

1. 确认机器人已经更新 `gradle.properties` 的 `releaseVersionCode`。版本 PR 的 CI 会检查版本号改变时安装版本也增加。
2. 将提交摘要整理为中文用户说明，合并重复条目，补充权限、插件、协议和数据重置等注意事项。直接编辑当前版本的 CHANGELOG 段落。
3. 确认版本 PR 的 CI 通过，完成需要的真机安装、核心功能和插件验证。
4. 合并版本 PR。release-please 创建 `vX.Y.Z` tag 和 Draft Release，显式启动发布。

release-please 后续提交可能重新生成当前版本草稿，因此在版本 PR 准备合并时做最后整理；若随后有新功能合并，重新检查日志。旧版本条目保持在 CHANGELOG.md 中。

Release 正文在构建时从对应版本的 CHANGELOG 段落提取，覆盖 Draft 的自动正文。GitHub Release 与应用内更新弹窗读取同一份说明。

## 正式构建与发布

发布工作流只允许 `vX.Y.Z` 与 `vX.Y.Z-rc.N`，检查 tag、文件版本、manifest 一致，且 tag 指向 main 历史中的准确提交。后续 CI 和发布都 checkout 已解析的 commit SHA。

先重新执行完整验证，再构建主 App 和五个插件。缺少正式签名配置时停止；密码通过 Gradle 环境变量传递，不拼入命令。每个 APK 都检查 applicationId、versionName、versionCode 和签名证书，主 App 检查上一已公开版本的安装版本号。

全部产物通过后上传到 Draft Release，核对附件名称和大小，最后公开。附件包含 arm64-v8a / universal App APK、五个插件 APK、`SHA256SUMS` 和包含 commit / 版本 / 证书指纹的 `release-metadata.json`。主 App 文件名保留 ABI 后缀，适配应用内更新选择逻辑。R8 mapping 作为内部 Actions Artifact 保存 90 天，应在过期前另行归档长期需要的文件。

正式版本设为 latest；RC 标记为 prerelease，不影响应用的 `/releases/latest` 更新通道。发布后真机检查下载、覆盖安装和核心功能；重要版本优先通过 RC 验证。

## 手动 tag、RC 和失败重试

手动发版也需要先把版本文件、manifest、递增的 versionCode 和对应 CHANGELOG 段落提交并合并 main，再推送匹配的 tag。例如：

```sh
git tag v0.11.0-rc.1
git push origin v0.11.0-rc.1
```

RC 序号从 1 起；正式版本与每个 RC 使用各自递增的 versionCode。当前自动版本 PR 负责正式版本，RC 通过手动版本提交和 tag 发布。发过 RC 后，核对下一次 release-please 版本 PR 的目标版本；如需明确指定目标版本，可在合并提交中使用 `Release-As: 0.11.0` footer。

发布失败会保留未公开 Draft。修复环境或密钥配置后，在 Actions → Publish Android Release → Run workflow 填入已有 tag 补跑；补跑不会构建手动选择分支上的其他源码，也不会新建 tag。由 release-please 生成的 Draft 应在下一次合并版本 PR 前补跑完成，避免多个未完成的发布。

同版本发布串行执行，Draft 可覆盖不完整附件。已公开的 Release 不允许重写附件或正文，重跑会明确失败；代码问题应发布新的补丁版本。不能移动 tag 后补跑；GitHub tag 规则负责保证 tag 不可变。
