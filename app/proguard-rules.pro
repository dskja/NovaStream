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
