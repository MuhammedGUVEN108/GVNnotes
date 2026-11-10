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

data class NoteCard(val key:String,val title:String,val modified:Long,val strokeCount:Int,
    val revision:Long,val preview:File?,val unreadable:Boolean=false)

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
                val snapshot=PageStore(dir).open().snapshot ?: return@mapNotNull null
                val modified=listOf("document-a.gvn","document-b.gvn").maxOf { File(dir,it).lastModified() }
                val preview=runCatching { thumbnail(dir,snapshot) }.getOrNull()
                NoteCard(dir.name,snapshot.note.title,modified,snapshot.strokes.size,snapshot.revision,preview)
            } catch(e:Exception) {
                NoteCard(dir.name,"Not açılamadı",dir.lastModified(),0,0,null,true)
            }
        }.sortedByDescending { it.modified }
    }
    fun create(title:String):String {
        val name=title.trim()
        require(name.isNotEmpty() && name.length<=100) { "Not adı 1–100 karakter olmalı." }
        val key=UUID.randomUUID().toString(); val dir=directory(key)
        check(!dir.exists() && dir.mkdirs()) { "Yeni not klasörü oluşturulamadı." }
        val folder=Folder(parentId=null,name="Notlar",relativePath="")
        val note=Note(folderId=folder.id,title=name)
        val page=Page(noteId=note.id,order=0)
        try { PageStore(dir).save(PageSnapshot(1,folder,note,page,emptyList())) }
        catch(e:Exception) { dir.deleteRecursively(); throw e } // Yalnız bu çağrıda oluşturulan boş not.
        return key
    }
    private fun thumbnail(directory:File,snapshot:PageSnapshot):File {
        val image=File(directory,"preview.png"); val marker=File(directory,"preview-revision.txt")
        if(image.isFile && marker.isFile && marker.readText()==snapshot.revision.toString())return image
        val w=320; val h=(w*snapshot.page.height/snapshot.page.width).toInt().coerceIn(1,640)
        val bitmap=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        val file=AtomicFile(image); var output:FileOutputStream?=null
        try {
            val canvas=Canvas(bitmap); canvas.drawColor(Color.WHITE)
            val matrix=Matrix().apply { setScale(w/snapshot.page.width,h/snapshot.page.height) }
            canvas.concat(matrix)
            val renderer=CanvasStrokeRenderer.create()
            snapshot.strokes.forEach { renderer.draw(canvas,it.ink,matrix) }
            output=file.startWrite(); check(bitmap.compress(Bitmap.CompressFormat.PNG,100,output)); file.finishWrite(output)
            marker.writeText(snapshot.revision.toString())
            return image
        } catch(e:Exception) { output?.let { file.failWrite(it) }; throw e }
        finally { bitmap.recycle() }
    }
}

class LibraryState(val library:NoteLibrary):ViewModel() {
    var notes by mutableStateOf<List<NoteCard>>(emptyList()); private set
    var loading by mutableStateOf(true); private set
    var error by mutableStateOf<String?>(null); private set
    var creating by mutableStateOf(false); private set
    var creationError by mutableStateOf<String?>(null); private set
    var selectedKey by mutableStateOf<String?>(null); private set
    private var refreshJob:Job?=null
    private var refreshGeneration=0
    init { refresh() }
    fun refresh() {
        val generation=++refreshGeneration
        refreshJob?.cancel()
        refreshJob=viewModelScope.launch {
            loading=true; error=null
            try { notes=withContext(Dispatchers.IO) { library.list() } }
            catch(e:Exception) { if(e is CancellationException)throw e; error="Notlar açılamadı. Yeniden deneyin." }
            finally { if(generation==refreshGeneration)loading=false }
        }
    }
    fun open(key:String) { library.directory(key); selectedKey=key }
    fun close() { selectedKey=null; refresh() }
    fun clearCreationError() { creationError=null }
    fun create(title:String,onSuccess:()->Unit) {
        if(creating)return
        creating=true; creationError=null
        viewModelScope.launch {
            try {
                val key=withContext(Dispatchers.IO) { library.create(title) }
                selectedKey=key; onSuccess()
            } catch(e:Exception) {
                if(e is CancellationException)throw e
                creationError="Not oluşturulamadı. Adı ve depolama alanını kontrol edin."
            } finally { creating=false }
        }
    }
}
