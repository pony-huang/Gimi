"""Behavior coverage for the repository's module boundary guard."""
import unittest

from check_architecture import allowed_dependency, cycles, dependencies, forbidden_import, imported_module


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


if __name__ == '__main__':
    unittest.main()
