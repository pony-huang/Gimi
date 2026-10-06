import tempfile
import unittest
from pathlib import Path

from profile_build import ROOT, generated_changes, generated_snapshot, profile_details, summarize_log


class BuildProfileTest(unittest.TestCase):
    def test_task_outcomes_do_not_treat_cached_or_skipped_tasks_as_executed(self):
        result = summarize_log('''> Task :chat:compileDebugKotlin
> Task :app:compileDebugKotlin UP-TO-DATE
> Task :app:packageDebug FROM-CACHE
> Task :app:compileJava NO-SOURCE
> Task :app:kapt SKIPPED
> Task :app:check FAILED
Configuration cache entry reused.
''')
        self.assertEqual([':chat:compileDebugKotlin'], result['executed_tasks'])
        self.assertEqual(1, result['task_outcomes']['FROM-CACHE'])
        self.assertEqual(1, result['task_outcomes']['FAILED'])
        self.assertTrue(result['configuration_cache_reused'])

    def test_profile_preserves_report_and_ranks_only_executed_tasks(self):
        parent = ROOT / 'temp'
        parent.mkdir(exist_ok=True)
        with tempfile.TemporaryDirectory(dir=parent) as folder:
            output = Path(folder)
            report = output / 'gradle.html'
            report.write_text('''<tr><td>:chat:compile</td><td>2.5s</td><td></td></tr>
<tr><td>:app:compile</td><td>3s</td><td>UP-TO-DATE</td></tr>
<tr><td>:chat:ksp</td><td>1s</td><td></td></tr>''')
            result = profile_details(f'See the profiling report at: {report.as_uri()}', output, 'sample')
            self.assertEqual(':chat:compile', result['slowest_executed_tasks'][0]['task'])
            self.assertEqual(2, len(result['slowest_executed_tasks']))
            self.assertTrue((ROOT / result['profile']).is_file())

    def test_generated_changes_ignore_timestamps_and_detect_content_additions_and_removals(self):
        with tempfile.TemporaryDirectory() as folder:
            directory = Path(folder)
            original = directory / 'Factory.java'
            original.write_text('original factory')
            removed = directory / 'Removed.java'
            removed.write_text('removed')
            before = generated_snapshot(directory)
            original.touch()
            self.assertEqual(before, generated_snapshot(directory))
            original.write_text('new factory')
            removed.unlink()
            (directory / 'Added.java').write_text('added')
            self.assertEqual({'added': ['Added.java'], 'removed': ['Removed.java'],
                              'changed': ['Factory.java']},
                             generated_changes(before, generated_snapshot(directory)))

    def test_missing_profile_is_not_an_error(self):
        self.assertEqual({}, profile_details('BUILD SUCCESSFUL', ROOT / 'temp', 'absent'))


if __name__ == '__main__':
    unittest.main()
