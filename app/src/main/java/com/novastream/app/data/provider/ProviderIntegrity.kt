package com.novastream.app.data.provider

/**
 * Validates that every built-in provider is correctly wired for production use.
 */
object ProviderIntegrity {

    data class Issue(val providerId: String, val message: String)

    /** All built-in provider ids from [ProviderRegistry] (excludes dynamic imports). */
    fun builtInProviderIds(): List<String> =
        ProviderRegistry.providers.map { it.id }

    fun validateAll(): List<Issue> = builtInProviderIds().flatMap { validateProvider(it) }

    fun validateProvider(providerId: String): List<Issue> {
        val issues = mutableListOf<Issue>()
        val provider = ProviderRegistry.getProviderOrNull(providerId)
        if (provider == null) {
            issues += Issue(providerId, "not registered")
            return issues
        }
        if (provider.displayName.isBlank()) {
            issues += Issue(providerId, "blank displayName")
        }
        if (provider.baseUrl.isBlank()) {
            issues += Issue(providerId, "blank baseUrl")
        }
        if (ProviderCatalogHints.forId(providerId) == null && provider.catalogHint == null) {
            issues += Issue(providerId, "missing catalogHint")
        }
        if (ProviderGenres.forId(providerId).isEmpty() && provider.availableGenres.isEmpty()) {
            issues += Issue(providerId, "missing genres")
        }
        val detail = ProviderDetailUrls.resolve(providerId, "https://example.com", "test-slug")
        if (!detail.startsWith("https://example.com")) {
            issues += Issue(providerId, "invalid detail URL: $detail")
        }
        if (ProviderDomainManager.alternateDomains(providerId).isNotEmpty()) {
            val needle = ProviderMirrorNeedles.needleFor(providerId)
            if (needle.isBlank() && providerId !in mirrorOptional) {
                issues += Issue(providerId, "has mirrors but no content needle")
            }
        }
        return issues
    }

    /** Providers where empty mirror needle is acceptable (homepage probe only). */
    private val mirrorOptional = setOf("freecatalog", "freecatalogbrowse")
}
