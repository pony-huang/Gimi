# version.txt 是版本号唯一真源,发版弃用 release-please

2026-11-27 起发版流程以仓库根 `version.txt` 为版本号唯一真源:发版人用 `prepare_release.py` 输入目标版本号,脚本只执行不推算(改 version.txt、生成 CHANGELOG 段落、提版本 PR 并分配 versionCode);合入后本地打 tag、`workflow_dispatch` 触发 `release.yml` 直接发布非 draft 的 GitHub Release(beta/rc 标记 prerelease)。

选这条路是因为 release-please 对 `beta.N` / `beta.N.M` 这类版本号推算连续出错(1.0.0-beta.4 算成 2.0.0-beta.4、1.0.0-beta.4.0.1 算成 1.0.1-beta.4),人工强推纠正的成本已高于收益;而 `version.txt` 是构建真正读取的文件,让它当真源消除了"manifest / version.txt / tag"三源不一致。

**Considered Options**:保留 release-please 并手动同步 manifest(被否:本质还是三源,人工同步无强制保证);保留 release-please 全自动(被否:推算规则不可控);CHANGELOG.md 停止维护改用 releases 页(被否:已有历史内容,保留但封存,后续版本以 GitHub Releases 为准,由 `--generate-notes` 与脚本生成的版本 PR 段落覆盖)。

**Consequences**:`release-please.yml`、`release-please-config.json`、`.release-please-manifest.json` 删除,CHANGELOG.md 封存不再追加;`release.yml` 删除 `push tags` 触发,只留 `workflow_dispatch`;tag 必须打在版本 PR 合入后的 main 上,本地脚本 `pretag-check` 前置校验 tag 正则、与 version.txt 的一致性及配套 runtime APK 产物。
