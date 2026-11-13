package com.gvn.notes.core

import java.io.*
import java.security.MessageDigest
import java.security.DigestInputStream
import java.security.DigestOutputStream

class NewerSaveFormatException: IOException("Bu dosya daha yeni bir kayıt biçimi kullanıyor.")
data class SaveEnvelope(val revision: Long, val payload: ByteArray)

/** Sürümlü ve özet kontrollü zarf: yarım/bozuk dosya sağlam kayıt gibi açılmaz. */
object SaveEnvelopeCodec {
    private const val MAGIC=0x47564e53
    private const val VERSION=1
    const val MAX_BYTES=64*1024*1024
    fun encode(revision:Long,payload:ByteArray):ByteArray {
        return ByteArrayOutputStream(payload.size+52).also {
            write(revision,ByteArrayInputStream(payload),payload.size,it)
        }.toByteArray()
    }
    /** Aynı zarfı büyük ara byte dizileri oluşturmadan yazar; çıktı akışını kapatmaz. */
    fun write(revision:Long,payload:InputStream,size:Int,output:OutputStream):ByteArray {
        require(revision>=0 && size in 0..MAX_BYTES)
        val digest=MessageDigest.getInstance("SHA-256")
        val checked=DigestOutputStream(output,digest); val out=DataOutputStream(checked)
        out.writeInt(MAGIC); out.writeInt(VERSION); out.writeLong(revision); out.writeInt(size)
        val buffer=ByteArray(64*1024); var remaining=size
        while(remaining>0) { val n=payload.read(buffer,0,minOf(buffer.size,remaining)); require(n>0); out.write(buffer,0,n); remaining-=n }
        require(payload.read()==-1); out.flush()
        val hash=digest.digest(); output.write(hash); return hash
    }
    fun decode(bytes:ByteArray):SaveEnvelope=read(ByteArrayInputStream(bytes))
    fun read(source:InputStream):SaveEnvelope=readChecked(source,true).first!!
    /** Dosyayı RAM'e almadan SHA256 ve biçim doğrulaması yapar; revizyon ve özeti döndürür. */
    fun verify(source:InputStream):Pair<Long,ByteArray> { val result=readChecked(source,false); return result.second }
    private fun readChecked(source:InputStream,retain:Boolean):Pair<SaveEnvelope?,Pair<Long,ByteArray>> {
            val digest=MessageDigest.getInstance("SHA-256")
            val checked=DigestInputStream(source,digest); val input=DataInputStream(checked)
            require(input.readInt()==MAGIC) { "GVNnotes kaydı değil." }
            val version=input.readInt()
            val revision=input.readLong(); require(revision>=0)
            val size=input.readInt(); require(size in 0..MAX_BYTES)
            val payload=if(retain) ByteArray(size).also { input.readFully(it) } else null
            if(!retain) { val buffer=ByteArray(64*1024); var remaining=size
                while(remaining>0) { val n=input.read(buffer,0,minOf(buffer.size,remaining)); require(n>0); remaining-=n } }
            val hash=digest.digest(); checked.on(false)
            val stored=ByteArray(32).also { input.readFully(it) }
            require(input.read()==-1 && MessageDigest.isEqual(hash,stored)) { "Kayıt bütünlüğü doğrulanamadı." }
            if(version>VERSION) throw NewerSaveFormatException()
            require(version==VERSION) { "Kayıt sürümü geçersiz." }
            return (payload?.let { SaveEnvelope(revision,it) }) to (revision to hash)
    }
}
