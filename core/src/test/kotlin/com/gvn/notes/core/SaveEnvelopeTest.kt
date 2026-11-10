package com.gvn.notes.core
import org.junit.Assert.*
import org.junit.Test
import java.security.MessageDigest

class SaveEnvelopeTest {
    @Test fun payloadAndRevisionRoundTrip() {
        val original=byteArrayOf(0,1,-1,42)
        val loaded=SaveEnvelopeCodec.decode(SaveEnvelopeCodec.encode(18,original))
        assertEquals(18L,loaded.revision); assertArrayEquals(original,loaded.payload)
    }
    @Test fun corruptionAndTruncationAreRejected() {
        val bytes=SaveEnvelopeCodec.encode(1,ByteArray(20) { it.toByte() })
        val damaged=bytes.copyOf().apply { this[25]=(this[25].toInt() xor 1).toByte() }
        listOf(damaged,bytes.copyOf(bytes.size-1),bytes+byteArrayOf(0)).forEach {
            assertTrue(runCatching { SaveEnvelopeCodec.decode(it) }.isFailure)
        }
    }
    @Test fun newerValidFormatIsNotMistakenForRecoverableCorruption() {
        val bytes=SaveEnvelopeCodec.encode(1,byteArrayOf(9))
        val content=bytes.copyOf(bytes.size-32).apply { this[7]=2 }
        val newer=content+MessageDigest.getInstance("SHA-256").digest(content)
        assertTrue(runCatching { SaveEnvelopeCodec.decode(newer) }.exceptionOrNull() is NewerSaveFormatException)
    }
    @Test fun restoredHistoryDoesNotExposePreviousProcessUndo() {
        val h=History<Int>(); h.replace(listOf(1)); h.restore(listOf(4,5))
        assertFalse(h.canUndo); assertFalse(h.canRedo)
        h.replace(listOf(4)); h.undo(); assertEquals(listOf(4,5),h.items)
    }
}
