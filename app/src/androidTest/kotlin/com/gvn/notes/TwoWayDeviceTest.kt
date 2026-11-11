package com.gvn.notes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import android.os.SystemClock
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.ViewModelStore
import androidx.lifecycle.ViewModelStoreOwner
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 0.5.0: iki yönlü klasör işlemleri, resimden not ve son sayfanın hatırlanması. Gerçek LibraryState kod yolu kullanılır. */
@RunWith(AndroidJUnit4::class)
class TwoWayDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val base get()=File(inst.targetContext.filesDir,"test-library")
    private val root get()=File(base,"notebooks")
    private val src get()=File(inst.targetContext.filesDir,"test-twoway-src")
    private val library get()=NoteLibrary(root)

    private fun pdf(file:File,mark:Int) {
        file.parentFile!!.mkdirs()
        val d=PdfDocument(); val page=d.startPage(PdfDocument.PageInfo.Builder(600,800,1).create())
        page.canvas.drawRect(20f+mark*30f,20f,40f+mark*30f,40f,Paint().apply { color=Color.BLACK }); d.finishPage(page)
        file.outputStream().use { d.writeTo(it) }; d.close()
    }
    /** Yatay (400x200) JPEG; [rotate90] ise EXIF'te 90° döndürme bilgisi yazılır. */
    private fun jpeg(file:File,rotate90:Boolean) {
        file.parentFile!!.mkdirs()
        val b=Bitmap.createBitmap(400,200,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(200,60,60)) }
        file.outputStream().use { b.compress(Bitmap.CompressFormat.JPEG,90,it) }; b.recycle()
        if(rotate90) ExifInterface(file.path).apply { setAttribute(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_ROTATE_90.toString()); saveAttributes() }
    }
    private fun waitFor(timeout:Long=15000,what:String,check:()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end) { var ok=false; inst.runOnMainSync { ok=runCatching(check).getOrDefault(false) }; if(ok) return; SystemClock.sleep(50) }
        fail("Zaman aşımı: $what")
    }
    private fun state(writable:Boolean=true):LibraryState {
        var s:LibraryState?=null
        inst.runOnMainSync { s=LibraryState(library) { FileSourceTree(File(android.net.Uri.parse(it).path!!),writable) } }
        return s!!
    }
    private fun idle(s:LibraryState)=waitFor(what="boşta") { !s.syncing && !s.loading }
    private fun message(s:LibraryState,expected:String) { waitFor(what=expected) { s.actionMessage==expected }; inst.runOnMainSync { s.actionMessage=null }; idle(s) }
    private fun entry(key:String)=SyncIndex(library.syncIndexFile).load().entries.single { it.key==key }

    @Test fun twoWayImagesAndLastPage() {
        base.deleteRecursively(); src.deleteRecursively()
        pdf(File(src,"Ders/a.pdf"),1)
        jpeg(File(src,"Ders/foto.jpg"),rotate90=true)
        base.mkdirs(); File(base,"sync-source.txt").writeText("file://"+src.path)

        val s=state()
        waitFor(what="ilk eşitleme") { s.notes.size==2 && !s.syncing }
        idle(s)
        inst.runOnMainSync { assertTrue(s.writable) }
        // Resim notu: EXIF 90° uygulandı → yatay resim dikey sayfa oldu.
        val foto=library.list().single { it.title=="foto" }
        PageStore(library.directory(foto.key)).open().snapshot!!.page.let { assertTrue("EXIF yönü uygulanmadı",it.height>it.width) }
        assertEquals("Ders",foto.folderPath)
        val a=library.list().single { it.title=="a" }

        // Yeni klasör kaynakta da oluşur.
        inst.runOnMainSync { s.createFolder("","Yeni") }; message(s,"Klasör oluşturuldu.")
        assertTrue(File(src,"Yeni").isDirectory)
        inst.runOnMainSync { assertTrue("Yeni" in s.folders) }

        // Taşıma: dosya da taşınır; eşitleme geri almaz, kopya üretmez.
        inst.runOnMainSync { s.moveNote(a.key,"Yeni") }; message(s,"Not taşındı.")
        assertTrue(File(src,"Yeni/a.pdf").isFile); assertFalse(File(src,"Ders/a.pdf").exists())
        assertEquals("Yeni",library.list().single { it.key==a.key }.folderPath)
        inst.runOnMainSync { s.sync() }; idle(s)
        assertEquals(2,library.list().size); assertEquals("Yeni",library.list().single { it.key==a.key }.folderPath)

        // Ad çakışması: Ders'e yeni a.pdf → ayrı not; onu Yeni'ye taşımak reddedilir, dosyalar yerinde.
        pdf(File(src,"Ders/a.pdf"),2)
        inst.runOnMainSync { s.sync() }; idle(s)
        val a2=library.list().single { it.title=="a" && it.key!=a.key }
        inst.runOnMainSync { s.moveNote(a2.key,"Yeni") }; message(s,"Hedefte aynı adda bir öge var; işlem yapılmadı.")
        assertTrue(File(src,"Ders/a.pdf").isFile); assertEquals("Ders",library.list().single { it.key==a2.key }.folderPath)

        // Çöp: kaynak .GVNnotes-çöp'e taşınır; eşitleme yeniden içe aktarmaz.
        inst.runOnMainSync { s.moveToTrash(a.key) }; message(s,"Not ve kaynak dosyası çöpe taşındı.")
        assertFalse(File(src,"Yeni/a.pdf").exists()); assertTrue(File(src,"$TRASH_FOLDER/a.pdf").isFile)
        inst.runOnMainSync { s.sync() }; idle(s)
        assertTrue(library.list().none { it.key==a.key })
        // Çöpte aynı ad: ikinci a.pdf çöpe atılınca "a (2).pdf" olur, hiçbir dosya ezilmez.
        inst.runOnMainSync { s.moveToTrash(a2.key) }; message(s,"Not ve kaynak dosyası çöpe taşındı.")
        assertTrue(File(src,"$TRASH_FOLDER/a (2).pdf").isFile); assertTrue(File(src,"$TRASH_FOLDER/a.pdf").isFile)

        // Geri yükleme: dosya eski klasörüne özgün adıyla döner.
        inst.runOnMainSync { s.restore(a.key) }; message(s,"Not geri yüklendi.")
        assertTrue(File(src,"Yeni/a.pdf").isFile); assertFalse(File(src,"$TRASH_FOLDER/a.pdf").exists())
        assertEquals(SourceState.AVAILABLE,entry(a.key).state); assertFalse(entry(a.key).inTrash)
        inst.runOnMainSync { s.sync() }; idle(s)
        assertEquals(2,library.list().size)

        // Kalıcı silme: not gider, kullanıcının dosyası silinmez (çöp klasöründe kalır).
        inst.runOnMainSync { s.deleteForever(a2.key) }; message(s,"Not kalıcı olarak silindi.")
        assertTrue(File(src,"$TRASH_FOLDER/a (2).pdf").isFile)
        assertTrue(library.trash().list().none { it.key==a2.key })

        // Yazma izni yoksa klasörden gelen not taşınmaz (eşitleme geri alırdı); kullanıcıya söylenir.
        val readOnly=state(writable=false); idle(readOnly)
        inst.runOnMainSync { assertFalse(readOnly.writable); readOnly.moveNote(foto.key,"Yeni") }
        message(readOnly,"Kaynak klasöre yazma izni yok. Klasörü yeniden bağlayın.")
        assertTrue(File(src,"Ders/foto.jpg").isFile); assertEquals("Ders",library.list().single { it.key==foto.key }.folderPath)

        // Çoklu resim → tek not, seçim sırasıyla sayfalar; klasörsüz kök notu taşımak yalnız uygulamada yapılır.
        val r1=File(inst.targetContext.cacheDir,"r1.jpg").also { jpeg(it,false) }
        val r2=File(inst.targetContext.cacheDir,"r2.jpg").also { jpeg(it,true) }
        inst.runOnMainSync { s.importImages("İki resim","",listOf({ r1.inputStream() },{ r2.inputStream() })) }
        waitFor(what="resim notu") { !s.importing && s.selectedKey!=null }
        var multiKey=""; inst.runOnMainSync { multiKey=s.selectedKey!!; s.close() }
        idle(s)
        PageStore(library.directory(multiKey)).open().snapshot!!.let { snap ->
            assertEquals(2,snap.pages.size)
            assertTrue(snap.pages[0].width>snap.pages[0].height); assertTrue(snap.pages[1].height>snap.pages[1].width)
        }
        inst.runOnMainSync { s.moveNote(multiKey,"Yeni") }; message(s,"Not taşındı.")
        assertEquals("Yeni",library.list().single { it.key==multiKey }.folderPath)
        assertEquals(listOf("a.pdf"),File(src,"Yeni").list()!!.toList()) // Kaynak klasöre dosya eklenmedi.

        // Son sayfa: editör kapanınca kaydedilir, yeniden açılınca oradan başlar.
        val store=ViewModelStore()
        val owner=object:ViewModelStoreOwner { override val viewModelStore=store }
        val factory=object:ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST") override fun <T:ViewModel> create(modelClass:Class<T>):T=EditorState(library.directory(multiKey)) as T
        }
        inst.runOnMainSync { ViewModelProvider(owner,factory)[EditorState::class.java].currentPage=1; store.clear() }
        var reopened:EditorState?=null
        inst.runOnMainSync { reopened=EditorState(library.directory(multiKey)) }
        assertEquals(1,reopened!!.initialPage)
    }
}
