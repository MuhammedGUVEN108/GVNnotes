package com.gvn.notes

import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 0.12.0: klasörü alt klasörleriyle silme ve çöp kutusunun 30 gün sonra kendiliğinden boşalması. Gerçek LibraryState kod yolu. */
@RunWith(AndroidJUnit4::class)
class FolderDeleteDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val base get()=File(inst.targetContext.filesDir,"test-library")
    private val root get()=File(base,"notebooks")
    private val src get()=File(inst.targetContext.filesDir,"test-folderdelete-src")
    private val library get()=NoteLibrary(root)

    private fun pdf(file:File,mark:Int) {
        file.parentFile!!.mkdirs()
        val d=PdfDocument(); val page=d.startPage(PdfDocument.PageInfo.Builder(600,800,1).create())
        page.canvas.drawRect(20f+mark*30f,20f,40f+mark*30f,40f,Paint().apply { color=Color.BLACK }); d.finishPage(page)
        file.outputStream().use { d.writeTo(it) }; d.close()
    }
    private fun waitFor(timeout:Long=15000,what:String,check:()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end) { var ok=false; inst.runOnMainSync { ok=runCatching(check).getOrDefault(false) }; if(ok) return; SystemClock.sleep(50) }
        fail("Zaman aşımı: $what")
    }
    private fun state():LibraryState {
        var s:LibraryState?=null
        inst.runOnMainSync { s=LibraryState(library) { FileSourceTree(File(android.net.Uri.parse(it).path!!)) } }
        return s!!
    }
    private fun idle(s:LibraryState)=waitFor(what="boşta") { !s.syncing && !s.loading }

    @Test fun deleteFolderTreeAndExpireTrash() {
        base.deleteRecursively(); src.deleteRecursively()
        pdf(File(src,"Mat/a.pdf"),1)
        pdf(File(src,"Mat/Alt/b.pdf"),2)
        pdf(File(src,"Mat/Alt/Derin/c.pdf"),3)
        File(src,"Mat/Bos").mkdirs()
        File(src,"Mat/oku.txt").writeText("not değil") // Not olmayan kullanıcı dosyası da korunmalı.
        pdf(File(src,"Diger/d.pdf"),4)
        base.mkdirs(); File(base,"sync-source.txt").writeText("file://"+src.path)

        val s=state()
        waitFor(what="ilk eşitleme") { s.notes.size==4 && !s.syncing }
        idle(s)
        // Uygulamada oluşturulmuş, kaynağa bağlı olmayan yazılı not da klasörle birlikte gider.
        val own=library.create("Elle",0,"Mat/Alt")
        inst.runOnMainSync { s.refresh() }; waitFor(what="5 not") { s.notes.size==5 && !s.loading }

        inst.runOnMainSync { s.deleteFolder("Mat") }
        waitFor(what="silme mesajı") { s.actionMessage=="Klasör silindi · 4 not çöp kutusunda." }
        idle(s)
        // Uygulama: yalnız Diger kalır; 4 not çöpte; klasör listesinde Mat ve altı yok.
        assertEquals(listOf("d"),library.list().map { it.title })
        assertEquals(4,library.trash().list().size)
        inst.runOnMainSync { assertTrue(s.folders.none { it=="Mat" || it.startsWith("Mat/") }) }
        // Kaynak: Mat kökten kalktı; dosyalar silinmedi, .GVNnotes-çöp içinde.
        assertFalse(File(src,"Mat").exists())
        val trash=File(src,TRASH_FOLDER)
        assertTrue(File(trash,"a.pdf").isFile && File(trash,"b.pdf").isFile && File(trash,"c.pdf").isFile)
        assertTrue("Kalan klasör ağacı çöpe taşınmalı",File(trash,"Mat/oku.txt").isFile && File(trash,"Mat/Bos").isDirectory)
        assertTrue(File(src,"Diger/d.pdf").isFile)

        // Yeniden eşitleme silinen klasörü geri getirmez.
        inst.runOnMainSync { s.sync() }; idle(s)
        assertEquals(1,library.list().size)
        inst.runOnMainSync { assertTrue(s.folders.none { it.startsWith("Mat") }) }

        // Geri yükleme: alt klasördeki not, klasörü yeniden oluşturarak yerine döner.
        val b=library.trash().list().single { it.title=="b" }
        inst.runOnMainSync { s.restore(b.key) }
        waitFor(what="geri yükleme") { s.actionMessage=="Not geri yüklendi." }; idle(s)
        assertTrue(File(src,"Mat/Alt/b.pdf").isFile)
        assertEquals("Mat/Alt",library.list().single { it.key==b.key }.folderPath)

        // 30 gün: süresi dolan çöp kalıcı silinir, dolmayan kalır.
        val old=library.trash().list().single { it.key==own }
        File(library.trash().directory(old.key),"trashed-at.txt").writeText((System.currentTimeMillis()-31L*24*60*60*1000).toString())
        inst.runOnMainSync { s.refresh() }; idle(s)
        waitFor(what="süresi dolan silindi") { library.trash().list().none { it.key==own } }
        assertEquals(2,library.trash().list().size)
        inst.runOnMainSync { assertEquals(2,s.trash.size) }
        base.deleteRecursively(); src.deleteRecursively()
    }
}
