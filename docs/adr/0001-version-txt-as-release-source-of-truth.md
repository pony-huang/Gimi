# version.txt 是版本号唯一真源,发版弃用 release-please

2026-11-28 起,版本号以仓库根 `version.txt` 为唯一真源;发版采用 NewPipe 式流程:版本提升(`version.txt` + `gradle.properties` versionCode + CHANGELOG 段落,可由 `prepare_release.py` 辅助生成)是发版分支上的普通开发提交,合并 main 后由发版人本地打 tag 推送,**push tag 触发 `release.yml` 自动构建并发布非 draft 的 GitHub Release**(beta/rc 自动带 prerelease 标记);失败用 `workflow_dispatch` 填已有 tag 补跑。

选这条路的两个原因:一是 release-please 对 `beta.N` / `beta.N.M` 这类版本号推算连续出错(1.0.0-beta.4 算成 2.0.0-beta.4、1.0.0-beta.4.0.1 算成 1.0.1-beta.4),人工纠正成本高于收益,`version.txt` 是构建真正读取的文件,让它当真源消除了"manifest / version.txt / tag"三源不一致;二是评估过"合并版本 PR 即自动发布"的全自动方案,但 Android 发版依赖真机验证,主流开源应用(NewPipe/Signal/Mihon 等)全部保留人工打 tag 这一触发点作为发布闸门,故不追求无人值守。

**Considered Options**:保留 release-please 全自动(被否:版本号推算不可控);保留 release-please 并手动同步 manifest(被否:本质还是三源);"合并版本 PR 即自动打 tag 发布"的 CI 全自动方案(被否:失去真机验证后的发布闸门,与主流开源应用实践相悖);CHANGELOG.md 停止维护(被否:保留但由 `prepare_release.py` 生成段落、发版前人工整理,`notes()` 从中提取 Release 正文)。

**Consequences**:`release-please.yml`、`release-please-config.json`、`.release-please-manifest.json` 与 CI 侧 versionCode 分配脚本删除;`release.yml` 触发为 `push: tags: ['v*']` + 补跑用 `workflow_dispatch`;tag 保护规则需仓库管理员允许创建、禁止更新删除;本地可选预检 `release_tools.py pretag` 在打 tag 前校验形态/一致性/CHANGELOG 章节与版本推进。
