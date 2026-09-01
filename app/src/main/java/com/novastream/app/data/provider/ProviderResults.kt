package com.novastream.app.data.provider

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

fun StreamingProvider.foldResult(result: Result<*>): StreamingProvider.ProviderResult<*> =
    ProviderResults.fold(id, result)
