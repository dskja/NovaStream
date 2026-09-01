package com.novastream.app.data.provider

/**
 * Content/catalog language tag for provider grouping and global search scoping.
 */
enum class ContentLanguage(val tag: String) {
    DE("de"),
    EN("en"),
    FR("fr"),
    ES("es"),
    IT("it"),
    PL("pl"),
    MULTI("multi");

    companion object {
        fun fromTag(tag: String?): ContentLanguage =
            entries.find { it.tag.equals(tag, ignoreCase = true) } ?: MULTI
    }
}

data class ProviderSupport(
    val movies: Boolean,
    val series: Boolean,
    val capabilities: ProviderCapabilities = ProviderCapabilities()
)

data class RegisteredProvider(
    val provider: StreamingProvider,
    val support: ProviderSupport,
    val contentLanguage: ContentLanguage,
    val regionLabel: String? = null,
    val logoUrl: String? = null
)
