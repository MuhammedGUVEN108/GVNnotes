package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Color
import android.os.SystemClock
import android.view.InputDevice
import android.view.MotionEvent
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.Tool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Hardware-backed regression: screenshot pixels validate real renderer output, not just model math. */
@RunWith(AndroidJUnit4::class)
class InkPageRegressionTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var page: InkPageView
    private lateinit var editor: EditorState
    private fun ui(action:()->Unit) = inst.runOnMainSync(action)
    private fun find(view: View): InkPageView? {
        if(view is InkPageView) return view
        if(view is ViewGroup) for(i in 0 until view.childCount) find(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun settle() {
        val deadline=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<deadline) {
            var busy=true; ui { busy=editor.busy || editor.savedVersion!=editor.editVersion; assertNull(editor.problem) }
            var ready=false; ui { ready=editor.ready }
            if(ready && !busy) { inst.waitForIdleSync(); SystemClock.sleep(180); return }
            SystemClock.sleep(30)
        }
        fail("Ink ownership transfer timed out")
    }
    private fun gesture(points:List<Pair<Float,Float>>, cancel:Boolean=false) {
        val start=SystemClock.uptimeMillis()
        points.forEachIndexed { index,p ->
            ui {
                val screen=editor.viewport.toScreen(p.first,p.second)
                val props=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val coords=MotionEvent.PointerCoords().apply { x=screen.first; y=screen.second; pressure=1f; size=.02f }
                val action=when(index) { 0->MotionEvent.ACTION_DOWN; points.lastIndex->if(cancel)MotionEvent.ACTION_CANCEL else MotionEvent.ACTION_UP; else->MotionEvent.ACTION_MOVE }
                val e=MotionEvent.obtain(start,start+index*20,action,1,arrayOf(props),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            SystemClock.sleep(20)
        }
        settle()
    }
    private fun screenshot(name:String):Bitmap {
        inst.waitForIdleSync(); SystemClock.sleep(150)
        val bitmap=checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir=File(inst.targetContext.getExternalFilesDir(null),"regression").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { bitmap.compress(Bitmap.CompressFormat.PNG,100,it) }
        return bitmap
    }
    private fun hasInk(bitmap:Bitmap,p:Pair<Float,Float>):Boolean {
        val location=IntArray(2)
        var x=0; var y=0
        ui { page.getLocationOnScreen(location); val v=editor.viewport.toScreen(p.first,p.second); x=(v.first+location[0]).toInt(); y=(v.second+location[1]).toInt() }
        for(dy in -10..10) for(dx in -10..10) {
            val color=bitmap.getPixel((x+dx).coerceIn(0,bitmap.width-1),(y+dy).coerceIn(0,bitmap.height-1))
            if(Color.red(color)<100 && Color.green(color)<100 && Color.blue(color)<100) return true
        }
        return false
    }
    @Test fun completedStrokeStaysOnPageAndEraserWorksAfterZoom() {
        // Yalnız testin kendi çalışma verisini temizler; notebooks/main kullanıcı verisi değildir.
        File(inst.targetContext.filesDir,"test-document").deleteRecursively()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(600)
        ui {
            page=checkNotNull(find(activity.window.decorView))
            editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState
            editor.color=0xff18222e.toInt(); editor.size=8f; editor.tool=Tool.PEN
        }
        settle()
        gesture(listOf(400f to 650f,450f to 650f,500f to 650f,550f to 650f,600f to 650f))
        ui { assertEquals(1,editor.history.items.size) }
        assertEquals("Completed pen gesture was not autosaved",1,PageStore(File(inst.targetContext.filesDir,"test-document")).open().snapshot!!.strokes.size)
        val first=screenshot("01-finished-at-pen-position")
        try { assertTrue("Finished ink jumped away from its page coordinates",hasInk(first,500f to 650f)) } finally { first.recycle() }
        ui { editor.viewport=editor.viewport.zoomAt(1.2f,700f,900f).let { it.copy(x=it.x+25f,y=it.y+70f) }; page.refresh() }
        val zoomed=screenshot("02-after-zoom-pan")
        try { assertTrue("Ink lost page alignment after zoom/pan",hasInk(zoomed,500f to 650f)) } finally { zoomed.recycle() }
        ui { editor.tool=Tool.ERASER }
        gesture(listOf(500f to 620f,500f to 650f,500f to 680f))
        ui { assertTrue("Eraser did not remove stroke",editor.history.items.isEmpty()) }
        val erased=screenshot("03-erased")
        try { assertFalse("Erased ink is still visible",hasInk(erased,500f to 650f)) } finally { erased.recycle() }
        ui { editor.undo(); page.refresh(); assertEquals(1,editor.history.items.size) }
        val undone=screenshot("04-undo-restores")
        try { assertTrue("Undo did not restore ink at original page position",hasInk(undone,500f to 650f)) } finally { undone.recycle() }
        ui { editor.tool=Tool.PEN }
        gesture(listOf(400f to 800f,500f to 800f,600f to 800f),cancel=true)
        ui { assertEquals("Canceled gesture must not persist",1,editor.history.items.size) }
    }
}
