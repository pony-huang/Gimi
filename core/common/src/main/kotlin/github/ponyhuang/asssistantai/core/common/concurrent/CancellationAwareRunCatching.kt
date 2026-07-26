package github.ponyhuang.asssistantai.core.common.concurrent

import java.util.concurrent.CancellationException

/**
 * Equivalent to [runCatching] for recoverable failures, while preserving structured cancellation.
 *
 * Logging and fallback selection stay at the owning feature/data boundary.
 */
inline fun <T> cancellationAwareRunCatching(block: () -> T): Result<T> =
    try {
        Result.success(block())
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (failure: Throwable) {
        Result.failure(failure)
    }
