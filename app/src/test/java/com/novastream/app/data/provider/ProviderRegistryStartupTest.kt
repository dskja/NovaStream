package com.novastream.app.data.provider

import android.app.Application
import androidx.test.core.app.ApplicationProvider
import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Before
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config

@RunWith(RobolectricTestRunner::class)
@Config(sdk = [28])
class ProviderRegistryStartupTest {

    @Before
    fun resetRegistry() {
        ProviderRegistry.bindContext(ApplicationProvider.getApplicationContext<Application>())
    }

    @Test
    fun bindContext_doesNotBuildProviders() {
        assertFalse(ProviderRegistry.isBuilt())
        assertEquals(ProviderRegistry.DEFAULT_PROVIDER_ID, ProviderManager.defaultProviderId)
    }

    @Test
    fun ensureBuilt_loadsProvidersOnce() {
        ProviderRegistry.ensureBuilt()
        assertTrue(ProviderRegistry.isBuilt())
        val count = ProviderRegistry.providers.size
        ProviderRegistry.ensureBuilt()
        assertEquals(count, ProviderRegistry.providers.size)
        assertTrue(ProviderRegistry.providers.any { it.id == ProviderRegistry.DEFAULT_PROVIDER_ID })
    }
}
