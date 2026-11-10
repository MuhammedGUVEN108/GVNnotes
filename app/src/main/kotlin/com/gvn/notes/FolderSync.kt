package com.gvn.notes

import android.content.Context
import android.net.Uri
import android.provider.DocumentsContract
import android.util.AtomicFile
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.InputStream

/** Kaynak klasördeki bir öge. [path] kök klasöre göre "/" ile ayrılmış klasör yoludur ("" = kök). */
data class SourceItem(val documentId:String,val path:String,val name:String,val isDirectory:Boolean,
    val size:Long,val modified:Long)

/** Tarama sonucu. [complete] false ise bazı klasörler okunamadı; o tarama hiçbir notu "kayıp" saymaz. */
data class SourceScan(val items:List<SourceItem>,val complete:Boolean)

interface SourceTree {
    fun scan():SourceScan
    fun open(documentId:String):InputStream
}

/** Sistem klasör erişimi (SAF): kullanıcının bir kez izin verdiği klasör ağacı. */
class SafSourceTree(private val context:Context,private val tree:Uri):SourceTree {
    override fun scan():SourceScan {
        val items=ArrayList<SourceItem>(); var complete=true
        val resolver=context.contentResolver
        fun walk(parentId:String,path:String,depth:Int) {
            if(depth>MAX_DEPTH) { complete=false; return }
            val children=DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId)
            val cursor=runCatching { resolver.query(children,arrayOf(
                DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME,
                DocumentsContract.Document.COLUMN_MIME_TYPE,DocumentsContract.Document.COLUMN_SIZE,
                DocumentsContract.Document.COLUMN_LAST_MODIFIED),null,null,null) }.getOrNull()
            if(cursor==null) { complete=false; return }
            val dirs=ArrayList<Pair<String,String>>()
            cursor.use { c ->
                while(c.moveToNext()) {
                    val id=c.getString(0) ?: continue; val name=c.getString(1) ?: continue
                    if(name.startsWith(".")) continue // Gizli/çöp klasörleri taranmaz.
                    val dir=c.getString(2)==DocumentsContract.Document.MIME_TYPE_DIR
                    val item=SourceItem(id,path,name,dir,if(c.isNull(3)) -1 else c.getLong(3),if(c.isNull(4)) 0 else c.getLong(4))
                    if(dir) { items+=item; dirs+=id to (if(path.isEmpty()) name else "$path/$name") }
                    else if(isPdf(name,c.getString(2))) items+=item
                }
            }
            dirs.forEach { (id,p) -> walk(id,p,depth+1) }
        }
        val rootId=runCatching { DocumentsContract.getTreeDocumentId(tree) }.getOrNull() ?: return SourceScan(emptyList(),false)
        walk(rootId,"",0)
        return SourceScan(items,complete)
    }
    override fun open(documentId:String):InputStream =
        checkNotNull(context.contentResolver.openInputStream(DocumentsContract.buildDocumentUriUsingTree(tree,documentId))) { "PDF okunamadı." }
}

/** Testler için düz dosya sistemi kaynağı. Belge kimliği göreli yoldur (SAF yerel sağlayıcısındaki gibi taşınınca değişir). */
class FileSourceTree(private val root:File):SourceTree {
    override fun scan():SourceScan {
        if(!root.isDirectory) return SourceScan(emptyList(),false)
        val items=ArrayList<SourceItem>()
        // Gizli klasörlere (ör. .GVNnotes-çöp) hiç girilmez; içindekiler de taranmaz.
        root.walkTopDown().onEnter { it==root || !it.name.startsWith(".") }.filter { it!=root && !it.name.startsWith(".") }.forEach { f ->
            val rel=f.relativeTo(root).invariantSeparatorsPath
            val parent=rel.substringBeforeLast('/',"")
            if(f.isDirectory) items+=SourceItem(rel,parent,f.name,true,-1,f.lastModified())
            else if(isPdf(f.name,null)) items+=SourceItem(rel,parent,f.name,false,f.length(),f.lastModified())
        }
        return SourceScan(items,true)
    }
    override fun open(documentId:String):InputStream=File(root,documentId).inputStream()
}

private const val MAX_DEPTH=12
private fun isPdf(name:String,mime:String?)=mime=="application/pdf" || name.endsWith(".pdf",ignoreCase=true)

enum class SourceState { AVAILABLE, MISSING, REPLACED }

/** Kaynak dosya ↔ not eşlemesi. Not kimliği UUID kalır; hash yalnız içerik kimliğidir. */
data class SyncEntry(val documentId:String?,val key:String,val sha256:String,val size:Long,val modified:Long,
    val path:String,val name:String,val state:SourceState)

/** Eşitleme dizini: notebooks kökünün yanında, atomik yazılır. */
class SyncIndex(private val file:File) {
    var entries:List<SyncEntry> = emptyList(); private set
    var folders:List<String> = emptyList(); private set
    var lastSync:Long=0; private set
    fun load():SyncIndex {
        val atomic=AtomicFile(file)
        if(!file.exists() && !File(file.path+".new").exists()) return this
        val json=JSONObject(String(atomic.readFully(),Charsets.UTF_8))
        require(json.getInt("version")==1)
        val list=json.getJSONArray("entries")
        entries=List(list.length()) { i -> list.getJSONObject(i).let { o ->
            SyncEntry(o.optString("documentId").ifEmpty { null },o.getString("key"),o.getString("sha256"),o.getLong("size"),
                o.getLong("modified"),o.getString("path"),o.getString("name"),SourceState.valueOf(o.getString("state")))
        } }
        val dirs=json.optJSONArray("folders") ?: JSONArray()
        folders=List(dirs.length()) { dirs.getString(it) }
        lastSync=json.optLong("lastSync")
        return this
    }
    fun save(entries:List<SyncEntry>,folders:List<String>,time:Long) {
        val json=JSONObject().put("version",1).put("lastSync",time)
            .put("folders",JSONArray(folders))
            .put("entries",JSONArray(entries.map { e -> JSONObject().put("documentId",e.documentId ?: "").put("key",e.key)
                .put("sha256",e.sha256).put("size",e.size).put("modified",e.modified).put("path",e.path).put("name",e.name)
                .put("state",e.state.name) }))
        file.parentFile?.mkdirs()
        val atomic=AtomicFile(file); var out:FileOutputStream?=null
        try { out=atomic.startWrite(); out.write(json.toString().toByteArray()); out.flush(); out.fd.sync(); atomic.finishWrite(out) }
        catch(e:Exception) { out?.let { atomic.failWrite(it) }; throw e }
        this.entries=entries; this.folders=folders; lastSync=time
    }
}

data class SyncReport(val imported:Int=0,val moved:Int=0,val missing:Int=0,val replaced:Int=0,val failed:Int=0,val complete:Boolean=true) {
    val summary get()=buildList {
        if(imported>0) add("$imported yeni PDF not oldu")
        if(moved>0) add("$moved not taşındı")
        if(replaced>0) add("$replaced PDF değişti, yeni sürüm ayrı not")
        if(missing>0) add("$missing kaynak bulunamadı")
        if(failed>0) add("$failed PDF açılamadı")
        if(!complete) add("bazı klasörler okunamadı")
    }.joinToString(" · ").ifEmpty { "Klasör güncel" }
}

/**
 * Tek yönlü eşitleme: kaynak klasör → notlar. Kurallar (docs/TASARIM.md):
 * - Yeni PDF yeni not olur; klasör yolu korunur.
 * - Aynı belge yeniden görülürse tekrar oluşturulmaz. Boyut/zaman yalnız ipucudur; değişince yeniden hash alınır.
 * - İçerik değişirse eski not ve yazıları korunur, yeni sürüm ayrı not olur.
 * - Kaybolan kaynakla aynı içerikte tek yeni belge varsa taşıma sayılır; not ve yazılar korunur, klasör güncellenir.
 * - Yalnız tamamlanmış taramada yokluk "kayıp" sayılır; not asla silinmez.
 */
class FolderSync(private val library:NoteLibrary,private val index:SyncIndex,private val tree:SourceTree) {
    fun run():SyncReport {
        val scan=tree.scan()
        val files=scan.items.filter { !it.isDirectory }
        val folders=scan.items.filter { it.isDirectory }.map { if(it.path.isEmpty()) it.name else "${it.path}/${it.name}" }.sorted()
        val old=index.entries
        val byDoc=old.filter { it.documentId!=null && it.state!=SourceState.REPLACED }.associateBy { it.documentId!! }
        val seen=files.map { it.documentId }.toSet()
        val result=ArrayList<SyncEntry>()
        var imported=0; var moved=0; var replaced=0; var failed=0
        // Kaybolmuş (bu taramada görülmeyen) kayıtlar taşıma adayıdır.
        val vanished=old.filter { it.state!=SourceState.REPLACED && it.documentId!=null && it.documentId !in seen }.toMutableList()
        val untouched=old.filter { it.state==SourceState.REPLACED || it.documentId==null }
        result+=untouched
        for(item in files) {
            val known=byDoc[item.documentId]
            try {
                if(known!=null && known.size==item.size && known.modified==item.modified) {
                    result+=relocate(known,item); continue
                }
                // Yeni veya değişmiş olabilir: içerik kopyalanıp parmak izi alınır.
                val (temp,hash)=library.receive { tree.open(item.documentId) }
                try {
                    when {
                        known!=null && known.sha256==hash -> result+=relocate(known,item).copy(size=item.size,modified=item.modified)
                        known!=null -> {
                            result+=known.copy(documentId=null,state=SourceState.REPLACED)
                            val key=library.createPdfNote(temp,hash,item.name.removeSuffix(".pdf").removeSuffix(".PDF"),item.path)
                            result+=SyncEntry(item.documentId,key,hash,item.size,item.modified,item.path,item.name,SourceState.AVAILABLE)
                            replaced++
                        }
                        else -> {
                            val candidates=vanished.filter { it.sha256==hash }
                            if(candidates.size==1) {
                                val from=candidates.single(); vanished.remove(from)
                                result+=relocate(from,item).copy(documentId=item.documentId,size=item.size,modified=item.modified)
                                moved++
                            } else {
                                val key=library.createPdfNote(temp,hash,item.name.removeSuffix(".pdf").removeSuffix(".PDF"),item.path)
                                result+=SyncEntry(item.documentId,key,hash,item.size,item.modified,item.path,item.name,SourceState.AVAILABLE)
                                imported++
                            }
                        }
                    }
                } finally { temp.delete() }
            } catch(e:Exception) {
                failed++
                known?.let { result+=it } // Okunamayan bilinen kaynak eski haliyle kalır.
            }
        }
        // Görülmeyenler: tamamlanmış taramada kayıp, eksik taramada bilinmiyor (eski hali korunur).
        var missing=0
        vanished.forEach { e ->
            if(scan.complete) { result+=e.copy(state=SourceState.MISSING); if(e.state!=SourceState.MISSING) missing++ }
            else result+=e
        }
        index.save(result,if(scan.complete) folders else (index.folders+folders).distinct().sorted(),System.currentTimeMillis())
        return SyncReport(imported,moved,missing,replaced,failed,scan.complete)
    }
    /** Kaynak taşındı/adı değişti: not klasörü güncellenir (kaynak kazanır), yazılar korunur. */
    private fun relocate(entry:SyncEntry,item:SourceItem):SyncEntry {
        if(entry.path!=item.path || entry.name!=item.name) {
            val oldTitle=entry.name.removeSuffix(".pdf").removeSuffix(".PDF")
            library.moveNote(entry.key,item.path,oldTitle,item.name.removeSuffix(".pdf").removeSuffix(".PDF"))
        }
        return entry.copy(documentId=item.documentId,path=item.path,name=item.name,state=SourceState.AVAILABLE)
    }
}
