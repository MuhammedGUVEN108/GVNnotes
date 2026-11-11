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
    /** Kaynağa yazılabiliyor mu (kalıcı yazma izni). false ise iki yönlü işlemler yapılmaz. */
    val writable:Boolean get()=false
    /** [parentPath] altında klasör oluşturur. Aynı ad varsa [NameTakenException]. */
    fun createFolder(parentPath:String,name:String):Unit=throw UnsupportedOperationException()
    /** Belgeyi [toPath] klasörüne taşır, yeni belge kimliğini döndürür. Hedefte aynı ad varsa [NameTakenException]. */
    fun move(documentId:String,fromPath:String,toPath:String,name:String):String=throw UnsupportedOperationException()
    /** Belgeyi kökteki [TRASH_FOLDER] klasörüne taşır (gerekirse oluşturur, ad çakışırsa benzersiz ad verir). */
    fun moveToTrash(documentId:String,fromPath:String,name:String):String=throw UnsupportedOperationException()
    /** Çöpteki belgeyi [toPath] klasörüne özgün adıyla geri getirir. Hedefte aynı ad varsa [NameTakenException]. */
    fun restoreFromTrash(documentId:String,toPath:String,originalName:String):String=throw UnsupportedOperationException()
}

class NameTakenException(name:String):IllegalStateException("Hedefte aynı adda öge var: $name")
/** Uygulamanın çöpe attığı kaynak dosyaların konduğu klasör; gizli olduğu için taranmaz. */
const val TRASH_FOLDER=".GVNnotes-çöp"

/** Sistem klasör erişimi (SAF): kullanıcının bir kez izin verdiği klasör ağacı. */
class SafSourceTree(private val context:Context,private val tree:Uri):SourceTree {
    private val resolver get()=context.contentResolver
    private fun uri(id:String)=DocumentsContract.buildDocumentUriUsingTree(tree,id)
    private val rootId get()=DocumentsContract.getTreeDocumentId(tree)
    override val writable:Boolean get()=resolver.persistedUriPermissions.any { it.uri==tree && it.isWritePermission }
    override fun scan():SourceScan {
        val items=ArrayList<SourceItem>(); var complete=true
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
                    else if(isSource(name,c.getString(2))) items+=item
                }
            }
            dirs.forEach { (id,p) -> walk(id,p,depth+1) }
        }
        val root=runCatching { rootId }.getOrNull() ?: return SourceScan(emptyList(),false)
        walk(root,"",0)
        return SourceScan(items,complete)
    }
    override fun open(documentId:String):InputStream =
        checkNotNull(resolver.openInputStream(uri(documentId))) { "Kaynak dosya okunamadı." }

    private fun nameOf(id:String):String? =
        resolver.query(uri(id),arrayOf(DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { if(it.moveToFirst()) it.getString(0) else null }
    /** Doğrudan çocuklar arasında adı eşleşen ögenin kimliği. */
    private fun child(parentId:String,name:String):String? =
        resolver.query(DocumentsContract.buildChildDocumentsUriUsingTree(tree,parentId),arrayOf(
            DocumentsContract.Document.COLUMN_DOCUMENT_ID,DocumentsContract.Document.COLUMN_DISPLAY_NAME),null,null,null)?.use { c ->
            var found:String?=null
            while(c.moveToNext()) if(c.getString(1)==name) { found=c.getString(0); break }
            found
        }
    /** Klasör yolunu kimliğe çevirir; [create] ise eksik klasörleri oluşturur. */
    private fun dir(path:String,create:Boolean=false):String {
        var id=rootId
        if(path.isEmpty()) return id
        for(segment in path.split('/')) {
            id=child(id,segment) ?: if(create) DocumentsContract.getDocumentId(checkNotNull(DocumentsContract.createDocument(resolver,uri(id),
                DocumentsContract.Document.MIME_TYPE_DIR,segment)) { "Klasör oluşturulamadı." })
                else throw IllegalStateException("Klasör bulunamadı: $path")
        }
        return id
    }
    override fun createFolder(parentPath:String,name:String) {
        val parent=dir(parentPath)
        if(child(parent,name)!=null) throw NameTakenException(name)
        val created=DocumentsContract.createDocument(resolver,uri(parent),DocumentsContract.Document.MIME_TYPE_DIR,name)
        // Bazı sağlayıcılar çakışmada "ad (1)" üretir; istenen ad oluşmadıysa geri al ve reddet.
        if(created==null || nameOf(DocumentsContract.getDocumentId(created))!=name) {
            created?.let { DocumentsContract.deleteDocument(resolver,it) }; throw NameTakenException(name)
        }
    }
    private fun moveDoc(documentId:String,fromParent:String,toParent:String):String {
        val moved=checkNotNull(DocumentsContract.moveDocument(resolver,uri(documentId),uri(fromParent),uri(toParent))) { "Dosya taşınamadı." }
        return DocumentsContract.getDocumentId(moved)
    }
    override fun move(documentId:String,fromPath:String,toPath:String,name:String):String {
        val target=dir(toPath)
        if(child(target,name)!=null) throw NameTakenException(name)
        return moveDoc(documentId,dir(fromPath),target)
    }
    override fun moveToTrash(documentId:String,fromPath:String,name:String):String {
        val trash=dir(TRASH_FOLDER,create=true)
        var id=documentId
        if(child(trash,name)!=null) {
            // Çöpte aynı ad varsa önce kaynakta benzersiz ada çevir; hiçbir dosyanın üzerine yazılmaz.
            val unique=uniqueNames(name).first { child(trash,it)==null }
            id=DocumentsContract.getDocumentId(checkNotNull(DocumentsContract.renameDocument(resolver,uri(id),unique)) { "Dosya adlandırılamadı." })
        }
        return moveDoc(id,dir(fromPath),trash)
    }
    override fun restoreFromTrash(documentId:String,toPath:String,originalName:String):String {
        val target=dir(toPath,create=true)
        if(child(target,originalName)!=null) throw NameTakenException(originalName)
        var id=moveDoc(documentId,dir(TRASH_FOLDER),target)
        if(nameOf(id)!=originalName)
            id=DocumentsContract.getDocumentId(checkNotNull(DocumentsContract.renameDocument(resolver,uri(id),originalName)) { "Dosya adlandırılamadı." })
        return id
    }
}

/** "ad (2).pdf", "ad (3).pdf"… */
private fun uniqueNames(name:String):Sequence<String> {
    val base=name.substringBeforeLast('.'); val ext=name.substringAfterLast('.',"")
    return generateSequence(2) { it+1 }.map { "$base ($it)${if(ext.isEmpty() || !name.contains('.')) "" else ".$ext"}" }
}

/** Testler için düz dosya sistemi kaynağı. Belge kimliği göreli yoldur (SAF yerel sağlayıcısındaki gibi taşınınca değişir). */
class FileSourceTree(private val root:File,override val writable:Boolean=true):SourceTree {
    override fun scan():SourceScan {
        if(!root.isDirectory) return SourceScan(emptyList(),false)
        val items=ArrayList<SourceItem>()
        // Gizli klasörlere (ör. .GVNnotes-çöp) hiç girilmez; içindekiler de taranmaz.
        root.walkTopDown().onEnter { it==root || !it.name.startsWith(".") }.filter { it!=root && !it.name.startsWith(".") }.forEach { f ->
            val rel=f.relativeTo(root).invariantSeparatorsPath
            val parent=rel.substringBeforeLast('/',"")
            if(f.isDirectory) items+=SourceItem(rel,parent,f.name,true,-1,f.lastModified())
            else if(isSource(f.name,null)) items+=SourceItem(rel,parent,f.name,false,f.length(),f.lastModified())
        }
        return SourceScan(items,true)
    }
    override fun open(documentId:String):InputStream=File(root,documentId).inputStream()
    private fun at(path:String)=if(path.isEmpty()) root else File(root,path)
    private fun id(f:File)=f.relativeTo(root).invariantSeparatorsPath
    override fun createFolder(parentPath:String,name:String) {
        val f=File(at(parentPath),name); if(f.exists()) throw NameTakenException(name); check(f.mkdirs())
    }
    override fun move(documentId:String,fromPath:String,toPath:String,name:String):String {
        val target=File(at(toPath),name); if(target.exists()) throw NameTakenException(name)
        target.parentFile!!.mkdirs(); check(File(root,documentId).renameTo(target)) { "Dosya taşınamadı." }; return id(target)
    }
    override fun moveToTrash(documentId:String,fromPath:String,name:String):String {
        val trash=File(root,TRASH_FOLDER).apply { mkdirs() }
        val target=(sequenceOf(name)+uniqueNames(name)).map { File(trash,it) }.first { !it.exists() }
        check(File(root,documentId).renameTo(target)) { "Dosya çöpe taşınamadı." }; return id(target)
    }
    override fun restoreFromTrash(documentId:String,toPath:String,originalName:String):String {
        val target=File(at(toPath),originalName); if(target.exists()) throw NameTakenException(originalName)
        target.parentFile!!.mkdirs(); check(File(root,documentId).renameTo(target)) { "Dosya geri getirilemedi." }; return id(target)
    }
}

private const val MAX_DEPTH=12
private val IMAGE_EXT=setOf("jpg","jpeg","png","webp")
fun isImageName(name:String)=name.substringAfterLast('.',"").lowercase() in IMAGE_EXT
/** Eşitlenen kaynak türleri: PDF ve JPEG/PNG/WebP resimler. */
private fun isSource(name:String,mime:String?)=mime=="application/pdf" || name.endsWith(".pdf",ignoreCase=true) ||
    mime in setOf("image/jpeg","image/png","image/webp") || isImageName(name)
/** Dosya adından not başlığı (uzantısız). */
fun titleOf(name:String)=if(name.contains('.')) name.substringBeforeLast('.') else name

/** TRASHED: not uygulamada çöpe atıldı; kaynak klasörde dursa da yeniden içe aktarılmaz. */
enum class SourceState { AVAILABLE, MISSING, REPLACED, TRASHED }

/** Kaynak dosya ↔ not eşlemesi. Not kimliği UUID kalır; hash yalnız içerik kimliğidir. */
data class SyncEntry(val documentId:String?,val key:String,val sha256:String,val size:Long,val modified:Long,
    val path:String,val name:String,val state:SourceState,val inTrash:Boolean=false)

/** Eşitleme dizini: notebooks kökünün yanında, atomik yazılır. */
class SyncIndex(private val file:File) {
    /** Tek notun kaynak durumunu değiştirir (çöpe atma/geri yükleme). Kayıt yoksa bir şey yapmaz. */
    fun setState(key:String,state:SourceState)=update(key) { it.copy(state=state) }
    fun entry(key:String)=load().entries.firstOrNull { it.key==key }
    fun update(key:String,change:(SyncEntry)->SyncEntry) {
        load()
        if(entries.none { it.key==key }) return
        save(entries.map { if(it.key==key) change(it) else it },folders,lastSync)
    }
    fun addFolder(path:String) { load(); save(entries,(folders+path).distinct().sorted(),lastSync) }
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
                o.getLong("modified"),o.getString("path"),o.getString("name"),SourceState.valueOf(o.getString("state")),o.optBoolean("inTrash"))
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
                .put("state",e.state.name).put("inTrash",e.inTrash) }))
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
        // Etkin kayıtlar eşlenir; değişmiş (REPLACED) ve çöpteki (TRASHED) kayıtlar olduğu gibi korunur.
        val active=old.filter { it.documentId!=null && (it.state==SourceState.AVAILABLE || it.state==SourceState.MISSING) }
        val byDoc=active.associateBy { it.documentId!! }
        val trashedDocs=old.filter { it.state==SourceState.TRASHED }.mapNotNull { it.documentId }.toSet()
        val seen=files.map { it.documentId }.toSet()
        val result=ArrayList<SyncEntry>()
        var imported=0; var moved=0; var replaced=0; var failed=0
        // Kaybolmuş (bu taramada görülmeyen) kayıtlar taşıma adayıdır.
        val vanished=active.filter { it.documentId !in seen }.toMutableList()
        val untouched=old.filter { it !in active }
        result+=untouched
        for(item in files) {
            if(item.documentId in trashedDocs) continue // Kullanıcı bu notu çöpe attı; yeniden oluşturma.
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
                            val key=library.createNoteFromSource(temp,hash,titleOf(item.name),item.path,item.name)
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
                                val key=library.createNoteFromSource(temp,hash,titleOf(item.name),item.path,item.name)
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
            val oldTitle=titleOf(entry.name)
            library.moveNote(entry.key,item.path,oldTitle,titleOf(item.name))
        }
        return entry.copy(documentId=item.documentId,path=item.path,name=item.name,state=SourceState.AVAILABLE)
    }
}
