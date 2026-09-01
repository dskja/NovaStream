package com.novastream.app

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.novastream.app.extractor.ExtractorEngine
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class NovaStreamSmokeTest {

    @Test
    fun appContextLoads() {
        val context = InstrumentationRegistry.getInstrumentation().targetContext
        assertTrue(context.packageName.contains("novastream"))
    }

    @Test
    fun extractorEngineRegistered() {
        assertTrue(ExtractorEngine.registeredCount() >= 40)
    }
}
