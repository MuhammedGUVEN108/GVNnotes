package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.Page
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.io.InputStream

/** 0.3: Kaynak klasör → not eşitlemesi. SAF yerine düz klasör kullanılır; kurallar aynı motorda sınanır. */
@RunWith(AndroidJUnit4::class)
class FolderSyncDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val base get()=File(inst.targetContext.filesDir,"test-library")
    private val root get()=File(base,"notebooks")
    private val src get()=File(inst.targetContext.filesDir,"test-sync-src")
    private val library get()=NoteLibrary(root)

    /** Her dosya farklı içerik (farklı hash) taşısın diye işaretli sayfa üretir. */
    private fun pdf(file:File,mark:Int,pages:Int=1) {
        file.parentFile!!.mkdirs()
        val document=PdfDocument()
        repeat(pages) { i ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(600,800,i+1).create())
            page.canvas.drawRect(20f+mark*30f,20f,40f+mark*30f,40f,Paint().apply { color=Color.BLACK })
            document.finishPage(page)
        }
        file.outputStream().use { document.writeTo(it) }; document.close()
    }
    private fun sync(tree:SourceTree=FileSourceTree(src))=FolderSync(library,SyncIndex(library.syncIndexFile).load(),tree).run()
    private fun notes()=library.list()
    private fun index()=SyncIndex(library.syncIndexFile).load()
    private fun shot(name:String) {
        compose.waitForIdle(); SystemClock.sleep(300)
        val image=checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir=File(inst.targetContext.getExternalFilesDir(null),"sync-test").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    private fun waitFor(timeout:Long=15000,check:()->Boolean)=compose.waitUntil(timeout) { runCatching(check).getOrDefault(false) }

    @Test fun folderSyncRules() {
        base.deleteRecursively(); src.deleteRecursively()
        pdf(File(src,"1. KURUL/Histoloji/hucre.pdf"),1,pages=2)
        pdf(File(src,"1. KURUL/biyokimya.pdf"),2)
        pdf(File(src,"kok.pdf"),3)
        File(src,"Boş klasör").mkdirs()
        File(src,".GVNnotes-çöp").mkdirs(); pdf(File(src,".GVNnotes-çöp/atilan.pdf"),9) // Gizli/çöp taranmaz.

        // --- Arayüz: bağlı klasör uygulama açılışında taranır, klasör kutucukları ve gezinme ---
        base.mkdirs(); File(base,"sync-source.txt").writeText("file://"+src.path)
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitFor { compose.onAllNodesWithText("3 not",substring=true).fetchSemanticsNodes().isNotEmpty() }
        waitFor { compose.onAllNodes(hasTestTag("folder-1. KURUL") or hasTestTag("tree-folder-1. KURUL")).fetchSemanticsNodes().isNotEmpty() }
        compose.onAllNodes(hasTestTag("folder-Boş klasör") or hasTestTag("tree-folder-Boş klasör")).onFirst().assertExists()
        compose.onAllNodesWithTag("folder-.GVNnotes-çöp").assertCountEquals(0)
        shot("01-all-notes-folders")
        compose.onAllNodes(hasTestTag("folder-1. KURUL") or hasTestTag("tree-folder-1. KURUL")).onFirst().performClick()
        waitFor { compose.onAllNodes(hasTestTag("folder-Histoloji") or hasTestTag("tree-folder-Histoloji")).fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("biyokimya").assertExists()
        compose.onAllNodesWithText("hucre").assertCountEquals(0)
        shot("02-folder-1-kurul")
        compose.onAllNodes(hasTestTag("folder-Histoloji") or hasTestTag("tree-folder-Histoloji")).onFirst().performClick()
        waitFor { compose.onAllNodesWithText("hucre").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithContentDescription("Üst klasör").performClick()
        compose.onNodeWithContentDescription("Üst klasör").performClick()
        // Geniş ekranda klasör ağacı adası hep açıktır; dar ekranda çekmeceden açılır.
        if(compose.onAllNodesWithTag("drawer").fetchSemanticsNodes().isNotEmpty()) compose.onNodeWithTag("drawer").performClick()
        waitFor { compose.onAllNodesWithContentDescription("Histoloji, 1 not").fetchSemanticsNodes().isNotEmpty() }
        shot("03-drawer-tree")
        inst.runOnMainSync { activity.finish() }
        SystemClock.sleep(500)

        // --- Motor kuralları ---
        val first=notes()
        assertEquals(3,first.size)
        assertEquals(setOf("1. KURUL/Histoloji","1. KURUL",""),first.map { it.folderPath }.toSet())
        assertTrue(index().folders.containsAll(listOf("1. KURUL","1. KURUL/Histoloji","Boş klasör")))
        assertFalse(index().folders.any { it.startsWith(".") })
        val hucre=first.single { it.title=="hucre" }
        assertEquals(2,hucre.pageCount)

        // Tekrar tarama kopya üretmez.
        val again=sync(); assertEquals(0,again.imported); assertEquals(3,notes().size)

        // Kullanıcı düzenlemesi: hucre notuna sayfa ekle; biyokimya notunun adını değiştir.
        PageStore(library.directory(hucre.key)).let { s -> val snap=s.open().snapshot!!
            s.save(snap.copy(revision=snap.revision+1,pages=snap.pages+Page(noteId=snap.note.id,order=2))) }
        val biyo=first.single { it.title=="biyokimya" }
        PageStore(library.directory(biyo.key)).let { s -> val snap=s.open().snapshot!!
            s.save(snap.copy(revision=snap.revision+1,note=snap.note.copy(title="Biyo notlarım"))) }

        // Taşıma: aynı içerik yeni konumda → aynı not, klasör güncellenir, düzenleme korunur.
        File(src,"1. KURUL/Histoloji/hucre.pdf").renameTo(File(src,"1. KURUL/hucre.pdf"))
        // Yeniden adlandırma: kullanıcının verdiği başlık korunur.
        File(src,"1. KURUL/biyokimya.pdf").renameTo(File(src,"1. KURUL/biyokimya-2.pdf"))
        val moved=sync()
        assertEquals(2,moved.moved); assertEquals(0,moved.imported)
        val afterMove=notes()
        assertEquals(3,afterMove.size)
        afterMove.single { it.key==hucre.key }.let { assertEquals("1. KURUL",it.folderPath); assertEquals(3,it.pageCount) }
        afterMove.single { it.key==biyo.key }.let { assertEquals("Biyo notlarım",it.title) }

        // İçerik değişti → eski not korunur, yeni sürüm ayrı not.
        val kok=afterMove.single { it.title=="kok" }
        SystemClock.sleep(1100) // Dosya zamanı saniye çözünürlüklü olabilir.
        pdf(File(src,"kok.pdf"),4,pages=3)
        val changed=sync()
        assertEquals(1,changed.replaced)
        val afterChange=notes()
        assertEquals(4,afterChange.size)
        assertTrue(afterChange.any { it.key==kok.key })
        assertEquals(SourceState.REPLACED,index().entries.single { it.key==kok.key }.state)
        assertEquals(3,afterChange.single { it.title=="kok" && it.key!=kok.key }.pageCount)

        // Yarım tarama: hiçbir şey kayıp sayılmaz.
        val broken=object:SourceTree {
            override fun scan()=SourceScan(emptyList(),complete=false)
            override fun open(documentId:String):InputStream=error("unused")
        }
        val partial=sync(broken)
        assertEquals(0,partial.missing); assertFalse(partial.complete)
        assertTrue(index().entries.none { it.state==SourceState.MISSING })

        // Dosyalarım'dan silinen kaynak: not çöp kutusuna gider (yazılar çöpte korunur), durum MISSING.
        File(src,"1. KURUL/hucre.pdf").delete()
        val gone=sync()
        assertEquals(1,gone.missing)
        assertTrue(notes().none { it.key==hucre.key })
        assertTrue(library.trash().list().any { it.key==hucre.key })
        assertEquals(SourceState.MISSING,index().entries.single { it.key==hucre.key }.state)
        assertEquals(0,sync().missing) // Tekrar eşitleme ikinci kez saymaz.

        // Geri gelirse (aynı içerik, aynı yol) not çöpten döner, kopya not oluşmaz.
        pdf(File(src,"1. KURUL/hucre.pdf"),1,pages=2)
        val back=sync()
        assertEquals(0,back.imported); assertEquals(0,back.missing); assertEquals(1,back.returned)
        assertEquals(SourceState.AVAILABLE,index().entries.single { it.key==hucre.key }.state)
        assertTrue(library.trash().list().none { it.key==hucre.key })
        assertEquals(4,notes().size)

        // Kullanıcı çöpten geri alırsa (dosya hâlâ yok) not bir daha çöpe atılmaz.
        File(src,"1. KURUL/hucre.pdf").delete(); assertEquals(1,sync().missing)
        library.restoreFromTrash(hucre.key)
        assertEquals(0,sync().missing)
        assertTrue(notes().any { it.key==hucre.key })
    }
}
