package com.novastream.app.util

import org.junit.Assert.assertEquals
import org.junit.Test

class SetupPermissionsTest {

    @Test
    fun networkPermissionStatus_isAlwaysGranted() {
        assertEquals(SetupPermissionStatus.GRANTED, networkPermissionStatus())
    }
}
