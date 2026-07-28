package github.ponyhuang.asssistantai.data.skills.di

import android.content.Context
import android.net.Uri
import com.google.adk.kt.skills.NewFileSystemSource
import com.google.adk.kt.skills.SkillSource
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import github.ponyhuang.asssistantai.data.skills.FileSkillRepository
import github.ponyhuang.asssistantai.data.skills.SkillArchiveReader
import github.ponyhuang.asssistantai.data.skills.SkillArchiveStore
import github.ponyhuang.asssistantai.domain.skills.repository.SkillRepository
import java.io.File
import javax.inject.Singleton
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import okhttp3.OkHttpClient
import androidx.core.net.toUri

@Module
@InstallIn(SingletonComponent::class)
object SkillsModule {
    @Provides
    @Singleton
    internal fun providePaths(
        @ApplicationContext context: Context,
    ): SkillStoragePaths = SkillStoragePaths(
        skillsRoot = File(context.filesDir, "skills"),
        stagingRoot = File(context.cacheDir, "skill-imports"),
    )

    @Provides
    @Singleton
    internal fun provideSkillRepository(
        @ApplicationContext context: Context,
        okHttpClient: OkHttpClient,
        paths: SkillStoragePaths,
    ): SkillRepository {
        val reader = SkillArchiveReader(
            okHttpClient = okHttpClient,
            localDocumentOpener = { value ->
                context.contentResolver.openInputStream(value.toUri())
            },
        )
        return FileSkillRepository(
            store = SkillArchiveStore(paths.skillsRoot, paths.stagingRoot),
            archiveReader = reader,
            scope = CoroutineScope(SupervisorJob() + Dispatchers.IO),
        )
    }

    @Provides
    @Singleton
    internal fun provideSkillSource(paths: SkillStoragePaths): SkillSource {
        paths.skillsRoot.mkdirs()
        return NewFileSystemSource(paths.skillsRoot.absolutePath)
    }
}

internal data class SkillStoragePaths(
    val skillsRoot: File,
    val stagingRoot: File,
)
