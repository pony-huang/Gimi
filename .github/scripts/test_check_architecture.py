"""Behavior coverage for the repository's module boundary guard."""
import unittest

from check_architecture import (
    allowed_dependency,
    cycles,
    dependencies,
    forbidden_import,
    imported_module,
    signature,
    split_new,
)


class ArchitectureGuardTest(unittest.TestCase):
    def test_production_graph_excludes_test_and_processor_dependencies(self):
        self.assertEqual({':domain:modelcatalog'}, dependencies('''
            implementation(project(":domain:modelcatalog"))
            testImplementation(project(":data:modelcatalog"))
            androidTestImplementation(project(":data:agent"))
            ksp(project(":data:agent"))
            // implementation(project(":feature:chat"))
            /* implementation(project(":app")) */
        '''))

    def test_feature_and_domain_dependencies_follow_layer_direction(self):
        self.assertTrue(allowed_dependency(':feature:chat', ':domain:conversation'))
        self.assertFalse(allowed_dependency(':feature:chat', ':feature:settings'))
        self.assertFalse(allowed_dependency(':domain:modelcatalog', ':data:agent'))
        self.assertFalse(allowed_dependency(':data:modelcatalog', ':data:agent'))
        self.assertFalse(allowed_dependency(':core:common', ':domain:conversation'))
        self.assertTrue(allowed_dependency(':data:agent', ':plugin-api'))

    def test_domain_rejects_framework_and_sdk_imports(self):
        for name in ('android.content.Context', 'androidx.room.Room', 'com.google.adk.kt.tools.BaseTool',
                     'okhttp3.OkHttpClient', 'dagger.hilt.InstallIn'):
            self.assertTrue(forbidden_import(':domain:modelcatalog', name), name)
        self.assertFalse(forbidden_import(':domain:modelcatalog', 'kotlinx.coroutines.flow.StateFlow'))
        self.assertTrue(forbidden_import(':data:modelcatalog', 'com.google.adk.kt.tools.BaseTool'))

    def test_own_feature_and_data_imports_remain_allowed(self):
        self.assertFalse(forbidden_import(':feature:chat', 'github.ponyhuang.gimi.feature.chat.R'))
        self.assertTrue(forbidden_import(':feature:chat', 'github.ponyhuang.gimi.feature.settings.R'))
        self.assertTrue(forbidden_import(':feature:chat', 'github.ponyhuang.gimi.data.agent.AgentFactory'))
        self.assertFalse(forbidden_import(':data:agent', 'github.ponyhuang.gimi.data.agent.AgentFactory'))
        self.assertTrue(forbidden_import(':data:modelcatalog', 'github.ponyhuang.gimi.data.agent.AgentFactory'))

    def test_standard_package_names_identify_direct_module_dependencies(self):
        self.assertEqual(':domain:modelcatalog', imported_module('github.ponyhuang.gimi.domain.modelcatalog.model.Model'))
        self.assertEqual(':core:storage', imported_module('github.ponyhuang.gimi.core.storage.StorageArea'))
        self.assertIsNone(imported_module('kotlinx.coroutines.flow.StateFlow'))

    def test_cycles_report_the_cycle_path(self):
        self.assertEqual(['Dependency cycle: :a -> :b -> :a'], cycles({':a': {':b'}, ':b': {':a'}}))

    def test_shared_dependencies_do_not_form_cycles(self):
        self.assertEqual([], cycles({':a': {':c'}, ':b': {':c'}, ':c': set()}))

    def test_runtime_role_composes_core_and_domain_only(self):
        self.assertTrue(allowed_dependency(':environment-runtime', ':core:security'))
        self.assertTrue(allowed_dependency(':environment-runtime', ':domain:environment'))
        self.assertFalse(allowed_dependency(':environment-runtime', ':feature:chat'))
        self.assertFalse(allowed_dependency(':environment-runtime', ':data:agent'))

    def test_approved_designsystem_voice_exception_stays_narrow(self):
        self.assertTrue(allowed_dependency(':core:designsystem', ':domain:voice'))
        self.assertFalse(
            forbidden_import(':core:designsystem', 'github.ponyhuang.gimi.domain.voice.model.TtsVoice'))
        self.assertTrue(
            forbidden_import(':core:designsystem', 'github.ponyhuang.gimi.domain.conversation.model.Conversation'))
        self.assertFalse(allowed_dependency(':core:designsystem', ':data:agent'))

    def test_baseline_signatures_drop_line_numbers_and_split_reports_only_new(self):
        self.assertEqual('data/agent/src/File.kt: forbidden import x.Y',
                         signature('data\\agent\\src\\File.kt:12: forbidden import x.Y'))
        known, new = split_new(
            ['data/agent/src/File.kt:1: forbidden import x.Y', 'data/agent/src/File.kt:2: forbidden import x.Z'],
            frozenset({'data/agent/src/File.kt: forbidden import x.Y'}))
        self.assertEqual(1, known)
        self.assertEqual(['data/agent/src/File.kt:2: forbidden import x.Z'], new)

    def test_unknown_module_layer_defaults_to_rejected(self):
        self.assertFalse(allowed_dependency(':tooling:some', ':core:common'))
        self.assertFalse(allowed_dependency(':tooling:some', ':domain:voice'))


if __name__ == '__main__':
    unittest.main()
