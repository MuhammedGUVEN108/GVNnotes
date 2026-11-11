package com.gvn.notes

import android.util.AtomicFile
import com.gvn.notes.core.*
import java.io.*
import java.util.WeakHashMap

/** Nota bağlı özgün PDF'nin uygulama içi kopyası. Hash içerik kimliğidir; not kimliği UUID kalır. */
data class NoteSource(val sha256:String,val displayName:String,val pageCount:Int)

data class PageSnapshot(val revision:Long,val folder:Folder,val note:Note,val pages:List<Page>,
    val strokes:List<RenderedStroke>,val source:NoteSource?=null) {
    init { require(pages.isNotEmpty()) }
    constructor(revision:Long,folder:Folder,note:Note,page:Page,strokes:List<RenderedStroke>):
        this(revision,folder,note,listOf(page),strokes)
    val page:Page get()=pages.first()
}
data class OpenedPage(val snapshot:PageSnapshot?,val recovered:Boolean=false)

/** Tek IO yazıcısı kullanır. İki atomik slot, son kayıtta sorun varsa önceki sağlam kaydı korur. */
class PageStore(private val directory:File) {
    private val slots=listOf("document-a.gvn","document-b.gvn").map { AtomicFile(File(directory,it)) }
    /** Değişmeyen çizgilerin kodlanmış hali yeniden üretilmez (çizgiler kayıttan sonra değiştirilmez). */
    private val encoded=WeakHashMap<PageStroke,ByteArray>()
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
            out.writeInt(SCHEMA) // 4: GVNpen çizgileri (Ink verisi yok); 3: şablon; 2: çok sayfa + PDF; 1–3 okunur.
            out.writeUTF(snapshot.folder.id); out.writeUTF(snapshot.folder.name); out.writeUTF(snapshot.folder.relativePath)
            out.writeUTF(snapshot.note.id); out.writeUTF(snapshot.note.title)
            require(snapshot.folder.parentId==null && snapshot.pages.size<=MAX_PAGES)
            require(snapshot.pages.map { it.id }.toSet().size==snapshot.pages.size)
            out.writeInt(snapshot.pages.size)
            snapshot.pages.forEach { p ->
                require(p.background!=BackgroundType.IMAGE)
                require(p.background==BackgroundType.BLANK || (snapshot.source!=null && p.sourcePageIndex!=null))
                out.writeUTF(p.id); out.writeInt(p.order); out.writeFloat(p.width); out.writeFloat(p.height)
                out.writeUTF(p.background.name); out.writeInt(p.sourcePageIndex ?: -1); out.writeInt(p.pattern)
            }
            out.writeBoolean(snapshot.source!=null)
            snapshot.source?.let { out.writeUTF(it.sha256); out.writeUTF(it.displayName); out.writeInt(it.pageCount) }
            val pageIds=snapshot.pages.map { it.id }.toSet()
            require(snapshot.strokes.size<=20000 && snapshot.strokes.all { it.data.pageId in pageIds })
            out.writeInt(snapshot.strokes.size)
            snapshot.strokes.forEach { stroke ->
                val bytes=encoded[stroke.data] ?: encode(stroke).also { encoded[stroke.data]=it }
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
            out.writeUTF(s.tool.name); out.writeInt(s.color); out.writeFloat(s.width)
            out.writeInt(s.samples.size)
            s.samples.forEach { p ->
                out.writeFloat(p.x); out.writeFloat(p.y); out.writeFloat(p.pressure)
                out.writeFloat(p.tiltRadians); out.writeFloat(p.orientationRadians); out.writeLong(p.elapsedMillis)
            }
        }
    }.toByteArray()
    private fun decode(envelope:SaveEnvelope):PageSnapshot = DataInputStream(ByteArrayInputStream(envelope.payload)).use { input ->
        val schema=input.readInt(); if(schema>SCHEMA) throw NewerSaveFormatException(); require(schema in 1..SCHEMA)
        val folder=Folder(id=input.readUTF(),parentId=null,name=input.readUTF(),relativePath=input.readUTF())
        val note=Note(id=input.readUTF(),folderId=folder.id,title=input.readUTF())
        var source:NoteSource?=null
        val pages=if(schema==1) {
            listOf(Page(id=input.readUTF(),noteId=note.id,order=input.readInt(),width=input.readFloat(),height=input.readFloat()))
        } else {
            val n=input.readInt(); require(n in 1..MAX_PAGES)
            val list=List(n) {
                val id=input.readUTF(); val order=input.readInt(); val w=input.readFloat(); val h=input.readFloat()
                val background=BackgroundType.valueOf(input.readUTF()); val index=input.readInt()
                val pattern=if(schema>=3) input.readInt().coerceIn(0,15) else 0
                require(background!=BackgroundType.IMAGE && (background==BackgroundType.BLANK)==(index<0))
                Page(id=id,noteId=note.id,order=order,width=w,height=h,background=background,pattern=pattern,
                    sourcePageIndex=index.takeIf { it>=0 })
            }
            if(input.readBoolean()) source=NoteSource(input.readUTF(),input.readUTF(),input.readInt()).also { s ->
                require(s.sha256.length==64 && s.pageCount in 1..MAX_PAGES)
            }
            list.map { if(it.background==BackgroundType.PDF) it.copy(sourceBlobHash=checkNotNull(source).sha256) else it }
        }
        require(pages.all { it.width.isFinite() && it.height.isFinite() && it.width>0 && it.height>0 })
        require(pages.map { it.id }.toSet().size==pages.size)
        pages.forEach { p -> p.sourcePageIndex?.let { require(it < checkNotNull(source).pageCount) } }
        val pageIds=pages.map { it.id }.toSet()
        val count=input.readInt(); require(count in 0..20000)
        var totalSamples=0L
        val strokes=List(count) {
            val bytes=input.blob()
            DataInputStream(ByteArrayInputStream(bytes)).use { s ->
                val id=s.readUTF(); val pageId=s.readUTF(); val version=s.readInt()
                if(version>1) throw NewerSaveFormatException()
                require(version==1 && pageId in pageIds)
                val tool=Tool.valueOf(s.readUTF()); require(tool==Tool.PEN || tool==Tool.HIGHLIGHTER)
                var color=s.readInt(); val width=s.readFloat()
                if(schema<4) s.readFloat() // Eski Ink fırça hassasiyeti; artık kullanılmıyor.
                require(width.isFinite() && width>0f && width<=400f)
                val n=s.readInt(); require(n in 1..1_000_000)
                totalSamples+=n; require(totalSamples<=2_000_000)
                var previous=-1L
                val samples=List(n) {
                    var sample=Sample(s.readFloat(),s.readFloat(),s.readFloat(),s.readFloat(),s.readFloat(),s.readLong())
                    // Biçim 1–3 ham basınç (0..1) saklar; GVNpen çizimi basınç katsayısı (0,15..1,5) kullanır.
                    if(schema<4) sample=sample.copy(pressure=(sample.pressure*1.4f).coerceIn(.15f,1.5f))
                    require(listOf(sample.x,sample.y,sample.pressure,sample.tiltRadians,sample.orientationRadians).all { it.isFinite() })
                    require(sample.elapsedMillis>=previous); previous=sample.elapsedMillis
                    sample
                }
                if(schema<4) { s.blob(); s.blob() } // Eski Ink fırça ailesi ve girdileri atlanır.
                require(s.available()==0)
                // Eski fosforlu renkler yarı saydam saklanırdı; GVNpen fosforluyu tam renkle %32 saydam çizer.
                if(schema<4 && tool==Tool.HIGHLIGHTER) color=color or 0xff000000.toInt()
                val data=PageStroke(id,pageId,version,tool,color,width,samples)
                RenderedStroke(data).also { if(schema>=4) encoded[data]=bytes }
            }
        }
        require(input.available()==0)
        PageSnapshot(envelope.revision,folder,note,pages,strokes,source)
    }
    companion object {
        const val SCHEMA=4
        const val MAX_PAGES=2000
        /** Not klasöründeki özgün PDF kopyası. */
        fun sourceFile(directory:File)=File(directory,"source.pdf")
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
