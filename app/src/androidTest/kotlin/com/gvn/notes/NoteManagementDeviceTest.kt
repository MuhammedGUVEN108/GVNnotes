package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.graphics.pdf.PdfRenderer
import android.os.ParcelFileDescriptor
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 0.4.0: sayfa silme/sıralama, PDF dışa aktarma, çöp kutusu ve eşitlemeyle etkileşimi. */
@RunWith(AndroidJUnit4::class)
class NoteManagementDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val base get()=File(inst.targetContext.filesDir,"test-library")
    private val root get()=File(base,"notebooks")
    private val src get()=File(inst.targetContext.filesDir,"test-manage-src")
    private val library get()=NoteLibrary(root)

    private fun pdf(file:File,pages:Int) {
        file.parentFile!!.mkdirs()
        val document=PdfDocument()
        repeat(pages) { i ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(600,800,i+1).create())
            page.canvas.drawRect(100f,100f+i*100f,300f,200f+i*100f,Paint().apply { color=Color.BLACK })
            document.finishPage(page)
        }
        file.outputStream().use { document.writeTo(it) }; document.close()
    }
    private fun waitFor(timeout:Long=15000,check:()->Boolean)=compose.waitUntil(timeout) { runCatching(check).getOrDefault(false) }
    private fun editorFor(key:String):EditorState {
        var editor:EditorState?=null
        inst.runOnMainSync { editor=EditorState(library.directory(key)) }
        waitFor { var ready=false; inst.runOnMainSync { ready=editor!!.ready }; ready }
        return editor!!
    }
    private fun saved(editor:EditorState)=waitFor { var ok=false; inst.runOnMainSync { ok=editor.savedVersion==editor.editVersion && !editor.busy }; ok }

    @Test fun pagesExportAndTrash() {
        base.deleteRecursively(); src.deleteRecursively()

        // --- Sayfa sıralama ve silme ---
        val source=File(inst.targetContext.cacheDir,"manage.pdf"); pdf(source,3)
        val key=library.importPdf("Yönetim") { source.inputStream() }
        val editor=editorFor(key)
        val fixture=persistenceFixture(1,1).strokes.single()
        var ids=listOf<String>()
        inst.runOnMainSync {
            ids=editor.pages.map { it.id }
            // Yazı 2. sayfada.
            editor.replace(listOf(RenderedStroke(fixture.data.copy(pageId=ids[1]))))
        }
        saved(editor)
        inst.runOnMainSync { editor.movePage(1,0) }; saved(editor)
        PageStore(library.directory(key)).open().snapshot!!.let { s ->
            assertEquals(listOf(ids[1],ids[0],ids[2]),s.pages.map { it.id })
            assertEquals(listOf(0,1,2),s.pages.map { it.order })
            assertEquals(ids[1],s.strokes.single().data.pageId) // Yazı kendi sayfasıyla taşındı.
            assertEquals(1,s.pages[0].sourcePageIndex) // PDF zemini de sayfayla birlikte.
        }

        // --- Dışa aktarma: sayfa sayısı ve içerik ---
        val exported=File(inst.targetContext.cacheDir,"export.pdf")
        inst.runOnMainSync { editor.export { exported.outputStream() } }
        waitFor { var done=false; inst.runOnMainSync { done=editor.exporting==null && editor.exportResult!=null }; done }
        inst.runOnMainSync { assertEquals("PDF kaydedildi (3 sayfa).",editor.exportResult) }
        ParcelFileDescriptor.open(exported,ParcelFileDescriptor.MODE_READ_ONLY).use { fd -> PdfRenderer(fd).use { r ->
            assertEquals(3,r.pageCount)
            r.openPage(0).use { page ->
                val bitmap=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888); bitmap.eraseColor(Color.WHITE)
                page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
                // İlk sayfa artık PDF'nin 2. sayfası: siyah kutu y=200..300 pt (oran korunur) ve yazı (y=650 birim).
                val sx=bitmap.width/600f
                assertTrue("PDF background missing in export",Color.red(bitmap.getPixel((200*sx).toInt(),(250*sx).toInt()))<80)
                val inkY=(650f*bitmap.width/1000f).toInt()
                assertTrue("Ink missing in export",(390..610 step 10).any { x -> Color.red(bitmap.getPixel((x*bitmap.width/1000f).toInt(),inkY))<120 })
                bitmap.recycle()
            }
        } }

        // Silme: yazılı sayfa silinir, yazıları da gider; son sayfa silinemez.
        inst.runOnMainSync { editor.deletePage(0) }; saved(editor)
        PageStore(library.directory(key)).open().snapshot!!.let { s ->
            assertEquals(listOf(ids[0],ids[2]),s.pages.map { it.id }); assertTrue(s.strokes.isEmpty())
        }
        inst.runOnMainSync { editor.deletePage(0); }; saved(editor)
        inst.runOnMainSync { editor.deletePage(0); assertEquals(1,editor.pages.size) }

        // --- Çöp kutusu + eşitleme: çöpteki not yeniden içe aktarılmaz ---
        pdf(File(src,"Ders/konu.pdf"),2)
        val sync={ FolderSync(library,SyncIndex(library.syncIndexFile).load(),FileSourceTree(src)).run() }
        assertEquals(1,sync().imported)
        val synced=library.list().single { it.title=="konu" }
        library.moveToTrash(synced.key); SyncIndex(library.syncIndexFile).setState(synced.key,SourceState.TRASHED)
        assertEquals(0,sync().imported)
        assertTrue(library.list().none { it.key==synced.key })
        assertEquals(listOf(synced.key),library.trash().list().map { it.key })
        library.restoreFromTrash(synced.key); SyncIndex(library.syncIndexFile).setState(synced.key,SourceState.AVAILABLE)
        val afterRestore=sync()
        assertEquals(0,afterRestore.imported); assertEquals(0,afterRestore.missing)
        assertTrue(library.list().any { it.key==synced.key && it.folderPath=="Ders" })
        library.moveToTrash(synced.key); SyncIndex(library.syncIndexFile).setState(synced.key,SourceState.TRASHED)
        library.deleteFromTrash(synced.key)
        assertEquals(0,sync().imported) // Kalıcı silinen not da geri gelmez.
        assertTrue(library.trash().list().isEmpty())

        // --- Arayüz: menüden çöpe taşı, çöp kutusundan geri yükle ---
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitFor { compose.onAllNodesWithText("Yönetim").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithTag("note-menu-Yönetim").performClick()
        compose.onNodeWithTag("menu-trash").performClick()
        waitFor { compose.onAllNodesWithText("Not çöp kutusuna taşındı.").fetchSemanticsNodes().isNotEmpty() }
        waitFor { compose.onAllNodesWithText("Yönetim").fetchSemanticsNodes().isEmpty() }
        if(compose.onAllNodesWithTag("drawer").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("drawer").performClick()
        waitFor { compose.onAllNodesWithContentDescription("Çöp kutusu, 1 not").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Çöp kutusu, 1 not").performClick()
        waitFor { compose.onAllNodesWithTag("restore-Yönetim").fetchSemanticsNodes().isNotEmpty() }
        SystemClock.sleep(300)
        val shot=checkNotNull(inst.uiAutomation.takeScreenshot())
        File(inst.targetContext.getExternalFilesDir(null),"manage-test").apply { mkdirs() }.let { d ->
            File(d,"01-trash.png").outputStream().use { shot.compress(Bitmap.CompressFormat.PNG,100,it) } }
        shot.recycle()
        compose.onNodeWithTag("restore-Yönetim").performClick()
        waitFor { library.list().any { it.key==key } }
        inst.runOnMainSync { activity.finish() }
    }
}
