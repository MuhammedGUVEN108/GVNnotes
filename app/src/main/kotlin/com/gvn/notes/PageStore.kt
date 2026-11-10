package com.gvn.notes

import android.util.AtomicFile
import androidx.ink.brush.Brush
import androidx.ink.brush.BrushFamily
import androidx.ink.brush.ExperimentalInkCustomBrushApi
import androidx.ink.storage.decode
import androidx.ink.storage.encode
import androidx.ink.strokes.Stroke
import androidx.ink.strokes.StrokeInputBatch
import com.gvn.notes.core.*
import java.io.*
import java.util.WeakHashMap

data class PageSnapshot(val revision:Long,val folder:Folder,val note:Note,val page:Page,val strokes:List<RenderedStroke>)
data class OpenedPage(val snapshot:PageSnapshot?,val recovered:Boolean=false)

/** Tek IO yazıcısı kullanır. İki atomik slot, son kayıtta sorun varsa önceki sağlam kaydı korur. */
@OptIn(ExperimentalInkCustomBrushApi::class)
class PageStore(private val directory:File) {
    private val slots=listOf("document-a.gvn","document-b.gvn").map { AtomicFile(File(directory,it)) }
    private val encoded=WeakHashMap<Stroke,ByteArray>()
    fun open():OpenedPage {
        var hadFiles=false; var damaged=false
        val valid=slots.mapNotNull { slot ->
            val file=slot.baseFile
            if(!file.exists() && !File(file.path+".bak").exists() && !File(file.path+".new").exists()) return@mapNotNull null
            hadFiles=true
            try {
                val bytes=slot.openRead().use { readLimited(it,SaveEnvelopeCodec.MAX_BYTES+52) }
                val envelope=SaveEnvelopeCodec.decode(bytes)
                decode(envelope)
            } catch(e:NewerSaveFormatException) { throw e }
              catch(e:Exception) { damaged=true; null }
        }
        if(hadFiles && valid.isEmpty()) throw IOException("Sağlam kayıt bulunamadı; mevcut dosyalar korundu.")
        return OpenedPage(valid.maxByOrNull { it.revision },damaged)
    }
    fun save(snapshot:PageSnapshot) {
        check(directory.isDirectory || directory.mkdirs()) { "Kayıt klasörü oluşturulamadı." }
        val payload=ByteArrayOutputStream()
        DataOutputStream(payload).use { out ->
            out.writeInt(1) // İç belge şeması; yeni arka plan türleri ayrı sürüm gerektirir.
            out.writeUTF(snapshot.folder.id); out.writeUTF(snapshot.folder.name); out.writeUTF(snapshot.folder.relativePath)
            out.writeUTF(snapshot.note.id); out.writeUTF(snapshot.note.title)
            out.writeUTF(snapshot.page.id); out.writeInt(snapshot.page.order)
            out.writeFloat(snapshot.page.width); out.writeFloat(snapshot.page.height)
            require(snapshot.page.background==BackgroundType.BLANK && snapshot.folder.parentId==null)
            require(snapshot.strokes.size<=20000)
            out.writeInt(snapshot.strokes.size)
            snapshot.strokes.forEach { stroke ->
                val bytes=encoded[stroke.ink] ?: encode(stroke).also { encoded[stroke.ink]=it }
                out.blob(bytes)
                require(payload.size()<=SaveEnvelopeCodec.MAX_BYTES) { "Not kayıt sınırını aşıyor." }
            }
        }
        val bytes=SaveEnvelopeCodec.encode(snapshot.revision,payload.toByteArray())
        val slot=slots[(snapshot.revision%2).toInt()]
        var output:FileOutputStream?=null
        try {
            output=slot.startWrite(); output.write(bytes); output.flush(); output.fd.sync()
            slot.finishWrite(output)
            // AtomicFile bazı rename/sync hatalarını yalnız loglayabilir; arayüze başarı bildirmeden doğrula.
            check(slot.openRead().use { readLimited(it,SaveEnvelopeCodec.MAX_BYTES+52) }.contentEquals(bytes)) { "Kayıt geri okunamadı." }
        }
        catch(e:Exception) { output?.let { slot.failWrite(it) }; throw e }
    }
    private fun encode(stroke:RenderedStroke):ByteArray = ByteArrayOutputStream().also { bytes ->
        DataOutputStream(bytes).use { out ->
            val s=stroke.data
            require(s.formatVersion==1 && s.samples.size in 1..1_000_000)
            out.writeUTF(s.id); out.writeUTF(s.pageId); out.writeInt(s.formatVersion)
            out.writeUTF(s.tool.name); out.writeInt(s.color); out.writeFloat(s.width); out.writeFloat(stroke.ink.brush.epsilon)
            out.writeInt(s.samples.size)
            s.samples.forEach { p ->
                out.writeFloat(p.x); out.writeFloat(p.y); out.writeFloat(p.pressure)
                out.writeFloat(p.tiltRadians); out.writeFloat(p.orientationRadians); out.writeLong(p.elapsedMillis)
            }
            val family=ByteArrayOutputStream().also { stroke.ink.brush.family.encode(it) }.toByteArray()
            val inputs=ByteArrayOutputStream().also { stroke.ink.inputs.encode(it) }.toByteArray()
            out.blob(family); out.blob(inputs)
        }
    }.toByteArray()
    private fun decode(envelope:SaveEnvelope):PageSnapshot = DataInputStream(ByteArrayInputStream(envelope.payload)).use { input ->
        val schema=input.readInt(); if(schema>1) throw NewerSaveFormatException(); require(schema==1)
        val folder=Folder(id=input.readUTF(),parentId=null,name=input.readUTF(),relativePath=input.readUTF())
        val note=Note(id=input.readUTF(),folderId=folder.id,title=input.readUTF())
        val page=Page(id=input.readUTF(),noteId=note.id,order=input.readInt(),width=input.readFloat(),height=input.readFloat())
        require(page.width.isFinite() && page.height.isFinite() && page.width>0 && page.height>0)
        val count=input.readInt(); require(count in 0..20000)
        var totalSamples=0L
        val strokes=List(count) {
            val bytes=input.blob()
            DataInputStream(ByteArrayInputStream(bytes)).use { s ->
                val id=s.readUTF(); val pageId=s.readUTF(); val version=s.readInt()
                if(version>1) throw NewerSaveFormatException()
                require(version==1 && pageId==page.id)
                val tool=Tool.valueOf(s.readUTF()); require(tool!=Tool.ERASER)
                val color=s.readInt(); val width=s.readFloat(); val epsilon=s.readFloat()
                require(width.isFinite() && epsilon.isFinite() && epsilon>0 && width>=epsilon)
                val n=s.readInt(); require(n in 1..1_000_000)
                totalSamples+=n; require(totalSamples<=2_000_000)
                var previous=-1L
                val samples=List(n) {
                    val sample=Sample(s.readFloat(),s.readFloat(),s.readFloat(),s.readFloat(),s.readFloat(),s.readLong())
                    require(listOf(sample.x,sample.y,sample.pressure,sample.tiltRadians,sample.orientationRadians).all { it.isFinite() })
                    require(sample.elapsedMillis>=previous); previous=sample.elapsedMillis
                    sample
                }
                val family=BrushFamily.decode(ByteArrayInputStream(s.blob()))
                val inputs=StrokeInputBatch.decode(ByteArrayInputStream(s.blob()))
                require(s.available()==0)
                val ink=Stroke(Brush.createWithColorIntArgb(family,color,width,epsilon),inputs)
                RenderedStroke(PageStroke(id,pageId,version,tool,color,width,samples),ink).also { encoded[ink]=bytes }
            }
        }
        require(input.available()==0)
        PageSnapshot(envelope.revision,folder,note,page,strokes)
    }
    private fun DataOutputStream.blob(bytes:ByteArray) { require(bytes.size<=48*1024*1024); writeInt(bytes.size); write(bytes) }
    private fun DataInputStream.blob():ByteArray {
        val size=readInt(); require(size>=0 && size<=48*1024*1024 && size<=available())
        return ByteArray(size).also { readFully(it) }
    }
    private fun readLimited(input:InputStream,max:Int):ByteArray {
        val bytes=ByteArrayOutputStream(); val buffer=ByteArray(8192)
        while(true) { val n=input.read(buffer); if(n<0)break; require(bytes.size()+n<=max); bytes.write(buffer,0,n) }
        return bytes.toByteArray()
    }
}
