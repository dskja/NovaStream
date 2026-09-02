package com.novastream.app.util

import android.content.Context
import androidx.test.core.app.ApplicationProvider
import com.novastream.app.data.provider.ContentLanguage
import org.junit.Assert.assertEquals
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner

@RunWith(RobolectricTestRunner::class)
class PrefsCacheTest {

    private val context: Context = ApplicationProvider.getApplicationContext()

    @Test
    fun uiLocale_roundTrips() {
        PrefsCache.setUiLocale(context, "en")
        assertEquals("en", PrefsCache.uiLocale(context))
    }

    @Test
    fun contentLanguage_roundTrips() {
        PrefsCache.setContentLanguage(context, ContentLanguage.FR.tag)
        assertEquals(ContentLanguage.FR.tag, PrefsCache.contentLanguage(context))
    }
}
