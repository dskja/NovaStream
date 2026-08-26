package com.novastream.app.data.provider

/**
 * Hält den aktuell aktiven Provider als Singleton.
 * Wird von SettingsScreen aktualisiert wenn der User den Provider wechselt.
 * ViewModels lesen den Provider von hier.
 */
object ActiveProvider {
    @Volatile
    private var current: StreamingProvider = ProviderManager.defaultProvider

    fun get(): StreamingProvider = current

    fun set(provider: StreamingProvider) {
        current = provider
    }

    fun setById(id: String) {
        current = ProviderManager.getProvider(id)
    }

    /** Aktuelle Provider-ID. */
    val id: String get() = current.id

    /** Aktueller Provider-Display-Name. */
    val displayName: String get() = current.displayName
}
