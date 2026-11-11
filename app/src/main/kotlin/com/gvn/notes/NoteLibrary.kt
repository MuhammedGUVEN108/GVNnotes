package com.gvn.notes

import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Color
import android.util.AtomicFile
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.compose.runtime.*
import com.gvn.notes.core.*
import java.io.File
import java.io.FileOutputStream
import java.util.UUID
import kotlinx.coroutines.*
import kotlinx.coroutines.sync.withLock

data class NoteCard(val key:String,val title:String,val modified:Long,val strokeCount:Int,
    val revision:Long,val preview:File?,val unreadable:Boolean=false,val pageCount:Int=1,val isPdf:Boolean=false,
    val folderPath:String="")

/** Dizin adı not kimliğinden bağımsızdır; eski notebooks/main yerinde okunur. */
class NoteLibrary(private val root:File) {
    fun directory(key:String):File {
        require(key=="main" || runCatching { UUID.fromString(key).toString()==key }.getOrDefault(false))
        val file=File(root,key)
        require(file.canonicalFile.parentFile==root.canonicalFile)
        return file
    }
    fun list():List<NoteCard> {
        if(!root.exists())return emptyList()
        val directories=checkNotNull(root.listFiles()) { "Not klasörü okunamadı." }
        return directories.filter { it.isDirectory && runCatching { directory(it.name) }.isSuccess }.mapNotNull { dir ->
            try {
                val slots=listOf("document-a.gvn","document-b.gvn").map { File(dir,it) }
                val modified=slots.maxOf { it.lastModified() }
                // Kayıt dosyaları değişmediyse özetten oku: tüm çizgileri çözmeden liste hızla açılır.
                val stamp=slots.joinToString("|") { "${it.lastModified()}:${it.length()}" }
                cachedCard(dir,stamp,modified)?.let { return@mapNotNull it }
                val snapshot=PageStore(dir).open().snapshot ?: return@mapNotNull null
                val preview=runCatching { thumbnail(dir,snapshot) }.getOrNull()
                NoteCard(dir.name,snapshot.note.title,modified,snapshot.strokes.size,snapshot.revision,preview,
                    pageCount=snapshot.pages.size,isPdf=snapshot.source!=null,folderPath=snapshot.folder.relativePath)
                    .also { card -> writeCard(dir,stamp,card) }
            } catch(e:Exception) {
                NoteCard(dir.name,"Not açılamadı",dir.lastModified(),0,0,null,true)
            }
        }.sortedByDescending { it.modified }
    }
    /** Liste özeti: kayıt dosyalarının zaman/boyut damgası eşleşirse geçerlidir; bozuksa yok sayılır. */
    private fun cachedCard(dir:File,stamp:String,modified:Long):NoteCard? = runCatching {
        val o=org.json.JSONObject(File(dir,"card.json").readText())
        if(o.getInt("v")!=1 || o.getString("stamp")!=stamp) return@runCatching null
        val revision=o.getLong("revision")
        val image=File(dir,"preview.png"); val marker=File(dir,"preview-revision.txt")
        val preview=if(image.isFile && marker.isFile && marker.readText()==revision.toString()) image else return@runCatching null
        NoteCard(dir.name,o.getString("title"),modified,o.getInt("strokes"),revision,preview,
            pageCount=o.getInt("pages"),isPdf=o.getBoolean("pdf"),folderPath=o.getString("folder"))
    }.getOrNull()
    private fun writeCard(dir:File,stamp:String,card:NoteCard) {
        runCatching {
            val o=org.json.JSONObject().put("v",1).put("stamp",stamp).put("title",card.title).put("revision",card.revision)
                .put("strokes",card.strokeCount).put("pages",card.pageCount).put("pdf",card.isPdf).put("folder",card.folderPath)
            val file=AtomicFile(File(dir,"card.json")); val out=file.startWrite()
            try { out.write(o.toString().toByteArray()); file.finishWrite(out) } catch(e:Exception) { file.failWrite(out); throw e }
        }
    }
    fun create(title:String,pattern:Int=0):String {
        val name=title.trim()
        require(name.isNotEmpty() && name.length<=100) { "Not adı 1–100 karakter olmalı." }
        val key=UUID.randomUUID().toString(); val dir=directory(key)
        check(!dir.exists() && dir.mkdirs()) { "Yeni not klasörü oluşturulamadı." }
        val folder=Folder(parentId=null,name="Notlar",relativePath="")
        val note=Note(folderId=folder.id,title=name)
        val page=Page(noteId=note.id,order=0,pattern=pattern)
        try { PageStore(dir).save(PageSnapshot(1,folder,note,page,emptyList())) }
        catch(e:Exception) { dir.deleteRecursively(); throw e } // Yalnız bu çağrıda oluşturulan boş not.
        return key
    }
    /** Eşitleme dizini ve geçici dosyalar not klasörlerinin yanında tutulur; not listesine karışmaz. */
    val syncIndexFile get()=File(root.parentFile,"sync-index.json")
    private val incoming get()=File(root.parentFile,"incoming")

    /**
     * Kaynağı geçici dosyaya kopyalar ve SHA256 parmak izini aynı akıştan üretir
     * (kopyalanırken değişen dosyada hash ile içerik ayrışmaz). Özgün dosyaya dokunulmaz.
     */
    fun receive(input:()->java.io.InputStream):Pair<File,String> {
        check(incoming.isDirectory || incoming.mkdirs()) { "Geçici klasör oluşturulamadı." }
        val temp=File.createTempFile("pdf-",".part",incoming)
        try {
            val digest=java.security.MessageDigest.getInstance("SHA-256")
            FileOutputStream(temp).use { out ->
                input().use { stream ->
                    val buffer=ByteArray(64*1024); var total=0L
                    while(true) {
                        val n=stream.read(buffer); if(n<0) break
                        total+=n; require(total<=MAX_PDF_BYTES) { "PDF çok büyük." }
                        digest.update(buffer,0,n); out.write(buffer,0,n)
                    }
                    require(total>0) { "PDF boş." }
                }
                out.flush(); out.fd.sync()
            }
            return temp to digest.digest().joinToString("") { "%02x".format(it) }
        } catch(e:Exception) { temp.delete(); throw e }
    }

    /**
     * Alınmış PDF'yi yeni nota dönüştürür: her PDF sayfası bir not sayfası olur.
     * [folderPath] kaynak klasördeki yerdir ("" = kök). Hata olursa yalnız bu çağrıda oluşturulan not silinir.
     */
    fun createPdfNote(temp:File,hash:String,title:String,folderPath:String=""):String {
        val name=title.trim().ifEmpty { "PDF notu" }.take(100)
        val key=UUID.randomUUID().toString(); val dir=directory(key)
        check(!dir.exists() && dir.mkdirs()) { "Yeni not klasörü oluşturulamadı." }
        try {
            val target=PageStore.sourceFile(dir)
            check(temp.renameTo(target) || run { temp.copyTo(target); temp.delete(); true }) { "PDF kopyası tamamlanamadı." }
            val sizes=PdfPages.pageSizes(target)
            require(sizes.isNotEmpty() && sizes.size<=PageStore.MAX_PAGES) { "PDF sayfa sayısı desteklenmiyor." }
            val folder=folderOf(folderPath)
            val note=Note(folderId=folder.id,title=name)
            // Sayfa genişliği boş sayfayla aynı (1000 birim); kalem kalınlıkları her notta aynı görünür.
            val pages=sizes.mapIndexed { i,(w,h) ->
                Page(noteId=note.id,order=i,width=PAGE_WIDTH,height=PAGE_WIDTH*h/w,
                    background=BackgroundType.PDF,sourceBlobHash=hash,sourcePageIndex=i)
            }
            PageStore(dir).save(PageSnapshot(1,folder,note,pages,emptyList(),NoteSource(hash,name,sizes.size)))
            return key
        } catch(e:Exception) { dir.deleteRecursively(); throw e } // Yalnız bu çağrıda oluşturulan not.
    }

    /** Eşitlenen kaynağı not yapar: PDF doğrudan, resim önce tek sayfalık PDF'e çevrilerek. */
    fun createNoteFromSource(temp:File,hash:String,title:String,folderPath:String,fileName:String):String {
        if(!isImageName(fileName)) return createPdfNote(temp,hash,title,folderPath)
        val pdf=File.createTempFile("img-",".pdf",incoming)
        try { imagesToPdf(listOf(temp),pdf); return createPdfNote(pdf,hash,title,folderPath) } finally { pdf.delete() }
    }
    /**
     * Seçilen resimleri tek not yapar (her resim bir sayfa, seçim sırasıyla). İçerik kimliği
     * resimlerin parmak izlerinden türetilir; özgün resimlere dokunulmaz.
     */
    fun importImages(title:String,folderPath:String,inputs:List<()->java.io.InputStream>):String {
        require(inputs.isNotEmpty() && inputs.size<=PageStore.MAX_PAGES)
        val received=ArrayList<Pair<File,String>>()
        val pdf=File.createTempFile("img-",".pdf",incoming.apply { mkdirs() })
        try {
            inputs.forEach { received+=receive(it) }
            imagesToPdf(received.map { it.first },pdf)
            val hash=java.security.MessageDigest.getInstance("SHA-256").digest(received.joinToString(",") { it.second }.toByteArray())
                .joinToString("") { "%02x".format(it) }
            return createPdfNote(pdf,hash,title,folderPath)
        } finally { received.forEach { it.first.delete() }; pdf.delete() }
    }
    /** Elle seçilen PDF'yi kök klasörde not yapar. */
    fun importPdf(title:String,input:()->java.io.InputStream):String {
        val (temp,hash)=receive(input)
        try { return createPdfNote(temp,hash,title) } finally { temp.delete() }
    }

    /**
     * Kaynak taşındığında notun klasörünü günceller. Başlık yalnız kullanıcı değiştirmemişse
     * (hâlâ eski dosya adıysa) yeni dosya adı olur. Yazılar ve sayfalar olduğu gibi kalır.
     */
    fun moveNote(key:String,folderPath:String,oldTitle:String,newTitle:String) {
        val store=PageStore(directory(key))
        val snapshot=store.open().snapshot ?: return
        val title=if(snapshot.note.title==oldTitle) newTitle.take(100) else snapshot.note.title
        if(snapshot.folder.relativePath==folderPath && snapshot.note.title==title) return
        store.save(snapshot.copy(revision=snapshot.revision+1,folder=folderOf(folderPath).copy(id=snapshot.folder.id),
            note=snapshot.note.copy(title=title)))
    }
    // --- Çöp kutusu: not klasörü olduğu gibi trash/ altına taşınır; geri yükleme aynı klasörü geri getirir. ---
    private val trashRoot get()=File(root.parentFile,"trash")
    /** Çöpteki notları listeler (önizlemeler dahil). */
    fun trash():NoteLibrary=NoteLibrary(trashRoot)
    fun moveToTrash(key:String) {
        val dir=directory(key); val target=File(trashRoot,key)
        check(trashRoot.isDirectory || trashRoot.mkdirs()) { "Çöp klasörü oluşturulamadı." }
        check(!target.exists() && dir.renameTo(target)) { "Not çöpe taşınamadı." }
        runCatching { File(target,"trashed-at.txt").writeText(System.currentTimeMillis().toString()) }
    }
    fun restoreFromTrash(key:String) {
        val from=trash().directory(key); val target=directory(key)
        check(!target.exists() && from.renameTo(target)) { "Not geri yüklenemedi." }
        File(target,"trashed-at.txt").delete()
    }
    /** Kalıcı silme yalnız çöpteki not için yapılır. */
    fun deleteFromTrash(key:String) { trash().directory(key).deleteRecursively() }
    fun trashedAt(key:String)=runCatching { File(trash().directory(key),"trashed-at.txt").readText().toLong() }.getOrDefault(0L)
    fun rename(key:String,title:String) {
        val name=title.trim(); require(name.isNotEmpty() && name.length<=100)
        val store=PageStore(directory(key)); val snapshot=store.open().snapshot ?: return
        if(snapshot.note.title==name) return
        store.save(snapshot.copy(revision=snapshot.revision+1,note=snapshot.note.copy(title=name)))
    }
    private fun folderOf(path:String)=Folder(parentId=null,name=path.substringAfterLast('/').ifEmpty { "Notlar" },relativePath=path)
    private fun thumbnail(directory:File,snapshot:PageSnapshot):File {
        val image=File(directory,"preview.png"); val marker=File(directory,"preview-revision.txt")
        if(image.isFile && marker.isFile && marker.readText()==snapshot.revision.toString())return image
        val bitmap=renderPage(directory,snapshot.page,snapshot.strokes,320)
        val file=AtomicFile(image); var output:FileOutputStream?=null
        try {
            output=file.startWrite(); check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)); file.finishWrite(output)
            marker.writeText(snapshot.revision.toString())
            return image
        } catch(e:Exception) { output?.let { file.failWrite(it) }; throw e }
        finally { bitmap.recycle() }
    }
}

/**
 * Tek sayfayı (PDF zemini + o sayfanın çizgileri) verilen genişlikte bitmap'e çizer.
 * Kütüphane önizlemesi ve sayfa sıralayıcı küçük resimleri için; arka plan iş parçacığında çağrılır.
 */
fun renderPage(directory:File,page:Page,strokes:List<RenderedStroke>,width:Int):Bitmap {
    val h=(width*page.height/page.width).toInt().coerceIn(1,width*4)
    val bitmap=Bitmap.createBitmap(width,h,Bitmap.Config.ARGB_8888)
    try {
        val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
        if(page.background==BackgroundType.PDF && page.sourcePageIndex!=null) {
            val background=PdfPages.renderOnce(PageStore.sourceFile(directory),page.sourcePageIndex!!,width)
            try { canvas.drawBitmap(background,null,android.graphics.Rect(0,0,width,h),null) } finally { background.recycle() }
        }
        val matrix=Matrix().apply { setScale(width/page.width,h/page.height) }
        canvas.concat(matrix)
        drawPattern(canvas,page)
        val renderer=CanvasStrokeRenderer.create()
        strokes.filter { it.data.pageId==page.id }.forEach { renderer.draw(canvas,it.ink,matrix) }
        return bitmap
    } catch(e:Exception) { bitmap.recycle(); throw e }
}

private const val PAGE_WIDTH=1000f
private const val MAX_PDF_BYTES=512L*1024*1024

class LibraryState(val library:NoteLibrary,private val treeFor:(String)->SourceTree):ViewModel() {
    // --- Kaynak klasör eşitlemesi (0.3) ---
    private val sourceFile=File(library.syncIndexFile.parentFile,"sync-source.txt")
    /** Bağlı kaynak klasör (SAF ağaç URI'si); bağlı değilse null. */
    var source by mutableStateOf(runCatching { sourceFile.readText().trim().ifEmpty { null } }.getOrNull()); private set
    /** Kaynak klasöre yazma izni var mı; yoksa iki yönlü işlemler reddedilir. */
    var writable by mutableStateOf(false); private set
    var syncing by mutableStateOf(false); private set
    var syncMessage by mutableStateOf<String?>(null); private set
    /** Kaynak klasörde (boş olanlar dahil) görülen klasör yolları. */
    var folders by mutableStateOf<List<String>>(emptyList()); private set
    /** Not anahtarı → kaynak durumu (yalnız klasörden gelen notlar). */
    var sourceStates by mutableStateOf<Map<String,SourceState>>(emptyMap()); private set
    private var pendingOpen:String?=null
    private val syncLock=kotlinx.coroutines.sync.Mutex()

    fun link(uri:String) {
        runCatching { sourceFile.parentFile?.mkdirs(); sourceFile.writeText(uri) }
        source=uri; sync()
    }
    /** Kaynak klasörü tarar; not açıkken çalışmaz (açık notun kaydıyla yarışmasın). */
    fun sync() {
        val uri=source ?: return
        if(selectedKey!=null || syncing) return
        syncing=true; syncMessage="Eşitleniyor…"
        viewModelScope.launch {
            try {
                val report=syncLock.withLock { withContext(Dispatchers.IO) {
                    FolderSync(library,SyncIndex(library.syncIndexFile).load(),treeFor(uri)).run()
                } }
                syncMessage=report.summary
            } catch(e:Exception) {
                if(e is CancellationException) throw e
                syncMessage="Klasör okunamadı. İzni yenilemek için klasörü yeniden bağlayın."
            } finally {
                syncing=false
                loadSyncState(); refresh()
                pendingOpen?.let { pendingOpen=null; open(it) }
            }
        }
    }
    private fun loadSyncState() {
        writable=runCatching { tree()?.writable==true }.getOrDefault(false)
        val index=runCatching { SyncIndex(library.syncIndexFile).load() }.getOrNull() ?: return
        folders=index.folders
        sourceStates=index.entries.associate { it.key to it.state }
    }
    var notes by mutableStateOf<List<NoteCard>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var creating by mutableStateOf(false); private set
    var creationError by mutableStateOf<String?>(null); private set
    var selectedKey by mutableStateOf<String?>(null); private set
    private var refreshJob:Job?=null
    private var refreshGeneration=0
    init { loadSyncState(); refresh(); sync() }
    fun refresh() {
        val generation=++refreshGeneration
        refreshJob?.cancel()
        refreshJob=viewModelScope.launch {
            loading=true; error=null
            try {
                val (active,trashed)=withContext(Dispatchers.IO) {
                    library.list() to runCatching { library.trash().list().map { it.copy(modified=library.trashedAt(it.key).takeIf { t -> t>0 } ?: it.modified) } }.getOrDefault(emptyList())
                }
                notes=active; trash=trashed.sortedByDescending { it.modified }
            }
            catch(e:Exception) { if(e is CancellationException)throw e; error="Notlar açılamadı. Yeniden deneyin." }
            finally { if(generation==refreshGeneration)loading=false }
        }
    }
    /** Çöpteki notlar; [NoteCard.modified] çöpe atılma zamanıdır. */
    var trash by mutableStateOf<List<NoteCard>>(emptyList()); private set
    var actionMessage by mutableStateOf<String?>(null)
    /** Not dosyası işlemleri eşitlemeyle aynı kilitte, arka planda yürür; ardından liste yenilenir. */
    private fun noteAction(done:String,failed:String,action:()->String?) {
        viewModelScope.launch {
            actionMessage=try { syncLock.withLock { withContext(Dispatchers.IO) { action() } } ?: done }
            catch(e:NameTakenException) { "Hedefte aynı adda bir öge var; işlem yapılmadı." }
            catch(e:NoWriteException) { "Kaynak klasöre yazma izni yok. Klasörü yeniden bağlayın." }
            catch(e:Exception) { if(e is CancellationException) throw e; failed }
            loadSyncState(); refresh()
        }
    }
    private class NoWriteException:IllegalStateException()
    /** Bağlı kaynak klasör (yoksa null). */
    private fun tree():SourceTree?=source?.let { runCatching { treeFor(it) }.getOrNull() }
    private fun writableTree():SourceTree { val t=tree() ?: throw NoWriteException(); if(!t.writable) throw NoWriteException(); return t }
    /** Klasörden gelen ve kaynağı yerinde olan notun eşitleme kaydı. */
    private fun liveEntry(key:String)=index.entry(key)?.takeIf { it.state==SourceState.AVAILABLE && it.documentId!=null && !it.inTrash }
    private val index get()=SyncIndex(library.syncIndexFile)
    /**
     * Notu çöpe taşır. Klasörden gelen notun kaynak dosyası, yazma izni varsa kaynak klasördeki
     * .GVNnotes-çöp klasörüne taşınır (iki yönlü); izin yoksa dosya yerinde kalır ama yeniden içe aktarılmaz.
     */
    fun moveToTrash(key:String)=noteAction("Not çöp kutusuna taşındı.","Not çöpe taşınamadı.") {
        val entry=liveEntry(key); val t=tree()
        var message:String?=null
        if(entry!=null && t!=null && t.writable) {
            val trashed=t.moveToTrash(entry.documentId!!,entry.path,entry.name)
            index.update(key) { it.copy(documentId=trashed,inTrash=true) }
            message="Not ve kaynak dosyası çöpe taşındı."
        }
        library.moveToTrash(key); index.setState(key,SourceState.TRASHED)
        message
    }
    /** Notu geri yükler; kaynak dosyası .GVNnotes-çöp'teyse eski klasörüne özgün adıyla geri getirilir. */
    fun restore(key:String)=noteAction("Not geri yüklendi.","Not geri yüklenemedi.") {
        library.restoreFromTrash(key)
        val entry=index.entry(key); val t=tree()
        var message:String?=null
        if(entry!=null && entry.inTrash && entry.documentId!=null && t!=null && t.writable) {
            try {
                val back=t.restoreFromTrash(entry.documentId,entry.path,entry.name)
                index.update(key) { it.copy(documentId=back,inTrash=false,state=SourceState.AVAILABLE) }
            } catch(e:NameTakenException) {
                index.setState(key,SourceState.MISSING)
                message="Not geri yüklendi; klasörde aynı adlı dosya olduğu için kaynak çöpte kaldı."
            }
        } else index.setState(key,if(entry?.inTrash==true) SourceState.MISSING else SourceState.AVAILABLE)
        message
    }
    /** Notu kalıcı siler. Kaynak dosya silinmez; .GVNnotes-çöp klasöründe kalır (kullanıcı dosyası). */
    fun deleteForever(key:String)=noteAction("Not kalıcı olarak silindi.","Not silinemedi.") { library.deleteFromTrash(key); null }
    fun rename(key:String,title:String)=noteAction("Not adı değiştirildi.","Not adı değiştirilemedi.") { library.rename(key,title); null }
    /**
     * Notu başka klasöre taşır. Klasörden gelen notun kaynak dosyası da kaynak klasörde taşınır (kaynak kazanır;
     * yalnız uygulamada taşımak sonraki eşitlemede geri alınırdı). Hedefte aynı ad varsa reddedilir.
     */
    fun moveNote(key:String,target:String)=noteAction("Not taşındı.","Not taşınamadı.") {
        val entry=liveEntry(key)
        if(entry!=null && entry.path!=target) {
            val moved=writableTree().move(entry.documentId!!,entry.path,target,entry.name)
            index.update(key) { it.copy(documentId=moved,path=target) }
        }
        library.moveNote(key,target,oldTitle="\u0000",newTitle="")
        null
    }
    /** Klasör oluşturur: kaynak klasör bağlıysa orada (iki yönlü), değilse yalnız uygulamada. */
    fun createFolder(parent:String,name:String)=noteAction("Klasör oluşturuldu.","Klasör oluşturulamadı.") {
        val clean=name.trim()
        require(clean.isNotEmpty() && clean.length<=80 && !clean.startsWith(".") && clean.none { it in "/\\:*?\"<>|" })
        if(source!=null) writableTree().createFolder(parent,clean)
        index.addFolder(if(parent.isEmpty()) clean else "$parent/$clean")
        null
    }
    /** Seçilen resimleri tek not yapar (her resim bir sayfa). */
    fun importImages(title:String,folderPath:String,inputs:List<()->java.io.InputStream>) {
        if(importing) return
        importing=true; importError=null
        viewModelScope.launch {
            try { selectedKey=withContext(Dispatchers.IO) { library.importImages(title,folderPath,inputs) } }
            catch(e:Exception) { if(e is CancellationException) throw e; importError="Resim açılamadı. Dosya bozuk ya da desteklenmeyen biçimde olabilir." }
            finally { importing=false }
        }
    }
    fun open(key:String) {
        library.directory(key)
        if(syncing) pendingOpen=key else selectedKey=key // Eşitleme bitince açılır.
    }
    fun close() { selectedKey=null; refresh(); sync() }
    fun clearCreationError() { creationError=null }
    var importing by mutableStateOf(false); private set
    var importError by mutableStateOf<String?>(null); private set
    fun clearImportError() { importError=null }
    /** Seçilen PDF'yi kopyalayıp not yapar ve notu açar. */
    fun importPdf(title:String,input:()->java.io.InputStream) {
        if(importing) return
        importing=true; importError=null
        viewModelScope.launch {
            try { selectedKey=withContext(Dispatchers.IO) { library.importPdf(title,input) } }
            catch(e:Exception) {
                if(e is CancellationException) throw e
                importError="PDF açılamadı. Dosya bozuk, şifreli veya çok büyük olabilir."
            } finally { importing=false }
        }
    }
    fun create(title:String,pattern:Int=0,onSuccess:()->Unit) {
        if(creating)return
        creating=true; creationError=null
        viewModelScope.launch {
            try {
                val key=withContext(Dispatchers.IO) { library.create(title,pattern) }
                selectedKey=key; onSuccess()
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                creationError="Not oluşturulamadı. Adı ve depolama alanını kontrol edin."
            } finally { creating=false }
        }
    }
}
