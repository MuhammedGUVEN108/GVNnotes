package com.gvn.notes

import android.util.AtomicFile
import com.gvn.notes.core.*
import java.io.*

/** Nota bağlı özgün PDF'nin uygulama içi kopyası. Hash içerik kimliğidir; not kimliği UUID kalır. */
data class NoteSource(val sha256:String,val displayName:String,val pageCount:Int)

data class PageSnapshot(val revision:Long,val folder:Folder,val note:Note,val pages:List<Page>,
    val strokes:List<RenderedStroke>,val source:NoteSource?=null,
    val pageInk:Map<String,List<RenderedStroke>>?=null) {
    init { require(pages.isNotEmpty()) }
    constructor(revision:Long,folder:Folder,note:Note,page:Page,strokes:List<RenderedStroke>):
        this(revision,folder,note,listOf(page),strokes)
    internal fun groupedInk():Map<String,List<RenderedStroke>> =
        if(pageInk!=null && (strokes as? FlattenedInk)?.groupToken===pageInk) pageInk else strokes.groupBy { it.data.pageId }
    val page:Page get()=pages.first()
}
data class OpenedPage(val snapshot:PageSnapshot?,val recovered:Boolean=false)

/** Küçük metadata + değişen sayfa/çizgi transaction'ları; noktalar gerektiğinde yüklenir. */
class PageStore(private val directory:File) {
    private val database=PageInkDatabase(databaseFile(directory))
    val lastWrittenPoints get()=database.lastWrittenPoints
    val lastWrittenPages get()=database.lastWrittenPages
    fun open():OpenedPage {
        val db=databaseFile(directory)
        if(db.isFile) database.open()?.let { return OpenedPage(it) }
        val legacy=listOf("document-a.gvn","document-b.gvn").any { File(directory,it).exists() }
        if(!legacy) return OpenedPage(null)
        val old=LegacyPageStore(directory).open()
        old.snapshot?.let { save(it) }
        return OpenedPage(database.open(),old.recovered)
    }
    fun save(snapshot:PageSnapshot) {
        database.save(snapshot)
        // Eski APK yeni sayfa bazlı kaydı eski snapshot sanıp açmasın.
        if(!File(directory,"paged-format.txt").exists()) {
            val marker=SaveEnvelopeCodec.encode(snapshot.revision,java.nio.ByteBuffer.allocate(4).putInt(SCHEMA).array())
            for(name in listOf("document-a.gvn","document-b.gvn")) {
                val atomic=AtomicFile(File(directory,name)); val out=atomic.startWrite()
                try { out.write(marker); atomic.finishWrite(out) } catch(e:Exception) { atomic.failWrite(out); throw e }
            }
            File(directory,"paged-format.txt").writeText(SCHEMA.toString())
        }
        runCatching { File(directory,"library-revision.txt").writeText(snapshot.revision.toString()) }
    }
    companion object {
        const val SCHEMA=6
        const val MAX_PAGES=2000
        fun databaseFile(directory:File)=File(directory,"notebook.db")
        fun sourceFile(directory:File)=File(directory,"source.pdf")
    }
}
