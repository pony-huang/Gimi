"""Version PR scaffolding gates: subject grouping, changelog shape, and baseline rejection."""
import tempfile
import unittest
from pathlib import Path
from unittest.mock import patch

import prepare_release as prepare
import release_tools as release


class PrepareReleaseTest(unittest.TestCase):
    """版本 PR 脚手架回归：提交分组、章节格式（notes() 可提取）、基线推进与 versionCode 递增。"""
    def test_changelog_groups_feat_and_fix_and_skips_chore(self):
        """feat/fix 各成一节，chore/test 等杂项不进正文。"""
        section = prepare.changelog_section(
            '1.0.0-beta.6',
            ['feat(chat): 新组件', 'fix!: 破坏性修复', 'chore(main): release 1.0.0-beta.5', 'test: 补测试'],
            'pony-huang/Gimi', 'origin/main', today='2026-11-28')
        self.assertIn('### 新功能与体验\n\n- 新组件', section)
        self.assertIn('### 修复\n\n- 破坏性修复', section)
        self.assertNotIn('release 1.0.0-beta.5', section)
        self.assertNotIn('补测试', section)

    def test_changelog_heading_is_extractable_and_links_compare(self):
        """标题带 compare 链接与日期，notes() 能按版本号提取正文。"""
        with patch.object(prepare, 'previous_tag', return_value='v1.0.0-beta.5'):
            section = prepare.changelog_section(
                '1.0.0-beta.6', ['feat: 功能'], 'pony-huang/Gimi', 'origin/main', today='2026-11-28')
        self.assertIn(
            '[1.0.0-beta.6](https://github.com/pony-huang/Gimi/compare/v1.0.0-beta.5...v1.0.0-beta.6) (2026-11-28)',
            section)
        self.assertEqual(release.notes('# 更新日志\n\n' + section + '## 0.6.3\n\n旧内容', '1.0.0-beta.6'),
                         '### 新功能与体验\n\n- 功能\n')

    def test_changelog_rejects_release_without_user_visible_commits(self):
        """没有 feat/fix 提交时拒绝生成空章节。"""
        with self.assertRaises(ValueError):
            prepare.changelog_section('1.0.0-beta.6', ['chore: 内部整理'], 'pony-huang/Gimi', 'origin/main')

    def test_previous_tag_picks_highest_semantic_tag(self):
        """基线 tag 取可达的最高语义版本，忽略非版本 tag。"""
        with patch.object(prepare, 'run', return_value='v1.0.0-beta.5\nv1.0.0-beta.4\nnightly'):
            self.assertEqual(prepare.previous_tag('origin/main'), 'v1.0.0-beta.5')

    def test_prepare_rejects_regression_and_existing_tag_before_writing(self):
        """版本回退、tag 已存在时在动工作区之前拒绝。"""
        with patch.object(release, 'config', return_value=('1.0.0-beta.6', 70)), patch.object(
            prepare, 'run', return_value=''), patch.object(release, 'version_key', wraps=release.version_key):
            with self.assertRaises(ValueError):
                prepare.prepare('1.0.0-beta.5', 'pony-huang/Gimi', 'origin/main')
        with patch.object(release, 'config', return_value=('1.0.0-beta.5', 70)), patch.object(
            prepare, 'run', return_value=''
        ), patch.object(prepare.subprocess, 'run', return_value=subprocess_result(0)):
            with self.assertRaises(ValueError):
                prepare.prepare('1.0.0-beta.6', 'pony-huang/Gimi', 'origin/main')


def subprocess_result(code):
    """构造 returncode 固定的 CompletedProcess，供 tag 存在性检查打桩。"""
    import subprocess
    return subprocess.CompletedProcess([], code)


if __name__ == '__main__':
    unittest.main()
