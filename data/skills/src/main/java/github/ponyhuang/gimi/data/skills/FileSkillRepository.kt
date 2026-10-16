package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.NonCancellable
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

internal class FileSkillRepository(
    private val store: SkillArchiveStore,
    private val archiveReader: SkillArchiveReader,
    scope: CoroutineScope,
    private val workerDispatcher: CoroutineDispatcher = Dispatchers.IO,
) : SkillRepository {
    private val installed = MutableStateFlow<List<InstalledSkill>>(emptyList())

    init {
        scope.launch { refresh() }
    }

    override fun observeInstalled(): Flow<List<InstalledSkill>> = installed

    override suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport {
        var prepared: PreparedSkillImport? = null
        try {
            return withContext(workerDispatcher) {
                archiveReader.open(source).use { store.prepare(it) }.also { prepared = it }
            }
        } catch (cancelled: CancellationException) {
            // IO 调度器完成后回送结果时也可能被取消，此时调用者拿不到票据，必须在仓库回收。
            prepared?.let { pending ->
                withContext(NonCancellable + workerDispatcher) { store.discard(pending.id) }
            }
            throw cancelled
        }
    }

    override suspend fun commitImport(preparedId: String, allowReplace: Boolean) {
        withContext(workerDispatcher) {
            store.commit(preparedId, allowReplace)
            refresh()
        }
    }

    override suspend fun discardImport(preparedId: String) {
        withContext(workerDispatcher) {
            store.discard(preparedId)
        }
    }

    override suspend fun remove(name: String) {
        withContext(workerDispatcher) {
            store.remove(name)
            refresh()
        }
    }

    private suspend fun refresh() {
        withContext(workerDispatcher) {
            installed.value = store.listInstalled()
        }
    }
}
