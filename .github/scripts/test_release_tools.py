"""Release gates must reject mismatched versions and prevent public asset replacement."""
import json
from pathlib import Path
import tempfile
import unittest
from unittest.mock import patch

import release_tools as release


class ReleaseToolsTest(unittest.TestCase):
    @classmethod
    def setUpClass(cls):
        (release.ROOT / "temp").mkdir(exist_ok=True)

    def test_extracts_linked_release_please_heading_and_stops_at_previous(self):
        text = '# 更新日志\n\n## [0.6.4](https://example.test) (2026-10-05)\n\n### 修复\n\n- 下载恢复\n\n## 0.6.3\n\n旧内容\n'
        self.assertEqual(release.notes(text, '0.6.4'), '### 修复\n\n- 下载恢复\n')

    def test_rejects_missing_duplicate_and_empty_notes(self):
        for text in ('## 0.6.3\n旧内容', '## 0.6.4\n', '## 0.6.4\na\n## 0.6.4\nb'):
            with self.subTest(text=text), self.assertRaises(ValueError):
                release.notes(text, '0.6.4')

    def test_rc_sorts_before_stable_and_after_previous_patch(self):
        versions = ['0.7.0', '0.7.0-rc.10', '0.6.9', '0.7.0-rc.2']
        self.assertEqual(sorted(versions, key=release.version_key),
                         ['0.6.9', '0.7.0-rc.2', '0.7.0-rc.10', '0.7.0'])

    def test_double_digit_minor_versions_and_release_notes(self):
        self.assertGreater(release.version_key('0.10.2'), release.version_key('0.6.3'))
        text = '## [0.10.3](https://example.test) (2026-10-05)\n\n- 新增功能\n\n## 0.10.2\n\n- 已发布版本\n'
        self.assertEqual(release.notes(text, '0.10.3'), '- 新增功能\n')
        self.assertEqual(release.notes(text, '0.10.2'), '- 已发布版本\n')

    def test_rejects_invalid_versions(self):
        for version in ('v0.6.4', '01.2.3', '1.2', '1.2.3-rc.0', '1.2.3; echo hi', '1.2.3-beta.1'):
            with self.subTest(version=version), self.assertRaises(ValueError):
                release.version_key(version)

    def test_requires_consistent_manifest_and_valid_install_code(self):
        with tempfile.TemporaryDirectory(dir=release.ROOT / 'temp') as folder:
            root = Path(folder)
            (root / 'version.txt').write_text('0.6.4\n')
            (root / '.release-please-manifest.json').write_text(json.dumps({'.': '0.6.4'}))
            (root / 'gradle.properties').write_text('releaseVersionCode=1001\n')
            self.assertEqual(release.config(root), ('0.6.4', 1001))
            (root / '.release-please-manifest.json').write_text(json.dumps({'.': '0.6.3'}))
            with self.assertRaises(ValueError):
                release.config(root)
            (root / '.release-please-manifest.json').write_text(json.dumps({'.': '0.6.4'}))
            for code in ('0', '2100000001', 'oops'):
                (root / 'gradle.properties').write_text(f'releaseVersionCode={code}\n')
                with self.assertRaises(ValueError):
                    release.config(root)

    def test_published_release_cannot_be_modified(self):
        with patch.object(release, 'config', return_value=('0.6.4', 1001)), patch.object(
            release, 'releases', return_value=[{'tag_name': 'v0.6.4', 'draft': False}]
        ), patch.object(release.subprocess, 'run') as execute:
            with self.assertRaises(ValueError):
                release.publish('v0.6.4')
            execute.assert_not_called()

    def test_latest_includes_rc_but_excludes_draft_and_other_tags(self):
        items = [{'tag_name': tag, 'draft': draft} for tag, draft in (
            ('v0.7.0', True), ('v0.6.3', False), ('v0.7.0-rc.1', False), ('nightly', False))]
        self.assertEqual(release.latest_release(items)['tag_name'], 'v0.7.0-rc.1')

    def test_incomplete_upload_never_publishes_draft(self):
        with tempfile.TemporaryDirectory(dir=release.ROOT / 'temp') as folder:
            root = Path(folder)
            dist = root / 'temp/release/dist'
            dist.mkdir(parents=True)
            (dist / 'app.apk').write_bytes(b'apk')
            (root / 'temp/release/notes.md').write_text('notes')
            with patch.object(release, 'ROOT', root), patch.object(
                release, 'config', return_value=('0.6.4', 1001)
            ), patch.object(release, 'releases', return_value=[{'tag_name': 'v0.6.4', 'draft': True}]), patch.object(
                release, 'run', side_effect=['commit', 'commit', '{"assets": []}']
            ), patch.object(release.subprocess, 'run') as execute:
                with self.assertRaises(ValueError):
                    release.publish('v0.6.4')
                commands = [call.args[0] for call in execute.call_args_list]
                self.assertTrue(any('upload' in command for command in commands))
                self.assertFalse(any('--draft=false' in command for command in commands))

    def test_complete_upload_publishes_rc_without_latest(self):
        with tempfile.TemporaryDirectory(dir=release.ROOT / 'temp') as folder:
            root = Path(folder)
            dist = root / 'temp/release/dist'
            dist.mkdir(parents=True)
            (dist / 'app.apk').write_bytes(b'apk')
            (root / 'temp/release/notes.md').write_text('notes')
            with patch.object(release, 'ROOT', root), patch.object(
                release, 'config', return_value=('0.7.0-rc.1', 1001)
            ), patch.object(release, 'releases', return_value=[]), patch.object(
                release, 'run', side_effect=['commit', 'commit', '{"assets": [{"name":"app.apk","size":3}]}']
            ), patch.object(release.subprocess, 'run') as execute:
                release.publish('v0.7.0-rc.1')
                commands = [call.args[0] for call in execute.call_args_list]
                self.assertIn('--prerelease', commands[1])
                self.assertIn('--draft=false', commands[-1])
                self.assertIn('--latest=false', commands[-1])

    def test_moved_tag_stops_before_upload(self):
        with patch.object(release, 'config', return_value=('0.6.4', 1001)), patch.object(
            release, 'releases', return_value=[]
        ), patch.object(release, 'run', side_effect=['moved', 'built']), patch.object(
            release.subprocess, 'run'
        ) as execute:
            with self.assertRaises(ValueError):
                release.publish('v0.6.4')
            self.assertEqual(execute.call_count, 1)

    def test_package_rejects_wrong_application_or_certificate(self):
        for metadata, digest in ((('other.app', 1001, '0.6.4'), 'a' * 64),
                                 (('github.ponyhuang.gimi', 1001, '0.6.4'), 'b' * 64)):
            with self.subTest(metadata=metadata, digest=digest), tempfile.TemporaryDirectory(
                dir=release.ROOT / 'temp'
            ) as folder:
                root = Path(folder)
                with patch.object(release, 'ROOT', root), patch.object(
                    release, 'config', return_value=('0.6.4', 1001)
                ), patch.dict(release.os.environ, {
                    'GITHUB_REPOSITORY': 'owner/Gimi', 'RELEASE_CERT_SHA256': 'a' * 64,
                }), patch.object(release, 'apk_metadata', return_value=metadata), patch.object(
                    release, 'run', return_value=f'Signer #1 certificate SHA-256 digest: {digest}'
                ), patch.object(release, 'releases') as remote:
                    with self.assertRaises(ValueError):
                        release.package('aapt', 'apksigner')
                    remote.assert_not_called()
                    self.assertEqual(list((root / 'temp/release/dist').iterdir()), [])

    def test_apk_metadata(self):
        with patch.object(release, 'run', return_value="package: name='github.ponyhuang.gimi' versionCode='1001' versionName='0.6.4' platformBuildVersionName='16'"):
            self.assertEqual(release.apk_metadata('aapt', Path('test.apk')), ('github.ponyhuang.gimi', 1001, '0.6.4'))


if __name__ == '__main__':
    unittest.main()
