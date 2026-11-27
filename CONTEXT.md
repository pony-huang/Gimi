# Glossary

- **版本号真源(version source of truth)**:仓库根 `version.txt`。任何工具、脚本、tag 都不得推算或覆盖版本号,只能读取它或按发版人显式输入写入它。
- **版本 PR(release PR)**:由 `prepare_release.py` 生成的、只改 `version.txt`/`versionCode`/`CHANGELOG.md` 段落的 PR。它是版本号进入 main 的唯一入口。
- **发版 tag(release tag)**:`v` + version.txt 内容(如 `v1.0.0-beta.6`)。打在版本 PR 合入后的 main 上,本地 `pretag-check` 通过后手动创建。
- **发版执行(release run)**:`workflow_dispatch` 触发 `release.yml` 并传入 tag。`push tag` 不再触发发布。
