package github.ponyhuang.gimi.data.voice.repository

import github.ponyhuang.gimi.domain.modelcatalog.model.Model
import github.ponyhuang.gimi.domain.modelcatalog.model.ModelSelection
import github.ponyhuang.gimi.domain.modelcatalog.model.ResolvedServiceConnection
import github.ponyhuang.gimi.domain.modelcatalog.repository.ModelCatalogRepository

/**
 * 按选择解析语音连接视图，并按用途校验模型能力。
 *
 * 启用状态、凭据与连接存在性由仓库的连接解析统一收敛；这里只补充语音用途
 * 特有的模型能力校验（识别模型可兼具 STT/ASR 语义，合成与实时按标记判断）。
 */
internal fun ModelCatalogRepository.resolveVoiceConnection(
    selection: ModelSelection?,
    modelFilter: (Model) -> Boolean,
): ResolvedServiceConnection? = resolveConnection(selection)?.takeIf { modelFilter(it.model) }
