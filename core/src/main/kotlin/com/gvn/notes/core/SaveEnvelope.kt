package com.gvn.notes.core

import java.io.*
import java.security.MessageDigest

class NewerSaveFormatException: IOException("Bu dosya daha yeni bir kayıt biçimi kullanıyor.")
data class SaveEnvelope(val revision: Long, val payload: ByteArray)

/** Sürümlü ve özet kontrollü zarf: yarım/bozuk dosya sağlam kayıt gibi açılmaz. */
object SaveEnvelopeCodec {
    private const val MAGIC=0x47564e53
    private const val VERSION=1
    const val MAX_BYTES=64*1024*1024
    fun encode(revision:Long,payload:ByteArray):ByteArray {
        require(revision>=0 && payload.size<=MAX_BYTES)
        val content=ByteArrayOutputStream().also { bytes ->
            DataOutputStream(bytes).use { out ->
                out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(revision)
                out.writeInt(payload.size); out.write(payload)
            }
        }.toByteArray()
        return content+MessageDigest.getInstance("SHA-256").digest(content)
    }
    fun decode(bytes:ByteArray):SaveEnvelope {
        require(bytes.size in 52..(MAX_BYTES+52)) { "Kayıt boyutu geçersiz." }
        val content=bytes.copyOfRange(0,bytes.size-32)
        require(MessageDigest.isEqual(MessageDigest.getInstance("SHA-256").digest(content),bytes.copyOfRange(bytes.size-32,bytes.size))) { "Kayıt bütünlüğü doğrulanamadı." }
        return DataInputStream(ByteArrayInputStream(content)).use { input ->
            require(input.readInt()==MAGIC) { "GVNnotes kaydı değil." }
            val version=input.readInt()
            if(version>VERSION) throw NewerSaveFormatException()
            require(version==VERSION) { "Kayıt sürümü geçersiz." }
            val revision=input.readLong(); require(revision>=0)
            val size=input.readInt(); require(size>=0 && size==input.available())
            SaveEnvelope(revision,ByteArray(size).also { input.readFully(it) })
        }
    }
}
