package github.ponyhuang.gimi.data.plugin.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.data.plugin.InstalledApkPluginLoader
import github.ponyhuang.gimi.data.plugin.PluginConfigStore
import github.ponyhuang.gimi.data.plugin.PluginLoader
import github.ponyhuang.gimi.data.plugin.PluginManager
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.domain.plugin.runtime.PluginRuntimeProvider
import github.ponyhuang.gimi.pluginapi.AgentPlugin
import javax.inject.Singleton

/** 提供动态插件加载器的进程级单例绑定。 */
@Module
@InstallIn(SingletonComponent::class)
object PluginModule {

    @Provides
    @Singleton
    fun providePluginLoader(
        @ApplicationContext context: Context,
        configStore: PluginConfigStore,
    ): PluginLoader = InstalledApkPluginLoader(context, configStore)
}

/** 把插件管理契约绑定到 [PluginManager]。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginRepositoryModule {
    @Binds
    @Singleton
    abstract fun bindPluginRepository(implementation: PluginManager): PluginRepository

    /** 为 Agent 运行时暴露同一插件管理单例的窄类型契约。 */
    @Binds
    @Singleton
    abstract fun bindPluginRuntimeProvider(
        implementation: PluginManager,
    ): PluginRuntimeProvider<AgentPlugin>
}
