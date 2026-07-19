package com.ai.assistance.operit.data.backup

import java.util.concurrent.Callable
import java.util.concurrent.Executors
import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class MiraMemoryArchiveFilePolicyTest {
    @get:Rule
    val temporaryFolder = TemporaryFolder()

    @Test
    fun concurrentExportsAtTheSameMillisecondReserveDifferentFiles() {
        val directory = temporaryFolder.newFolder("memory")
        val executor = Executors.newFixedThreadPool(8)
        try {
            val files =
                executor
                    .invokeAll(
                        List(32) {
                            Callable {
                                MiraMemoryArchiveManager.reserveExportFile(
                                    directory = directory,
                                    nowMs = 1_721_234_567_890L,
                                )
                            }
                        },
                    )
                    .map { it.get() }

            assertEquals(files.size, files.map { it.canonicalPath }.distinct().size)
            assertTrue(files.all { it.isFile })
            assertTrue(
                files.all {
                    it.name.startsWith("memory_backup_mira_") && it.name.endsWith(".json")
                },
            )
        } finally {
            executor.shutdownNow()
        }
    }
}
