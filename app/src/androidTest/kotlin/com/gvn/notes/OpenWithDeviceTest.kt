package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfDocument
import android.net.Uri
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

/**
 * 0.19.0 "Birlikte aç": başka uygulamadan gelen VIEW isteği PDF/resmi not yapıp açar; not açıkken gelen dosya açık notu
 * kaydedip kapatarak açılır; aynı dosya ikinci kez açılınca kopya oluşmaz. Gerçek WhatsApp/Telegram/Drive denenmez;
 * debug derlemede izinli file:// yolu kullanılır.
 */
@RunWith(AndroidJUnit4::class)
class OpenWithDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:MainActivity
    private val root get()=File(inst.targetContext.filesDir,"test-library/notebooks")
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
    private fun makePdf(file:File) {
        val document=PdfDocument()
        repeat(3) { i -> document.finishPage(document.startPage(PdfDocument.PageInfo.Builder(600,800,i+1).create())) }
        file.outputStream().use { document.writeTo(it) }; document.close()
    }
    private fun makePng(file:File) {
        val bitmap=Bitmap.createBitmap(400,300,Bitmap.Config.ARGB_8888).apply { eraseColor(Color.rgb(20,120,140)) }
        file.outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }; bitmap.recycle()
    }
    private fun view(file:File,type:String)=Intent(Intent.ACTION_VIEW).setClass(inst.targetContext,MainActivity::class.java)
        .setDataAndType(Uri.fromFile(file),type).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    @Test fun openWithCreatesOpensAndReusesNotes() {
        File(inst.targetContext.filesDir,"test-library").deleteRecursively()
        val pdf=File(inst.targetContext.cacheDir,"Ders notu açılış.pdf"); makePdf(pdf)
        val png=File(inst.targetContext.cacheDir,"tahta fotoğrafı.png"); makePng(png)

        // Sistem "birlikte aç" listesinde GVNnotes görünür (PDF ve resim; görüntüle ve paylaş).
        val pm=inst.targetContext.packageManager
        fun offered(intent:Intent)=pm.queryIntentActivities(intent,0).any { it.activityInfo.name==MainActivity::class.java.name }
        val content=Uri.parse("content://ornek.saglayici/belge/1")
        assertTrue("PDF görüntüleme",offered(Intent(Intent.ACTION_VIEW).setDataAndType(content,"application/pdf")))
        assertTrue("Resim görüntüleme",offered(Intent(Intent.ACTION_VIEW).setDataAndType(content,"image/jpeg")))
        assertTrue("PDF paylaşma",offered(Intent(Intent.ACTION_SEND).setType("application/pdf")))
        assertFalse("Video sunulmamalı",offered(Intent(Intent.ACTION_VIEW).setDataAndType(content,"video/mp4")))

        // 1) Uygulama kapalıyken PDF açılır: not oluşur ve doğrudan düzenleyicide açılır.
        activity=inst.startActivitySync(view(pdf,"application/pdf").putExtra("gvnnotes.testLibrary",true)) as MainActivity
        awaitEditor(3)
        val first=NoteLibrary(root).list()
        assertEquals(1,first.size)
        assertEquals("Ders notu açılış",first[0].title)
        assertTrue(first[0].isPdf)

        // 2) Not açıkken resim gelir: aynı ekran kullanılır (singleTask), açık not kapanır, resim notu açılır.
        inst.targetContext.startActivity(view(png,"image/png"))
        awaitEditor(1)
        val second=NoteLibrary(root).list()
        assertEquals(2,second.size)
        assertTrue(second.any { it.title=="tahta fotoğrafı" && it.pageCount==1 })
        assertTrue("İlk not bozulmamalı",second.any { it.key==first[0].key && !it.unreadable && it.pageCount==3 })

        // 3) Aynı PDF yeniden açılır: kopya oluşmaz, ilk not açılır.
        inst.targetContext.startActivity(view(pdf,"application/pdf"))
        awaitEditor(3)
        assertEquals(2,NoteLibrary(root).list().size)

        // 4) Açık olan notun dosyası yeniden gelirse hiçbir şey değişmez.
        inst.targetContext.startActivity(view(pdf,"application/pdf"))
        compose.waitForIdle(); android.os.SystemClock.sleep(1500)
        awaitEditor(3)
        assertEquals(2,NoteLibrary(root).list().size)

        // 5) Okunamayan dosya: not açıkken de hata görünür, kütüphane değişmez.
        val broken=File(inst.targetContext.cacheDir,"bozuk.pdf").apply { writeText("bu bir pdf değil") }
        inst.targetContext.startActivity(view(broken,"application/pdf"))
        compose.waitUntil(15000) { runCatching { compose.onAllNodesWithText("İçe aktarılamadı").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.onNodeWithText("Tamam").performClick()
        assertEquals(2,NoteLibrary(root).list().size)
        awaitEditor(3)
    }
}
