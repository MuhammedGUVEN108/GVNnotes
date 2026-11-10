package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.BackgroundType
import com.gvn.notes.core.Tool
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** 0.2: PDF içe aktarma, PDF arka planının gerçek ekranda çizilmesi ve ikinci sayfaya yazının doğru sayfaya kaydı. */
@RunWith(AndroidJUnit4::class)
class PdfNoteDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:MainActivity
    private val root get()=File(inst.targetContext.filesDir,"test-library/notebooks")
    private fun find(view:View):InkPageView? {
        if(view is InkPageView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun editor():Pair<InkPageView,EditorState> {
        var result:Pair<InkPageView,EditorState>?=null
        inst.runOnMainSync {
            val page=checkNotNull(find(activity.window.decorView))
            result=page to (InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState)
        }
        return result!!
    }
    private fun settle(state:EditorState) {
        compose.waitUntil(10000) { var done=false; inst.runOnMainSync { done=state.ready && !state.busy && state.savedVersion==state.editVersion }; done }
    }
    /** 3 sayfalı test PDF'i: 1. sayfada bilinen konumda siyah dikdörtgen, 3. sayfa yatay. */
    private fun makePdf(file:File) {
        val document=PdfDocument()
        listOf(600 to 800,600 to 800,800 to 600).forEachIndexed { i,(w,h) ->
            val page=document.startPage(PdfDocument.PageInfo.Builder(w,h,i+1).create())
            if(i==0) page.canvas.drawRect(100f,100f,300f,200f,Paint().apply { color=Color.BLACK })
            document.finishPage(page)
        }
        file.outputStream().use { document.writeTo(it) }; document.close()
    }
    private fun dark(bitmap:Bitmap,x:Int,y:Int)=bitmap.getPixel(x.coerceIn(0,bitmap.width-1),y.coerceIn(0,bitmap.height-1)).let {
        Color.red(it)<60 && Color.green(it)<60 && Color.blue(it)<60
    }
    private fun screenshot(name:String):Bitmap {
        compose.waitForIdle(); SystemClock.sleep(200)
        val image=checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir=File(inst.targetContext.getExternalFilesDir(null),"pdf-test").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }
        return image
    }
    /** Not sayfası yerel koordinatını ekran pikseline çevirir (sayfalar 32 birim aralıkla alt alta). */
    private fun screenOf(view:InkPageView,state:EditorState,pageIndex:Int,x:Float,y:Float):Pair<Int,Int> {
        var result=0 to 0
        inst.runOnMainSync {
            val top=state.pages.take(pageIndex).sumOf { (it.height+32f).toDouble() }.toFloat()
            val at=IntArray(2); view.getLocationOnScreen(at)
            val p=state.viewport.toScreen(x,top+y); result=(p.first+at[0]).toInt() to (p.second+at[1]).toInt()
        }
        return result
    }

    @Test fun importDrawAndReopenPdfNote() {
        File(inst.targetContext.filesDir,"test-library").deleteRecursively()
        val pdf=File(inst.targetContext.cacheDir,"gvnnotes-test.pdf"); makePdf(pdf)
        val expectedHash=MessageDigest.getInstance("SHA-256").digest(pdf.readBytes()).joinToString("") { "%02x".format(it) }
        val key=NoteLibrary(root).importPdf("PDF testi") { pdf.inputStream() }

        // Kaynak: özel kopya + doğru parmak izi + PDF başına bir sayfa; 1000 birim genişlik, oran korunur.
        val imported=PageStore(NoteLibrary(root).directory(key)).open().snapshot!!
        assertEquals(expectedHash,imported.source!!.sha256)
        assertEquals(expectedHash,MessageDigest.getInstance("SHA-256").digest(PageStore.sourceFile(NoteLibrary(root).directory(key)).readBytes()).joinToString("") { "%02x".format(it) })
        assertEquals(3,imported.pages.size)
        assertTrue(imported.pages.all { it.background==BackgroundType.PDF })
        assertEquals(listOf(0,1,2),imported.pages.map { it.sourcePageIndex })
        assertEquals(1000f*800/600,imported.pages[0].height,.01f)
        assertEquals(1000f*600/800,imported.pages[2].height,.01f)

        activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        compose.waitUntil(15000) { runCatching { compose.onAllNodesWithText("3 sayfa · PDF",substring=true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        screenshot("01-library-pdf-card").recycle()
        compose.onNodeWithText("PDF testi").performClick()
        compose.waitUntil(15000) { runCatching { compose.onAllNodesWithTag("editor").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        val (view,state)=editor()
        settle(state)
        compose.onNodeWithContentDescription("Sayfa 1 / 3").assertExists()

        // PDF'deki siyah dikdörtgenin merkezi (200,150 pt) → not birimi (333.3,250).
        val (rx,ry)=screenOf(view,state,0,1000f*200/600,1000f*150/600)
        var drawn=false
        val deadline=SystemClock.uptimeMillis()+8000
        while(!drawn && SystemClock.uptimeMillis()<deadline) {
            val shot=screenshot("02-pdf-page-1"); drawn=dark(shot,rx,ry); shot.recycle()
            if(!drawn) SystemClock.sleep(250)
        }
        assertTrue("PDF background was not rendered at its page position",drawn)
        val (wx,wy)=screenOf(view,state,0,800f,1200f)
        val blank=screenshot("03-white-area"); try { assertFalse("PDF white area is dark",dark(blank,wx,wy)) } finally { blank.recycle() }

        // İkinci sayfaya git ve yaz.
        inst.runOnMainSync { view.scrollToPage(1) }
        compose.waitUntil(5000) { runCatching { compose.onAllNodesWithContentDescription("Sayfa 2 / 3").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        inst.runOnMainSync { state.tool=Tool.PEN; state.color=0xff18222e.toInt(); state.size=8f }
        val start=SystemClock.uptimeMillis()
        listOf(400f,500f,600f).forEachIndexed { i,x ->
            inst.runOnMainSync {
                val top=state.pages[0].height+32f
                val p=state.viewport.toScreen(x,top+300f)
                val prop=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val c=MotionEvent.PointerCoords().apply { this.x=p.first; this.y=p.second; pressure=1f }
                val e=MotionEvent.obtain(start,start+i*20,when(i){0->MotionEvent.ACTION_DOWN;2->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                    1,arrayOf(prop),arrayOf(c),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { view.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            SystemClock.sleep(20)
        }
        settle(state)
        inst.runOnMainSync {
            val stroke=state.history.items.single()
            assertEquals("Stroke must belong to page 2",state.pages[1].id,stroke.data.pageId)
            assertEquals(400f,stroke.data.samples.first().x,.5f)
            assertEquals(300f,stroke.data.samples.first().y,.5f)
        }
        val (ix,iy)=screenOf(view,state,1,500f,300f)
        val inked=screenshot("04-ink-on-page-2"); try { assertTrue("Ink not visible on page 2",dark(inked,ix,iy)) } finally { inked.recycle() }

        // Sona boş sayfa ekle; kaydedilsin.
        inst.runOnMainSync { state.addBlankPage() }
        settle(state)
        val saved=PageStore(NoteLibrary(root).directory(key)).open().snapshot!!
        assertEquals(4,saved.pages.size)
        assertEquals(BackgroundType.BLANK,saved.pages[3].background)
        assertEquals(saved.pages[1].id,saved.strokes.single().data.pageId)
        assertEquals(expectedHash,saved.source!!.sha256)

        // Sayfa sıralayıcı: küçük resme dokununca o sayfaya gidilir.
        compose.onNodeWithContentDescription("Sayfa sıralayıcı").performClick()
        compose.onNodeWithTag("page-sorter").assertExists()
        compose.waitUntil(5000) { runCatching { compose.onAllNodesWithContentDescription("Sayfa 3").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.onNodeWithContentDescription("Sayfa 3").performClick()
        compose.waitUntil(5000) { runCatching { compose.onAllNodesWithContentDescription("Sayfa 3 / 4").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        SystemClock.sleep(800) // Küçük resimlerin arka planda üretilmesi için.
        screenshot("05-page-sorter").recycle()
        compose.onNodeWithContentDescription("Sayfa sıralayıcı").performClick()

        compose.onNodeWithTag("back-to-notes").performClick()
        compose.waitUntil(15000) { runCatching { compose.onAllNodesWithText("4 sayfa · PDF",substring=true).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        assertTrue(NoteLibrary(root).list().single().preview?.isFile==true)
        screenshot("06-library-after-edit").recycle()
    }
}
