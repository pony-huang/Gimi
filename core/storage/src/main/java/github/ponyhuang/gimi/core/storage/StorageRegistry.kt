package github.ponyhuang.gimi.core.storage

import java.io.File
import javax.inject.Inject
import javax.inject.Singleton

/** 汇总各 capability 目录声明并在进程启动时验证全局冲突。 */
@Singleton
class StorageRegistry @Inject constructor(
    contributedSpecs: Set<@JvmSuppressWildcards ManagedDirectorySpec>,
    private val resolver: AppDirectoryResolver,
) {
    val specs: List<ManagedDirectorySpec> = contributedSpecs.sortedBy(ManagedDirectorySpec::id)

    private val specsById = specs.associateBy(ManagedDirectorySpec::id).also { indexed ->
        require(indexed.size == specs.size) {
            "Managed directory ids must be unique: ${duplicatesBy(ManagedDirectorySpec::id)}"
        }
    }

    init {
        val byPath = specs.groupBy { spec -> resolver.resolve(spec).canonicalPath }
        require(byPath.values.none { it.size > 1 }) {
            "Managed directory locations must be unique: ${byPath.filterValues { it.size > 1 }.keys}"
        }
    }

    fun requireSpec(id: String): ManagedDirectorySpec =
        requireNotNull(specsById[id]) { "Unknown managed directory id: $id" }

    fun resolve(id: String, create: Boolean = false): File =
        resolver.resolve(requireSpec(id), create)

    private fun duplicatesBy(selector: (ManagedDirectorySpec) -> String): Set<String> = specs
        .groupingBy(selector)
        .eachCount()
        .filterValues { it > 1 }
        .keys
}
