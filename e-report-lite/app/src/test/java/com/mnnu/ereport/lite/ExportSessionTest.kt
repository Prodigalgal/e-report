package com.mnnu.ereport.lite

import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import java.security.MessageDigest

class ExportSessionTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun hash(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it) }

    @Test fun exportsOnlyCompleteOrderedAndVerifiedBytes() {
        val bytes = "现场附件".toByteArray()
        ExportSession(temporary.newFolder()).use { session ->
            val id = session.begin("../../现场.txt", "text/plain", bytes.size.toLong(), hash(bytes))
            assertThrows(IllegalArgumentException::class.java) { session.append(id, 1, bytes) }
            session.append(id, 0, bytes)
            assertThrows(IllegalArgumentException::class.java) { session.append(id, bytes.size.toLong(), bytes) }
            val completed = session.complete(id)
            assertEquals("现场.txt", completed.name)
            assertArrayEquals(bytes, completed.file.readBytes())
        }
    }
    @Test fun deletesHashMismatchAndInterruptedTransfers() {
        val directory = temporary.newFolder()
        ExportSession(directory).use { session ->
            val id = session.begin("file", "text/plain", 1, "0".repeat(64))
            session.append(id, 0, byteArrayOf(1))
            assertThrows(IllegalArgumentException::class.java) { session.complete(id) }
            assertTrue(directory.listFiles()!!.isEmpty())
            session.begin("other", "text/plain", 1, "0".repeat(64))
        }
        assertTrue(directory.listFiles()!!.isEmpty())
    }
}
