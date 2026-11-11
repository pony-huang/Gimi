package github.ponyhuang.gimi.data.mobileuse

import dagger.Binds
import dagger.Module
import dagger.hilt.InstallIn
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.gimi.domain.mobileuse.DirectPhoneController

/** 直控手机后端绑定；实装后仅需替换 implementation 类型，agent 装配层不受影响。 */
@Module
@InstallIn(SingletonComponent::class)
abstract class DirectPhoneModule {
    @Binds
    abstract fun bindDirectPhoneController(implementation: PlaceholderDirectPhoneController): DirectPhoneController
}
