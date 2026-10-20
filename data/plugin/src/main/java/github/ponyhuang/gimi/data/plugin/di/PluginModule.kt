package github.ponyhuang.gimi.data.plugin.di

import android.content.Context
import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.core.storage.StorageRegistry
import github.ponyhuang.gimi.data.plugin.InstalledApkPluginLoader
import github.ponyhuang.gimi.data.plugin.PluginConfigStore
import github.ponyhuang.gimi.data.plugin.PluginLoadNoticeQueue
import github.ponyhuang.gimi.data.plugin.PluginLoader
import github.ponyhuang.gimi.data.plugin.PluginManager
import github.ponyhuang.gimi.data.plugin.catalog.SubscribedPluginCatalogRepository
import github.ponyhuang.gimi.data.plugin.install.AndroidPluginPackageInstaller
import github.ponyhuang.gimi.data.plugin.install.DefaultPluginInstallEnvironment
import github.ponyhuang.gimi.data.plugin.remote.GitHubPluginReleaseRepository
import github.ponyhuang.gimi.data.plugin.subscription.SharedPreferencesPluginSubscriptionStore
import github.ponyhuang.gimi.domain.plugin.repository.PluginCatalogRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginInstallEnvironment
import github.ponyhuang.gimi.domain.plugin.repository.PluginPackageInstaller
import github.ponyhuang.gimi.domain.plugin.repository.PluginReleaseRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginRepository
import github.ponyhuang.gimi.domain.plugin.repository.PluginSubscriptionStore
import github.ponyhuang.gimi.domain.plugin.runtime.PluginLoadNotices
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
        storageRegistry: StorageRegistry,
        notices: PluginLoadNoticeQueue,
    ): PluginLoader = InstalledApkPluginLoader(context, configStore, storageRegistry, notices)
}

/** 把插件管理契约绑定到 [PluginManager]。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginRepositoryModule {
    @Binds
    abstract fun bindPluginLoadNotices(implementation: PluginLoadNoticeQueue): PluginLoadNotices

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

/** 插件订阅目录、发布信息与安装链路绑定。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class PluginCatalogModule {

    @Binds
    @Singleton
    abstract fun bindPluginCatalogRepository(
        implementation: SubscribedPluginCatalogRepository,
    ): PluginCatalogRepository

    @Binds
    @Singleton
    abstract fun bindPluginSubscriptionStore(
        implementation: SharedPreferencesPluginSubscriptionStore,
    ): PluginSubscriptionStore

    @Binds
    @Singleton
    abstract fun bindPluginReleaseRepository(
        implementation: GitHubPluginReleaseRepository,
    ): PluginReleaseRepository

    @Binds
    @Singleton
    abstract fun bindPluginPackageInstaller(
        implementation: AndroidPluginPackageInstaller,
    ): PluginPackageInstaller

    @Binds
    @Singleton
    abstract fun bindPluginInstallEnvironment(
        implementation: DefaultPluginInstallEnvironment,
    ): PluginInstallEnvironment
}
