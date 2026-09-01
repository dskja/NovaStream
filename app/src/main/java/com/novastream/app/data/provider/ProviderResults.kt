package com.novastream.app.data.provider

import com.novastream.app.util.AppContext
import com.novastream.app.util.ErrorMapper
import com.novastream.app.util.ProviderHealthMonitor

/** Shared result folding with provider health telemetry. */
object ProviderResults {

    fun <T> fold(providerId: String, result: Result<T>): StreamingProvider.ProviderResult<T> =
        result.fold(
            onSuccess = {
                ProviderHealthMonitor.recordSuccess(providerId)
                StreamingProvider.ProviderResult.Success(it)
            },
            onFailure = {
                ProviderHealthMonitor.recordFailure(providerId)
                StreamingProvider.ProviderResult.Error(ErrorMapper.toUserMessage(it), it)
            }
        )
}

/** Run a suspending provider operation with health-tracked [Result] folding. */
suspend fun <T> StreamingProvider.runCatchingProvider(
    block: suspend () -> T
): StreamingProvider.ProviderResult<T> =
    ProviderResults.fold(id, runCatching { block() })

/** Standard localized error for blank search queries (no health penalty). */
fun StreamingProvider.emptySearchError(): StreamingProvider.ProviderResult<Nothing> =
    StreamingProvider.ProviderResult.Error(
        AppContext.get().getString(com.novastream.app.R.string.error_empty_search)
    )

/** Returns [emptySearchError] when [query] is blank, otherwise null. */
fun StreamingProvider.guardSearchQuery(query: String): StreamingProvider.ProviderResult<Nothing>? =
    if (query.trim().isBlank()) emptySearchError() else null
