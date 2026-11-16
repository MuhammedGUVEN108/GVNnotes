package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/**
 * "Birlikte aç" (0.19.0) ve "Nota Ekle" (0.20.0): başka uygulamadan gelen dosya geçici olarak açılır; kullanıcı
 * "Nota Ekle" derse kütüphaneye girer ve ham hali kaynak klasöre yazılır, demezse kapanınca silinir. Gerçek
 * WhatsApp/Telegram/Drive denenmez; debug derlemede izinli file:// yolu ve düz klasör kaynağı kullanılır.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:MainActivity
    private val base get()=File(inst.targetContext.filesDir,"test-library")
    private val root get()=File(base,"notebooks")
    private val src get()=File(inst.targetContext.filesDir,"test-open-src")
    private val library get()=NoteLibrary(root)
    private fun find(view:View):InkPageView? {
        if(view is InkPageView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let { return it }
        return null
    }
    /** Ekrandaki düzenleyicinin durumu; düzenleyici yoksa null. */
    private fun editorState():EditorState? {
        var result:EditorState?=null
        inst.runOnMainSync {
            find(activity.window.decorView)?.let { page ->
                result=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState
            }
        }
        return result
    }
    /** [pages] sayfalı bir not açılıp hazır olana kadar bekler. */
    private fun awaitEditor(pages:Int):EditorState {
        compose.waitUntil(20000) {
            val state=editorState(); var ok=false
            if(state!=null) inst.runOnMainSync { ok=state.ready && state.pages.size==pages }
            ok
        }
        return editorState()!!
    }
    private fun waitFor(timeout:Long=15000,check:()->Boolean)=compose.waitUntil(timeout) { runCatching(check).getOrDefault(false) }
    private fun tag(name:String)=compose.onAllNodesWithTag(name).fetchSemanticsNodes().isNotEmpty()
    private fun makePdf(file:File,pages:Int=3) {
        val document=PdfDocument()
        repeat(pages) { i -> document.finishPage(document.startPage(PdfDocument.PageInfo.Builder(600,800,i+1).create())) }
        file.outputStream().use { document.writeTo(it) }; document.close()
    }
    private fun makePng(file:File,color:Int=Color.rgb(20,120,140)) {
        val bitmap=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply { eraseColor(color) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    private fun sha(file:File)=MessageDigest.getInstance("SHA-256").digest(file.readBytes()).joinToString("") { "%02x".format(it) }
    private fun view(file:File,type:String)=Intent(Intent.ACTION_VIEW).setClass(inst.targetContext,MainActivity::class.java)
        .setDataAndType(Uri.fromFile(file),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)
    private fun shot(name:String) {
        compose.waitForIdle(); SystemClock.sleep(400)
        val image=checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir=File(inst.targetContext.getExternalFilesDir(null),"open-with-test").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }

    @Test fun openWithStaysTemporaryUntilAddedToNotes() {
        base.deleteRecursively(); src.deleteRecursively(); src.mkdirs()
        base.mkdirs(); File(base,"sync-source.txt").writeText("file://"+src.path)
        // Gönderen uygulamalar çoğu zaman uzantısız ad verir; klasöre yazılan ad yine de taranabilir olmalı.
        val pdf=File(inst.targetContext.cacheDir,"Ders notu açılış"); makePdf(pdf)
        val png=File(inst.targetContext.cacheDir,"tahta fotoğrafı.png"); makePng(png)

        // Sistem "birlikte aç" listesinde GVNnotes görünür (PDF ve resim; görüntüle ve paylaş).
        val pm=inst.targetContext.packageManager
        fun offered(intent:Intent)=pm.queryIntentActivities(intent,0).any { it.activityInfo.name==MainActivity::class.java.name }
        val content=Uri.parse("content://ornek.saglayici/belge/1")
        assertTrue("PDF görüntüleme",offered(Intent(Intent.ACTION_VIEW).setDataAndType(content,"application/pdf")))
        assertTrue("Resim görüntüleme",offered(Intent(Intent.ACTION_VIEW).setDataAndType(content,"image/jpeg")))
        assertTrue("PDF paylaşma",offered(Intent(Intent.ACTION_SEND).setType("application/pdf")))
        assertFalse("Video sunulmamalı",offered(Intent(Intent.ACTION_VIEW).setDataAndType(content,"video/mp4")))

        // 1) Uygulama kapalıyken PDF açılır: düzenleyicide görünür ama kütüphaneye de klasöre de girmez.
        activity=inst.startActivitySync(view(pdf,"application/pdf").putExtra("gvnnotes.testLibrary",true)) as MainActivity
        awaitEditor(3)
        waitFor { tag("add-to-notes") }
        compose.onNodeWithText("Nota Ekle").assertExists()
        shot("01-nota-ekle")
        assertEquals("Eklenmeden kütüphanede görünmemeli",0,library.list().size)
        assertEquals("Eklenmeden klasöre yazılmamalı",0,src.listFiles()!!.size)

        // 2) "Nota Ekle": not kütüphaneye girer, dosyanın ham hali klasörün köküne .pdf uzantısıyla yazılır,
        //    eşitleme kaydı bu notla eşleşir; düğme kaybolur.
        compose.onNodeWithTag("add-to-notes").performClick()
        waitFor { library.list().size==1 && File(src,"Ders notu açılış.pdf").isFile }
        shot("02-eklendi")
        val first=library.list().single()
        assertEquals("Ders notu açılış",first.title)
        assertEquals(sha(pdf),sha(File(src,"Ders notu açılış.pdf")))
        waitFor { SyncIndex(library.syncIndexFile).load().entries.any { it.key==first.key && it.state==SourceState.AVAILABLE && it.sha256==sha(pdf) } }
        waitFor(8000) { !tag("add-to-notes") }

        // 3) Not açıkken resim gelir. Geçişten hemen önceki kaydedilmemiş değişiklik (yeni sayfa) kaybolmaz.
        val open=awaitEditor(3)
        inst.runOnMainSync { open.addBlankPage() }
        inst.targetContext.startActivity(view(png,"image/png"))
        awaitEditor(1)
        assertEquals("Geçişten önceki değişiklik kaydedilmeli",4,PageStore(library.directory(first.key)).open().snapshot!!.pages.size)
        waitFor { tag("add-to-notes") }
        assertEquals("Resim eklenmeden kütüphaneye girmemeli",1,library.list().size)

        // 4) Resme dokunulmadan başka dosya açılır: sorulmadan kapanır ve geçici not silinir. Aynı PDF kopya üretmez.
        inst.targetContext.startActivity(view(pdf,"application/pdf"))
        awaitEditor(4)
        waitFor { root.listFiles()!!.count { it.isDirectory }==1 }
        assertEquals(1,library.list().size)
        assertFalse("Kalıcı notta düğme olmamalı",tag("add-to-notes"))

        // 5) Resim yeniden açılır ve üzerine değişiklik yapılır: kapatırken sorulur; "Nota ekle" ile saklanır.
        inst.targetContext.startActivity(view(png,"image/png"))
        val image=awaitEditor(1)
        inst.runOnMainSync { image.addBlankPage() }
        awaitEditor(2)
        compose.onNodeWithTag("back-to-notes").performClick()
        waitFor { tag("add-before-close") }
        shot("03-kapatirken-sor")
        compose.onNodeWithTag("add-before-close").performClick()
        waitFor { tag("library") }
        waitFor { library.list().size==2 && File(src,"tahta fotoğrafı.png").isFile }
        assertEquals(sha(png),sha(File(src,"tahta fotoğrafı.png")))
        assertTrue(library.list().any { it.title=="tahta fotoğrafı" && it.pageCount==2 })

        // 6) Sonraki eşitlemeler klasöre yazılan dosyalardan ikinci not üretmez, hiçbir notu çöpe atmaz.
        SystemClock.sleep(2500) // Uygulamanın kapanıştaki kendi eşitlemesi bitsin.
        repeat(2) { FolderSync(library,SyncIndex(library.syncIndexFile).load(),FileSourceTree(src)).run() }
        assertEquals(2,library.list().size)
        assertEquals(0,library.trash().list().size)
        val entries=SyncIndex(library.syncIndexFile).load().entries
        assertEquals(2,entries.count { it.state==SourceState.AVAILABLE })
        assertTrue("Kayıt ilk eşitlemede tamamlanmalı",entries.all { it.size>0 && it.modified>0 })

        // 7) Üzerine yazılıp "Eklemeden kapat" denirse not ve dosya hiçbir yere girmez.
        val other=File(inst.targetContext.cacheDir,"geçici.png"); makePng(other,Color.rgb(200,60,60))
        inst.targetContext.startActivity(view(other,"image/png"))
        val temp=awaitEditor(1)
        inst.runOnMainSync { temp.addBlankPage() }
        awaitEditor(2)
        compose.onNodeWithTag("back-to-notes").performClick()
        waitFor { tag("discard-shared") }
        compose.onNodeWithTag("discard-shared").performClick()
        waitFor { tag("library") }
        waitFor { root.listFiles()!!.count { it.isDirectory }==2 }
        assertEquals(2,library.list().size)
        assertFalse(File(src,"geçici.png").exists())

        // 8) Okunamayan dosya: hata görünür, kütüphane değişmez.
        val broken=File(inst.targetContext.cacheDir,"bozuk.pdf").apply { writeText("bu bir pdf değil") }
        inst.targetContext.startActivity(view(broken,"application/pdf"))
        waitFor { compose.onAllNodesWithText("İçe aktarılamadı").fetchSemanticsNodes().isNotEmpty() }
        compose.onNodeWithText("Tamam").performClick()
        assertEquals(2,library.list().size)
    }

    /** Uygulama not açıkken kapanırsa kalan geçici notlar: dokunulmamış silinir, üzerine yazılmış çöp kutusuna gider. */
    @Test fun leftoverTemporaryNotesAreCleanedSafely() {
        val temp=File(inst.targetContext.filesDir,"test-open-clean"); temp.deleteRecursively()
        val lib=NoteLibrary(File(temp,"notebooks").apply { mkdirs() })
        val a=File(inst.targetContext.cacheDir,"a.pdf"); makePdf(a,1)
        val b=File(inst.targetContext.cacheDir,"b.pdf"); makePdf(b,2)
        val untouched=lib.importShared(listOf(SharedFile({ "a.pdf" },{ a.inputStream() })))
        val written=lib.importShared(listOf(SharedFile({ "b.pdf" },{ b.inputStream() })))
        assertTrue(untouched.pending && written.pending)
        assertEquals(0,lib.list().size)
        lib.rename(written.key,"yazılmış") // Kayıt revizyonu ilerler: üzerinde değişiklik var.
        lib.cleanPending()
        assertFalse(lib.directory(untouched.key).exists())
        assertFalse(lib.directory(written.key).exists())
        assertEquals(0,lib.list().size)
        assertEquals(listOf(written.key),lib.trash().list().map { it.key })
        temp.deleteRecursively()
    }

    /** Kaynak klasörün yeri değişince (Documents/GVNnotes → GVNnotes) çöptekiler dahil bütün kayıtlar yeni köke çevrilir. */
    @Test fun movedSourceFolderKeepsEntries() {
        val file=File(inst.targetContext.cacheDir,"rebase-index.json"); file.delete(); File(file.path+".new").delete()
        val old="primary:Documents/GVNnotes"; val new="primary:GVNnotes"
        fun entry(id:String?,key:String,state:SourceState)=SyncEntry(id,key,"h$key",1,2,"a","b.pdf",state)
        SyncIndex(file).save(listOf(
            entry("$old/art/5.pdf","k1",SourceState.AVAILABLE),
            entry("$old/.GVNnotes-çöp/x.pdf","k2",SourceState.TRASHED),
            entry(null,"k3",SourceState.REPLACED),
            entry("primary:Documents/GVNnotes2/y.pdf","k4",SourceState.AVAILABLE),
            entry(old,"k5",SourceState.MISSING)),listOf("art"),7)
        SyncIndex(file).rebase(old,new)
        val after=SyncIndex(file).load()
        assertEquals(listOf("$new/art/5.pdf","$new/.GVNnotes-çöp/x.pdf",null,"primary:Documents/GVNnotes2/y.pdf",new),after.entries.map { it.documentId })
        assertEquals(listOf("k1","k2","k3","k4","k5"),after.entries.map { it.key })
        assertEquals(listOf("art"),after.folders); assertEquals(7,after.lastSync)
        file.delete()
    }
}
