"""Install version allocation is stable across release PR refreshes and retries."""
import base64
import unittest
from unittest.mock import patch

import prepare_version_pr as prepare


def file_response(text, sha='file-sha'):
    return {'content': base64.b64encode(text.encode()).decode(), 'sha': sha}


class PrepareVersionPrTest(unittest.TestCase):
    def test_next_version_is_allocated_once(self):
        properties = '# release properties\nreleaseVersionCode=54\nother=value\n'
        allocated = prepare.increment_properties(properties, 54)
        self.assertEqual(allocated, properties.replace('=54', '=55'))
        self.assertEqual(prepare.increment_properties(allocated, 54), allocated)

    def test_next_rc_and_stable_use_new_main_baseline(self):
        properties = 'releaseVersionCode=55\n'
        self.assertEqual(prepare.increment_properties(properties, 55), 'releaseVersionCode=56\n')
        self.assertEqual(prepare.increment_properties('releaseVersionCode=56\n', 56), 'releaseVersionCode=57\n')

    def test_keeps_explicit_higher_code(self):
        self.assertEqual(prepare.increment_properties('releaseVersionCode=60\n', 54), 'releaseVersionCode=60\n')

    def test_rejects_missing_duplicate_and_exhausted_codes(self):
        for text, base in (('other=value\n', 54), ('releaseVersionCode=54\nreleaseVersionCode=55\n', 54),
                           ('releaseVersionCode=2100000000\n', 2100000000)):
            with self.subTest(text=text, base=base), self.assertRaises(ValueError):
                prepare.increment_properties(text, base)

    def test_commits_code_before_dispatching_ci(self):
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'release-please--branches--main', 'repo': {'full_name': 'owner/Gimi'}}}
        with patch.object(prepare, 'api', side_effect=[pr, file_response('0.11.0\n'),
            file_response('releaseVersionCode=54\n'), {}]) as api, patch.object(prepare.subprocess, 'run') as dispatch:
            prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            payload = api.call_args_list[-1].args[1]
            self.assertEqual(base64.b64decode(payload['content']).decode(), 'releaseVersionCode=55\n')
            self.assertEqual(payload['sha'], 'file-sha')
            self.assertEqual(payload['branch'], pr['head']['ref'])
            dispatch.assert_called_once_with(['gh', 'workflow', 'run', 'ci.yml', '--ref', pr['head']['ref']], check=True)

    def test_same_pr_rerun_does_not_create_extra_commit(self):
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'release-please--branches--main', 'repo': {'full_name': 'owner/Gimi'}}}
        with patch.object(prepare, 'api', side_effect=[pr, file_response('0.11.0\n'),
            file_response('releaseVersionCode=55\n')]) as api, patch.object(prepare.subprocess, 'run') as dispatch:
            prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            self.assertEqual(api.call_count, 3)
            dispatch.assert_called_once()

    def test_failed_write_does_not_dispatch_ci(self):
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'release-please--branches--main', 'repo': {'full_name': 'owner/Gimi'}}}
        with patch.object(prepare, 'api', side_effect=[pr, file_response('0.11.0\n'),
            file_response('releaseVersionCode=54\n'), RuntimeError('concurrent edit')]), patch.object(
            prepare.subprocess, 'run'
        ) as dispatch:
            with self.assertRaises(RuntimeError):
                prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            dispatch.assert_not_called()

    def test_rejects_pr_from_another_repo(self):
        pr = {'state': 'open', 'base': {'ref': 'main'},
              'head': {'ref': 'branch', 'repo': {'full_name': 'other/Gimi'}}}
        with patch.object(prepare, 'api', return_value=pr) as api, patch.object(prepare.subprocess, 'run') as dispatch:
            with self.assertRaises(ValueError):
                prepare.prepare(12, 'owner/Gimi', '0.10.2', 54)
            self.assertEqual(api.call_count, 1)
            dispatch.assert_not_called()


if __name__ == '__main__':
    unittest.main()
