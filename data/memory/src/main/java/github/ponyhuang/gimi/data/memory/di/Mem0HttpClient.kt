package github.ponyhuang.gimi.data.memory.di

import javax.inject.Qualifier

/** 带短超时的 Mem0 专用 HTTP 客户端，避免共享长超时客户端阻塞对话关键路径。 */
@Qualifier
@Retention(AnnotationRetention.BINARY)
annotation class Mem0HttpClient
