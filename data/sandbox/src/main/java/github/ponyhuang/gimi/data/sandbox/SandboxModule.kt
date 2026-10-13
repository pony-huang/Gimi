package github.ponyhuang.gimi.data.sandbox

import dagger.Binds
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.sandbox.SandboxExecutionRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxSettingsRepository
import github.ponyhuang.gimi.domain.sandbox.SandboxTemplateRepository
import javax.inject.Qualifier
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob

/** 沙盒配置监控与阻塞 SDK 桥接使用的应用生命周期 IO scope。 */
@Qualifier
@Retention(AnnotationRetention.RUNTIME)
annotation class SandboxRuntimeScope

@Module
@InstallIn(SingletonComponent::class)
abstract class SandboxModule {
    @Binds abstract fun storage(implementation: KeystoreSandboxSettingsStorage): SandboxSettingsStorage
    @Binds abstract fun settings(implementation: SecureSandboxSettingsRepository): SandboxSettingsRepository
    @Binds abstract fun execution(implementation: SessionSandboxExecutionRepository): SandboxExecutionRepository
    @Binds abstract fun gatewayFactory(implementation: SdkSandboxGatewayFactory): SandboxGatewayFactory
    @Binds abstract fun templates(implementation: SdkSandboxTemplateRepository): SandboxTemplateRepository

    companion object {
        @Provides @Singleton @SandboxRuntimeScope
        fun scope(): CoroutineScope = CoroutineScope(SupervisorJob() + Dispatchers.IO)
    }
}
