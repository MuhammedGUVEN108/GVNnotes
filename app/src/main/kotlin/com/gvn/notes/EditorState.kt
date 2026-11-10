package com.gvn.notes

import androidx.compose.runtime.*
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.ink.strokes.Stroke
import com.gvn.notes.core.*
import java.io.File
import kotlinx.coroutines.*
import kotlinx.coroutines.channels.Channel

data class RenderedStroke(val data: PageStroke, val ink: Stroke)

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
    var tool by mutableStateOf(Tool.PEN)
    var color by mutableIntStateOf(0xff18222e.toInt())
    var size by mutableFloatStateOf(3f)
    var mouseInput by mutableStateOf(false)
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
    /** Notun sonuna boş sayfa ekler. Çizgi geçmişinden bağımsızdır; geri alma sayfayı silmez. */
    fun addBlankPage() {
        if(!ready || busy || pages.size>=PageStore.MAX_PAGES) return
        val last=pages.last()
        pages=pages+Page(noteId=note.id,order=last.order+1,width=last.width,height=last.height)
        changed()
    }
    /** Not başlığını değiştirir; boş veya 100 karakterden uzun ad kabul edilmez. */
    fun rename(title:String) {
        val name=title.trim()
        if(!ready || name.isEmpty() || name.length>100 || name==note.title) return
        note=note.copy(title=name); revision++; changed()
    }
    fun replace(strokes: List<RenderedStroke>) { if(ready && strokes!=history.items) { history.replace(strokes); changed() } }
    fun undo() { if (ready && !busy && history.canUndo) { history.undo(); changed() } }
    fun redo() { if (ready && !busy && history.canRedo) { history.redo(); changed() } }
    override fun onCleared() { writes.close(); pdf?.close(); super.onCleared() }
}
