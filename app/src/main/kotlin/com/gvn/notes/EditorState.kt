package com.gvn.notes

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import com.gvn.notes.core.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

/** Kayıtlı çizgi. GVNpen çizim görünümü (Geometry.Stroke) ilk kullanımda üretilir; çizgiler kayıttan sonra değişmez. */
data class RenderedStroke(val data: PageStroke) {
    val geo: com.gvn.notes.pen.Geometry.Stroke by lazy { data.toGeo() }
}


class EditorState(val directory:File) : ViewModel() {
    var folder = Folder(parentId=null,name="Notlar",relativePath=""); private set
    var note = Note(folderId=folder.id,title="İlk sayfam"); private set
    var pages by mutableStateOf(listOf(Page(noteId=note.id,order=0))); private set
    /** İlk sayfa; tek sayfalı eski notlar ve testler için. */
    val page:Page get()=pages.first()
    var source:NoteSource?=null; private set
    /** PDF arka planı olan notlarda sayfa görüntülerini üretir; boş notlarda null. */
    var pdf:PdfPages?=null; private set
    val history = History<RenderedStroke>()
    var revision by mutableIntStateOf(0); private set
    var mouseInput by mutableStateOf(false)
    /** Kement seçimi modu (GVNpen şeridinde yok; üst çubuktan açılır). */
    var lassoMode by mutableStateOf(false)
    var busy by mutableStateOf(false)
    var navigationPending by mutableStateOf(false)
    var viewport by mutableStateOf(Viewport())
    /** Görünür alanın ortasındaki sayfa (0 tabanlı); yalnız gösterim içindir. */
    var currentPage by mutableIntStateOf(0)
    var fitted = false
    var ready by mutableStateOf(false); private set
    var saveStatus by mutableStateOf("Açılıyor…"); private set
    var problem by mutableStateOf<String?>(null); private set
    var savedVersion by mutableLongStateOf(0); private set
    var editVersion by mutableLongStateOf(0); private set
    private val store=PageStore(directory)
    private val writes=Channel<PageSnapshot>(Channel.CONFLATED)
    init {
        open()
        viewModelScope.launch(Dispatchers.IO) {
            for(snapshot in writes) {
                try {
                    store.save(snapshot)
                    withContext(Dispatchers.Main) {
                        savedVersion=snapshot.revision
                        if(savedVersion==editVersion) { saveStatus="Kaydedildi"; problem=null }
                    }
                } catch(e:Exception) {
                    if(e is CancellationException) throw e
                    withContext(Dispatchers.Main) { problem="Kayıt yapılamadı. Değişiklikler bellekte duruyor."; saveStatus="Kaydedilemedi" }
                }
            }
        }
    }
    private fun open() = viewModelScope.launch {
        problem=null; saveStatus="Açılıyor…"
        try {
            val loaded=withContext(Dispatchers.IO) { store.open() }
            loaded.snapshot?.let { snapshot ->
                folder=snapshot.folder; note=snapshot.note; pages=snapshot.pages; source=snapshot.source
                if(snapshot.source!=null && pdf==null) pdf=PdfPages(PageStore.sourceFile(directory))
                history.restore(snapshot.strokes); editVersion=snapshot.revision; savedVersion=snapshot.revision
            }
            ready=true; revision++
            saveStatus=if(loaded.recovered) "Önceki sağlam kayıt açıldı" else if(loaded.snapshot!=null) "Kaydedildi" else "Otomatik kayıt açık"
        } catch(e:Exception) {
            if(e is CancellationException) throw e
            ready=false; problem="Not açılamadı. Kayıt dosyaları korundu; üzerine yazılmayacak."
            saveStatus="Açılamadı"
        }
    }
    private fun changed() {
        revision++; editVersion++; saveStatus="Kaydediliyor…"
        writes.trySend(snapshot())
    }
    fun retry() { if(ready) { saveStatus="Kaydediliyor…"; writes.trySend(snapshot()) } else open() }
    private fun snapshot()=PageSnapshot(editVersion,folder,note,pages,history.items.toList(),source)
    /**
     * [index] numaralı sayfanın arkasına boş sayfa ekler (Samsung Notes'taki "+" gibi). Boyut o sayfanınkiyle
     * aynıdır; PDF sayfasının arkasına eklenen sayfa düz kâğıttır.
     */
    fun insertBlankPageAfter(index:Int):Int {
        if(!ready || busy || pages.size>=PageStore.MAX_PAGES || index !in pages.indices) return -1
        val base=pages[index]
        val blank=Page(noteId=note.id,order=0,width=base.width,height=base.height,pattern=defaultPattern(base))
        val list=pages.toMutableList(); list.add(index+1,blank)
        pages=list.mapIndexed { i,p -> p.copy(order=i) }
        changed()
        return index+1
    }
    /** Yeni boş sayfanın şablonu: yanındaki boş sayfanınki, PDF sayfasıysa notun ilk boş sayfasınınki. */
    private fun defaultPattern(near:Page)=if(near.background==BackgroundType.BLANK) near.pattern
        else pages.firstOrNull { it.background==BackgroundType.BLANK }?.pattern ?: 0
    /** Boş sayfanın şablonunu değiştirir (PDF/resim sayfalarında etkisiz). */
    fun setPattern(index:Int,pattern:Int) {
        if(!ready || busy || index !in pages.indices || pages[index].background!=BackgroundType.BLANK || pages[index].pattern==pattern) return
        pages=pages.mapIndexed { i,p -> if(i==index) p.copy(pattern=pattern) else p }
        changed()
    }
    /** Notun sonuna boş sayfa ekler. Çizgi geçmişinden bağımsızdır; geri alma sayfayı silmez. */
    fun addBlankPage() {
        if(!ready || busy || pages.size>=PageStore.MAX_PAGES) return
        val last=pages.last()
        pages=pages+Page(noteId=note.id,order=last.order+1,width=last.width,height=last.height,pattern=defaultPattern(last))
        changed()
    }
    var exporting by mutableStateOf<String?>(null); private set
    var exportResult by mutableStateOf<String?>(null)
    /** Notu yazılarıyla PDF olarak [open] ile açılan hedefe yazar (arka planda). */
    fun export(open:()->java.io.OutputStream) {
        if(!ready || exporting!=null) return
        val pagesNow=pages; val strokesNow=history.items.toList()
        exporting="Dışa aktarılıyor…"; exportResult=null
        viewModelScope.launch {
            exportResult=try {
                withContext(Dispatchers.IO) {
                    open().use { out -> exportPdf(directory,pagesNow,strokesNow,out) { n ->
                        launch(Dispatchers.Main) { exporting="Dışa aktarılıyor… $n/${pagesNow.size}" } } }
                }
                "PDF kaydedildi (${pagesNow.size} sayfa)."
            } catch(e:Exception) {
                if(e is CancellationException) throw e
                "PDF kaydedilemedi. Kaynak PDF’yi ve hedef depolama alanını kontrol edip yeniden deneyin."
            } finally { exporting=null }
        }
    }
    /**
     * Sayfayı ve üzerindeki yazıları siler. Sayfa yapısı değiştiği için geri alma geçmişi sıfırlanır;
     * arayüz bu yüzden önce onay ister. Son sayfa silinemez.
     */
    fun deletePage(index:Int) {
        if(!ready || busy || pages.size<=1 || index !in pages.indices) return
        val id=pages[index].id
        pages=pages.filterIndexed { i,_ -> i!=index }.mapIndexed { i,p -> p.copy(order=i) }
        history.restore(history.items.filter { it.data.pageId!=id })
        if(currentPage>pages.lastIndex) currentPage=pages.lastIndex
        changed()
    }
    /** Sayfanın sırasını değiştirir; yazılar kendi sayfalarıyla birlikte taşınır. */
    fun movePage(from:Int,to:Int) {
        if(!ready || busy || from !in pages.indices || to !in pages.indices || from==to) return
        val list=pages.toMutableList(); val page=list.removeAt(from); list.add(to,page)
        pages=list.mapIndexed { i,p -> p.copy(order=i) }
        changed()
    }
    /** Not başlığını değiştirir; boş veya 100 karakterden uzun ad kabul edilmez. */
    fun rename(title:String) {
        val name=title.trim()
        if(!ready || name.isEmpty() || name.length>100 || name==note.title) return
        note=note.copy(title=name); revision++; changed()
    }
    // --- Kement seçimi ---
    /** Seçili çizgilerin kimlikleri (boş = seçim yok). */
    var selection by mutableStateOf<Set<String>>(emptySet())
    private fun selected()=history.items.filter { it.data.id in selection }
    fun clearSelection() { selection=emptySet() }
    /** Seçimi siler (tek geri alma adımı). */
    fun deleteSelection() {
        if(!ready || busy || selection.isEmpty()) return
        history.replace(history.items.filter { it.data.id !in selection }); selection=emptySet(); changed()
    }
    /** Seçimi sayfa içinde kaydırır; kimlikler korunur, seçim sürer. */
    fun moveSelection(dx:Float,dy:Float) {
        if(!ready || busy || selection.isEmpty() || (dx==0f && dy==0f)) return
        history.replace(history.items.map { rs ->
            if(rs.data.id !in selection) rs else {
                RenderedStroke(rs.data.copy(samples=rs.data.samples.map { it.copy(x=it.x+dx,y=it.y+dy) }))
            }
        }); changed()
    }
    /** Seçimin kopyasını biraz kaydırarak ekler; seçim kopyalara geçer. */
    fun duplicateSelection() {
        if(!ready || busy || selection.isEmpty()) return
        val copies=selected().map { rs -> rs.withSamples(rs.data.samples.map { it.copy(x=it.x+24f,y=it.y+24f) }) }
        history.replace(history.items+copies); selection=copies.map { it.data.id }.toSet(); changed()
    }
    /** Seçili çizgilere geçerli rengi uygular. */
    fun recolorSelection(value:Int) {
        if(!ready || busy || selection.isEmpty()) return
        history.replace(history.items.map { rs ->
            if(rs.data.id !in selection) rs else {
                RenderedStroke(rs.data.copy(color=(value and 0x00ffffff) or (rs.data.color and 0xff000000.toInt())))
            }
        }); changed()
    }
    fun replace(strokes: List<RenderedStroke>) { if(ready && strokes!=history.items) { history.replace(strokes); changed() } }
    fun undo() { if (ready && !busy && history.canUndo) { history.undo(); selection=emptySet(); changed() } }
    fun redo() { if (ready && !busy && history.canRedo) { history.redo(); selection=emptySet(); changed() } }
    /** Not en son hangi sayfada bırakıldıysa oradan açılır (yalnız görünüm bilgisi; not kaydından ayrı). */
    private val viewFile get()=File(directory,"view.txt")
    var initialPage=runCatching { viewFile.readText().trim().toInt() }.getOrDefault(0)
    override fun onCleared() {
        runCatching { if(directory.isDirectory) viewFile.writeText(currentPage.toString()) }
        writes.close(); pdf?.close(); super.onCleared()
    }
}
