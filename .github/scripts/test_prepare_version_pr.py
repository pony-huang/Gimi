"""Install version allocation is stable across release PR refreshes and retries."""
import base64
import unittest
from unittest.mock import patch

import prepare_version_pr as prepare


def file_response(text, sha='file-sha'):
    return {'content': base64.b64encode(text.encode()).decode(), 'sha': sha}


class PrepareVersionPrTest(unittest.TestCase):
    """版本号分配的幂等与防错回归：重复更新不连涨、手动调高不被压回、并发写失败可见。"""
    def test_next_version_is_allocated_once(self):
        """基线 +1 后再次分配不再变化：幂等是版本 PR 反复更新的前提。"""
        properties = '# release properties\nreleaseVersionCode=54\nother=value\n'
        allocated = prepare.increment_properties(properties, 54)
        self.assertEqual(allocated, properties.replace('=54', '=55'))
        self.assertEqual(prepare.increment_properties(allocated, 54), allocated)

    def test_next_rc_and_stable_use_new_main_baseline(self):
        """合并后基线推进：下个 rc 与正式版都从新 main 的 versionCode 续增。"""
        properties = 'releaseVersionCode=55\n'
        self.assertEqual(prepare.increment_properties(properties, 55), 'releaseVersionCode=56\n')
        self.assertEqual(prepare.increment_properties('releaseVersionCode=56\n', 56), 'releaseVersionCode=57\n')

    def test_keeps_explicit_higher_code(self):
        """手动调高的 versionCode 不被自动分配压回（人工修复优先）。"""
        self.assertEqual(prepare.increment_properties('releaseVersionCode=60\n', 54), 'releaseVersionCode=60\n')

    def test_rejects_missing_duplicate_and_exhausted_codes(self):
        """零/重复/越界的 releaseVersionCode 一律拒绝，不静默取值。"""
        for text, base in (('other=value\n', 54), ('releaseVersionCode=54\nreleaseVersionCode=55\n', 54),
                           ('releaseVersionCode=2100000000\n', 2100000000)):
            with self.subTest(text=text, base=base), self.assertRaises(ValueError):
                prepare.increment_properties(text, base)

    def test_commits_code_to_the_version_pr_branch(self):
        """contents 提交必须落在版本 PR 分支并携带正确 sha/内容，防并发覆盖。"""
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'release-please--branches--main', 'repo': {'full_name': 'owner/Gimi'}}}
        with patch.object(prepare, 'api', side_effect=[pr, file_response('0.11.0\n'),
            file_response('releaseVersionCode=54\n'), {}]) as api:
            prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            payload = api.call_args_list[-1].args[1]
            self.assertEqual(base64.b64decode(payload['content']).decode(), 'releaseVersionCode=55\n')
            self.assertEqual(payload['sha'], 'file-sha')
            self.assertEqual(payload['branch'], pr['head']['ref'])

    def test_same_pr_rerun_does_not_create_extra_commit(self):
        """现值已等于分配结果时不再产生新提交（重试安全）。"""
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'release-please--branches--main', 'repo': {'full_name': 'owner/Gimi'}}}
        with patch.object(prepare, 'api', side_effect=[pr, file_response('0.11.0\n'),
            file_response('releaseVersionCode=55\n')]) as api:
            prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            self.assertEqual(api.call_count, 3)

    def test_failed_write_propagates_without_a_commit(self):
        """写入失败向上抛错：让 release-please 运行可见地失败而不是静默跳过。"""
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'release-please--branches--main', 'repo': {'full_name': 'owner/Gimi'}}}
        with patch.object(prepare, 'api', side_effect=[pr, file_response('0.11.0\n'),
            file_response('releaseVersionCode=54\n'), RuntimeError('concurrent edit')]):
            with self.assertRaises(RuntimeError):
                prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)

    def test_rejects_pr_from_another_repo(self):
        """拒绝跨仓库 PR：contents 提交必须落在本仓库的版本分支上。"""
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'branch', 'repo': {'full_name': 'other/Gimi'}}}
        with patch.object(prepare, 'api', return_value=pr) as api:
            with self.assertRaises(ValueError):
                prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            self.assertEqual(api.call_count, 1)


if __name__ == '__main__':
    unittest.main()
