package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Kalem imleci ve silgi halkası ekranda sabit boy: yakınlaştırma/uzaklaştırma boylarını değiştirmez (12 Kasım 2026). */
@RunWith(AndroidJUnit4::class)
class CursorSizeDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var page:InkPageView
    private lateinit var editor:EditorState
    private fun ui(a:()->Unit)=inst.runOnMainSync(a)
    private fun find(v:View):InkPageView?{ if(v is InkPageView) return v; if(v is ViewGroup) for(i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }; return null }
    private fun hover(x:Float,y:Float) = ui {
        val t=SystemClock.uptimeMillis()
        val props=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
        val c=MotionEvent.PointerCoords().apply { this.x=x; this.y=y }
        val e=MotionEvent.obtain(t,t,MotionEvent.ACTION_HOVER_MOVE,1,arrayOf(props),arrayOf(c),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
        try { page.dispatchGenericMotionEvent(e) } finally { e.recycle() }
    }
    /** İmleç katmanını çizip opak piksellerin kutusunun genişliğini döndürür. */
    private fun cursorWidth():Int {
        var w=0
        ui {
            val layer=(0 until page.childCount).map { page.getChildAt(it) }.last { it.javaClass.name.contains("cursor",true) || it===page.getChildAt(page.childCount-1) }
            val bmp=Bitmap.createBitmap(page.width,page.height,Bitmap.Config.ARGB_8888); layer.draw(Canvas(bmp))
            var min=Int.MAX_VALUE; var max=-1
            for(y in 0 until bmp.height step 2) for(x in 0 until bmp.width) if(Color.alpha(bmp.getPixel(x,y))>40) { if(x<min) min=x; if(x>max) max=x }
            bmp.recycle(); w=if(max<0) 0 else max-min
        }
        return w
    }
    private fun zoom(f:Float) = ui { val cx=page.width/2f; val cy=page.height/2f; editor.viewport=editor.viewport.zoomAt(f,cx,cy); page.refresh() }

    private fun shot(name:String) {
        val image=inst.uiAutomation.takeScreenshot() ?: return
        val dir=File(inst.targetContext.getExternalFilesDir(null),"cursor-test").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
    }
    private fun attach(activity:android.app.Activity) {
        val end=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<end) { var found:InkPageView?=null; ui { found=find(activity.window.decorView) }; if(found!=null) break; SystemClock.sleep(100) }
        ui { page=checkNotNull(find(activity.window.decorView))
            editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState }
        val end2=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end2) { var ok=false; ui { ok=editor.ready && page.width>0 }; if(ok) break; SystemClock.sleep(100) }
        SystemClock.sleep(800)
    }

    /** PDF sayfası: kullanıcı imlecin yalnız PDF/resim notlarında büyüyüp küçüldüğünü bildirdi. */
    @Test fun cursorsKeepScreenSizeOnPdf() {
        val base=File(inst.targetContext.filesDir,"test-library"); base.deleteRecursively()
        val pdf=File(inst.targetContext.cacheDir,"imlec.pdf")
        val d=android.graphics.pdf.PdfDocument(); val pg=d.startPage(android.graphics.pdf.PdfDocument.PageInfo.Builder(600,800,1).create())
        pg.canvas.drawRect(100f,100f,500f,700f,android.graphics.Paint().apply { color=Color.rgb(230,230,250) }); d.finishPage(pg)
        pdf.outputStream().use { d.writeTo(it) }; d.close()
        val key=NoteLibrary(File(base,"notebooks")).importPdf("İmleç PDF") { pdf.inputStream() }
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.waitForIdleSync(); SystemClock.sleep(1500)
        ui { androidx.lifecycle.ViewModelProvider(activity)[LibraryState::class.java].open(key) }
        attach(activity)
        measure("pdf")
        base.deleteRecursively()
    }

    /**
     * Yalnız elle: kullanıcının gerçek notunda ölçer (-e note "<başlık>"). Nota yazmaz; yakınlaştırma geçicidir.
     * Argüman yoksa atlanır (DEVICE-TEST çalıştırmasında çalışmaz).
     */
    @Test fun cursorsOnUserNote() {
        val title=InstrumentationRegistry.getArguments().getString("note") ?: return
        val lib=NoteLibrary(File(inst.targetContext.filesDir,"notebooks"))
        val key=lib.list().first { it.title==title }.key
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        inst.waitForIdleSync(); SystemClock.sleep(2000)
        ui { androidx.lifecycle.ViewModelProvider(activity)[LibraryState::class.java].open(key) }
        attach(activity); SystemClock.sleep(1500)
        val v0=editor.viewport
        try { measure("gercek") } finally { ui { editor.viewport=v0; page.refresh() } }
    }

    @Test fun cursorsKeepScreenSize() {
        File(inst.targetContext.filesDir,"test-document").deleteRecursively()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(600)
        attach(activity)
        measure("bos")
    }

    private fun measure(kind:String) {
        val results=HashMap<String,Pair<Int,Int>>()
        for(tool in listOf("pen","eraser")) {
            ui { page.usePen(tool=tool,eraser=40f); page.prefs.showCursor=true }
            val cx=page.width/2f; val cy=page.height/2f
            hover(cx,cy); SystemClock.sleep(300); val normal=cursorWidth(); shot("$kind-$tool-1x")
            zoom(2f); hover(cx,cy); SystemClock.sleep(300); val zoomed=cursorWidth(); shot("$kind-$tool-2x")
            zoom(.5f); zoom(.5f); hover(cx,cy); SystemClock.sleep(300); val out=cursorWidth(); shot("$kind-$tool-05x")
            zoom(2f)
            results[tool]=normal to zoomed
            assertTrue("$tool imleci görünmeli",normal>0)
            assertTrue("$tool imleci yakınlaşınca değişmemeli: $normal → $zoomed",kotlin.math.abs(zoomed-normal)<=normal/10+2)
            assertTrue("$tool imleci uzaklaşınca değişmemeli: $normal → $out",kotlin.math.abs(out-normal)<=normal/10+2)
        }
        android.util.Log.i("GVNnotesTest","$kind imlec genislikleri: $results")
    }
}
