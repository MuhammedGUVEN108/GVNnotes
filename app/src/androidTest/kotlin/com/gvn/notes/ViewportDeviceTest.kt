package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.Viewport
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Yalnız test-library: gerçek bırakma olayı ve üretim oturma kancası. */
@RunWith(AndroidJUnit4::class)
class ViewportDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private fun ui(block:()->Unit)=inst.runOnMainSync(block)
    private fun waitFor(check:()->Boolean) {
        val end=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end) { var ok=false; ui { ok=check() }; if(ok) return; SystemClock.sleep(25) }
        fail("Görünüm zamanında hazır olmadı")
    }
    @Test fun releaseBoundsBlankAndPdf() {
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        val library=NoteLibrary(File(inst.targetContext.filesDir,"test-library/viewport"))
        val pdf=File(inst.targetContext.cacheDir,"viewport-test.pdf")
        PdfDocument().apply {
            repeat(2) { val p=startPage(PdfDocument.PageInfo.Builder(600,800,it+1).create()); finishPage(p) }
            pdf.outputStream().use { writeTo(it) }; close()
        }
        val keys=listOf(library.create("Oturma boş sayfa",0),library.importPdf("Oturma PDF") { pdf.inputStream() })
        keys.forEachIndexed { index,key ->
            lateinit var editor:EditorState
            lateinit var view:InkPageView
            ui { editor=EditorState(library.directory(key)); view=InkPageView(activity,editor); activity.setContentView(view); view.usePen(onlyPen=true) }
            waitFor { view.refresh(); editor.ready && view.width>0 && editor.fitted }
            var fit=0f
            var zoom=0f
            var releasedScale=0f
            fun assertCentered(scale:Float) {
                assertEquals("Ölçek korunmalı",scale,editor.viewport.scale,.00001f)
                assertEquals((view.width-editor.pages.maxOf { it.width }*scale)/2,editor.viewport.x,.5f)
                val height=editor.pages.sumOf { it.height.toDouble() }.toFloat()+(editor.pages.size-1)*InkPageView.GAP
                assertEquals((view.height-height*scale)/2,editor.viewport.y,.5f)
            }
            fun finish() { SystemClock.sleep(30); waitFor { view.navigationSettled() }; inst.waitForIdleSync() }
            fun touch(action:Int,points:List<Pair<Float,Float>>,start:Long,tool:Int=MotionEvent.TOOL_TYPE_FINGER) = ui {
                val props=Array(points.size) { i -> MotionEvent.PointerProperties().apply { id=i; toolType=tool } }
                val coords=Array(points.size) { i -> MotionEvent.PointerCoords().apply { x=points[i].first; y=points[i].second; pressure=.5f; touchMajor=10f } }
                val e=MotionEvent.obtain(start,SystemClock.uptimeMillis(),action,points.size,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
                try { view.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            // Gerçek iki parmakla uzaklaştırma; parmaklar kalkana kadar minimuma zorlanmaz.
            val start=SystemClock.uptimeMillis()
            val cx=view.width/2f; val cy=view.height/2f
            touch(MotionEvent.ACTION_DOWN,listOf(cx-240 to cy),start)
            touch(MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(cx-240 to cy,cx+240 to cy),start)
            repeat(12) { i ->
                val d=240f-i*18
                touch(MotionEvent.ACTION_MOVE,listOf(cx-d to cy,cx+d to cy),start); SystemClock.sleep(20)
            }
            ui { fit=view.fitScale(); assertTrue("Hareket sırasında uzaklaştırma serbest",editor.viewport.scale<fit); releasedScale=editor.viewport.scale }
            SystemClock.sleep(150)
            touch(MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(cx-42 to cy,cx+42 to cy),start)
            touch(MotionEvent.ACTION_UP,listOf(cx-42 to cy),start)
            finish()
            ui { assertCentered(releasedScale) }
            fun release() = ui {
                val now=SystemClock.uptimeMillis()
                fun event(action:Int) {
                    val p=arrayOf(MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_FINGER })
                    val c=arrayOf(MotionEvent.PointerCoords().apply { x=100f; y=100f; pressure=.5f; touchMajor=10f })
                    val e=MotionEvent.obtain(now,now+if(action==MotionEvent.ACTION_UP) 200 else 0,action,1,p,c,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
                    view.dispatchTouchEvent(e); e.recycle()
                }
                event(MotionEvent.ACTION_DOWN); event(MotionEvent.ACTION_UP)
            }
            val small=minOf(fit*.5f,view.height/(editor.pages.sumOf { it.height.toDouble() }.toFloat()+(editor.pages.size-1)*InkPageView.GAP)*.5f)
            ui { fit=view.fitScale(); editor.viewport=Viewport(small,200f,300f); view.refresh() }
            release(); finish()
            ui {
                assertCentered(small)
                // Yakınlaştırmada sınır dışı konum düzelir, ölçek korunur.
                zoom=maxOf(fit*2,view.width/editor.pages.maxOf { it.width }*1.5f).coerceAtMost(8f)
                editor.viewport=Viewport(zoom,view.width*2f,view.height*2f); view.settleViewport()
            }
            finish()
            ui {
                assertEquals(zoom,editor.viewport.scale,.001f)
                assertEquals(20*view.resources.displayMetrics.density,editor.viewport.x,.5f)
                assertEquals(20*view.resources.displayMetrics.density,editor.viewport.y,.5f)
                editor.viewport=Viewport(zoom,-100000f,-100000f); view.settleViewport()
            }
            finish()
            ui {
                val margin=20*view.resources.displayMetrics.density
                val height=editor.pages.sumOf { it.height.toDouble() }.toFloat()+(editor.pages.size-1)*InkPageView.GAP
                assertEquals(zoom,editor.viewport.scale,.00001f)
                assertEquals(view.width-editor.pages.maxOf { it.width }*zoom-margin,editor.viewport.x,.5f)
                assertEquals(view.height-height*zoom-margin,editor.viewport.y,.5f)
                editor.viewport=editor.viewport.copy(x=-100f,y=-150f)
            }
            val inside=editor.viewport
            release(); finish()
            ui { assertEquals("Sınır içi konum korunmalı",inside,editor.viewport) }
            // Belge yalnız 10 dp darsa da tam ortalanır; 20 dp eşiğinde serbest kalmaz.
            ui {
                val narrow=(view.width-10*view.resources.displayMetrics.density)/editor.pages.maxOf { it.width }
                editor.viewport=Viewport(narrow,150f,20f); view.settleViewport()
            }
            finish()
            ui { assertEquals(5*view.resources.displayMetrics.density,editor.viewport.x,.5f) }
            ui { editor.viewport=Viewport(.15f,150f,150f); view.refresh() }
            release(); finish()
            ui { assertCentered(.15f) }
            val full=checkNotNull(inst.uiAutomation.takeScreenshot())
            val images=File(inst.targetContext.getExternalFilesDir(null),"viewport-test").apply { mkdirs() }
            File(images,"release-$index.png").outputStream().use { full.compress(Bitmap.CompressFormat.PNG,100,it) }; full.recycle()
            // Dar çalışma alanı: tek boş sayfa yatayda bırakınca da ortalanır.
            ui { view.layout(0,0,600,1200); view.fit(); fit=view.fitScale(); zoom=fit*2; editor.viewport=Viewport(fit,100f,70f); view.settleViewport() }
            finish()
            ui { assertEquals((view.width-editor.pages.maxOf { it.width }*fit)/2,editor.viewport.x,.5f) }
            if(index==0) {
                val image=checkNotNull(inst.uiAutomation.takeScreenshot())
                val dir=File(inst.targetContext.getExternalFilesDir(null),"viewport-test").apply { mkdirs() }
                File(dir,"centered.png").outputStream().use { image.compress(Bitmap.CompressFormat.PNG,100,it) }; image.recycle()
            }
            // GVNpen gezinme aracı: gerçek kalem kaydırması ve savrulma sonunda aynı sınır.
            ui { view.drawing=false; editor.viewport=Viewport(zoom,-100f,-150f) }
            val panStart=SystemClock.uptimeMillis()
            touch(MotionEvent.ACTION_DOWN,listOf(100f to 300f),panStart,MotionEvent.TOOL_TYPE_STYLUS)
            repeat(6) { i ->
                SystemClock.sleep(16)
                touch(MotionEvent.ACTION_MOVE,listOf(100f+i*70 to 300f),panStart,MotionEvent.TOOL_TYPE_STYLUS)
            }
            touch(MotionEvent.ACTION_UP,listOf(450f to 300f),panStart,MotionEvent.TOOL_TYPE_STYLUS)
            finish()
            ui {
                assertEquals(zoom,editor.viewport.scale,.001f)
                assertEquals(20*view.resources.displayMetrics.density,editor.viewport.x,.5f)
                view.drawing=true
            }
            // Yazma/silme meşgulken oturma başlatılmaz.
            ui { editor.viewport=Viewport(fit*.5f,150f,150f); editor.busy=true }
            val busy=editor.viewport
            ui { view.settleViewport() }
            SystemClock.sleep(350)
            ui { assertEquals(busy,editor.viewport); editor.busy=false }
            ui { view.settleViewport() }
            SystemClock.sleep(40)
            touch(MotionEvent.ACTION_DOWN,listOf(100f to 100f),SystemClock.uptimeMillis())
            val interrupted=editor.viewport
            SystemClock.sleep(350)
            ui { assertEquals("Yeni temas animasyonu kesmeli",interrupted,editor.viewport) }
            touch(MotionEvent.ACTION_CANCEL,listOf(100f to 100f),SystemClock.uptimeMillis())
            finish()

        }
    }
}
