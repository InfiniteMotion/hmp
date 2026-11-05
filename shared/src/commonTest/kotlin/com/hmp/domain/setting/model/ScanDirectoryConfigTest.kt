package com.hmp.domain.setting.model

import kotlin.test.Test
import kotlin.test.assertEquals
import kotlin.test.assertTrue

class ScanDirectoryConfigTest {

    @Test
    fun defaultValues() {
        val config = ScanDirectoryConfig()
        assertTrue(config.scanDirectories.isEmpty())
        assertTrue(config.blockedDirectories.isEmpty())
    }

    @Test
    fun customValues() {
        val config = ScanDirectoryConfig(
            scanDirectories = listOf("/music", "/downloads"),
            blockedDirectories = listOf("/temp", "/cache"),
        )
        assertEquals(2, config.scanDirectories.size)
        assertEquals(2, config.blockedDirectories.size)
        assertEquals("/music", config.scanDirectories[0])
        assertEquals("/temp", config.blockedDirectories[0])
    }
}
