package github.ponyhuang.gimi.data.skills

import github.ponyhuang.gimi.domain.skills.model.InstalledSkill
import github.ponyhuang.gimi.domain.skills.model.PreparedSkillImport
import github.ponyhuang.gimi.domain.skills.model.SkillImportSource
import github.ponyhuang.gimi.domain.skills.repository.SkillRepository
import kotlinx.coroutines.CoroutineDispatcher
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
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

    override suspend fun prepareImport(source: SkillImportSource): PreparedSkillImport =
        withContext(workerDispatcher) {
            archiveReader.open(source).use { store.prepare(it) }
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
