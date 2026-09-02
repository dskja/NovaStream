# Add project specific ProGuard rules here.

# ─── Models ────────────────────────────────────────────────────────────
-keep class com.novastream.app.data.model.** { *; }
-keep class com.novastream.app.data.db.** { *; }

# ─── Jsoup ─────────────────────────────────────────────────────────────
-keep class org.jsoup.** { *; }
-dontwarn org.jsoup.**

# ─── Retrofit / OkHttp ─────────────────────────────────────────────────
-dontwarn okhttp3.**
-dontwarn okio.**
-dontwarn retrofit2.**
-keep class retrofit2.** { *; }
-keepattributes Signature
-keepattributes Exceptions
-keepclasseswithmembers class * {
    @retrofit2.http.* <methods>;
}

# ─── Room ──────────────────────────────────────────────────────────────
-keep class * extends androidx.room.RoomDatabase { *; }
-dontwarn androidx.room.paging.**

# ─── Coil ──────────────────────────────────────────────────────────────
-dontwarn coil.**

# ─── Media3 / ExoPlayer ────────────────────────────────────────────────
-dontwarn androidx.media3.**

# ─── Google Cast / MediaRouter ─────────────────────────────────────────
-keep class com.google.android.gms.cast.** { *; }
-keep class com.google.android.gms.cast.framework.** { *; }
-dontwarn com.google.android.gms.cast.**
-keep class androidx.mediarouter.** { *; }
-dontwarn androidx.mediarouter.**

# ─── Compose ───────────────────────────────────────────────────────────
-dontwarn androidx.compose.**

# ─── Gson ──────────────────────────────────────────────────────────────
-keep class com.google.gson.** { *; }
-keepattributes *Annotation*
-keepattributes EnclosingMethod

# ─── DataStore ─────────────────────────────────────────────────────────
-dontwarn androidx.datastore.**

# ─── Keep BuildConfig ──────────────────────────────────────────────────
-keep class com.novastream.app.BuildConfig { *; }

# ─── WebView JavaScript Interface ──────────────────────────────────────
-keepclassmembers class * {
    @android.webkit.JavascriptInterface <methods>;
}
-keep class com.novastream.app.util.VoeWebViewResolver { *; }
-keepclassmembers class com.novastream.app.util.VoeWebViewResolver$* { *; }

# ─── Kotlin Coroutines ─────────────────────────────────────────────────
-keepnames class kotlinx.coroutines.internal.MainDispatcherFactory {}
-keepnames class kotlinx.coroutines.CoroutineExceptionHandler {}
-dontwarn kotlinx.coroutines.**

# ─── Kotlin Metadata ───────────────────────────────────────────────────
-keepattributes KotlinMetadata
-keep class kotlin.Metadata { *; }

# ─── Kotlin Serializable ───────────────────────────────────────────────
-keepclassmembers class kotlinx.serialization.** {
    *** Companion;
}
-keepclasseswithmembers class kotlinx.serialization.** {
    kotlinx.serialization.KSerializer serializer(...);
}

# ─── DataStore Preferences ─────────────────────────────────────────────
-keep class androidx.datastore.preferences.** { *; }

# ─── Lifecycle / ViewModel ─────────────────────────────────────────────
-keep class androidx.lifecycle.** { *; }
-dontwarn androidx.lifecycle.**

# ─── Splash Screen ─────────────────────────────────────────────────────
-keep class androidx.core.splashscreen.** { *; }
-dontwarn androidx.core.splashscreen.**
