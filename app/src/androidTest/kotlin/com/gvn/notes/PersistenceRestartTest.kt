package com.gvn.notes

import android.content.Intent
import android.graphics.Color
import android.os.SystemClock
import android.view.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.Tool
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** DEVICE-TEST.ps1 her fazın arasında gerçek am force-stop uygular. */
@RunWith(AndroidJUnit4::class)
class PersistenceRestartTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var page:InkPageView
    private lateinit var editor:EditorState
    private fun ui(action:()->Unit)=inst.runOnMainSync(action)
    private fun find(v:View):InkPageView? {
        if(v is InkPageView)return v
        if(v is ViewGroup)for(i in 0 until v.childCount)find(v.getChildAt(i))?.let { return it }
        return null
    }
    private fun waitSaved() {
        val deadline=SystemClock.uptimeMillis()+15000
        while(SystemClock.uptimeMillis()<deadline) {
            var saved=false; var failure:String?=null
            ui { saved=editor.ready && !editor.busy && editor.savedVersion==editor.editVersion; failure=editor.problem }
            assertNull(failure)
            if(saved) { inst.waitForIdleSync(); SystemClock.sleep(180); return }
            SystemClock.sleep(30)
        }
        fail("Autosave did not reach durable version")
    }
    private fun hasInkAt500650(name:String):Boolean {
        inst.waitForIdleSync(); SystemClock.sleep(180)
        val shot=checkNotNull(inst.uiAutomation.takeScreenshot())
        val dir=File(inst.targetContext.getExternalFilesDir(null),"persistence-test").apply { mkdirs() }
        File(dir,"$name.png").outputStream().use { shot.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        val at=IntArray(2); var x=0; var y=0
        ui { page.getLocationOnScreen(at); val p=editor.viewport.toScreen(500f,650f); x=(p.first+at[0]).toInt(); y=(p.second+at[1]).toInt() }
        try {
            for(dx in -10..10)for(dy in -10..10) {
                val c=shot.getPixel(x+dx,y+dy)
                if(Color.red(c)<100&&Color.green(c)<100&&Color.blue(c)<100)return true
            }
            return false
        } finally { shot.recycle() }
    }
    private fun erase() {
        val now=SystemClock.uptimeMillis()
        ui { page.usePen(tool="eraser") }
        listOf(620f,650f,680f).forEachIndexed { i,y ->
            ui {
                val p=editor.viewport.toScreen(500f,y)
                val prop=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val coords=MotionEvent.PointerCoords().apply { x=p.first; this.y=p.second; pressure=1f }
                val e=MotionEvent.obtain(now,now+i*20,when(i){0->MotionEvent.ACTION_DOWN;2->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                    1,arrayOf(prop),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            SystemClock.sleep(20)
        }
        waitSaved()
    }
    private fun drawLine(y:Float) {
        val now=SystemClock.uptimeMillis()
        listOf(400f,500f,600f).forEachIndexed { i,x ->
            ui {
                val p=editor.viewport.toScreen(x,y)
                val prop=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val coords=MotionEvent.PointerCoords().apply { this.x=p.first; this.y=p.second; pressure=if(i==0).5f else if(i==1)1f else .7f }
                val e=MotionEvent.obtain(now,now+i*20,when(i){0->MotionEvent.ACTION_DOWN;2->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                    1,arrayOf(prop),arrayOf(coords),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            SystemClock.sleep(20)
        }
        waitSaved()
    }
    @Test fun processRestartPhase() {
        val phase=InstrumentationRegistry.getArguments().getString("phase") ?: "seed"
        val dir=File(inst.targetContext.filesDir,"test-document")
        if(phase=="seed") { dir.deleteRecursively(); PageStore(dir).save(persistenceFixture(0,0)) }
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(400)
        ui { page=checkNotNull(find(activity.window.decorView)); editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState }
        waitSaved()
        ui { assertEquals("persistence-page",editor.page.id) }
        when(phase) {
            "seed" -> {
                ui { page.usePen(width=8f) }
                drawLine(650f)
                ui { page.usePen(tool="highlighter",color=0xffe5a900.toInt()) }
                drawLine(850f)
                ui { assertEquals(2,editor.history.items.size) }
                assertTrue(hasInkAt500650("01-seeded"))
            }
            "verify" -> {
                ui { assertEquals(2,editor.history.items.size); assertEquals(0.7f,editor.history.items[0].data.samples[0].pressure,.001f) /* GVNpen: basınç×1,4 */ }
                assertTrue("Reloaded ink no longer aligns with its page",hasInkAt500650("02-reopened"))
                erase(); ui { assertEquals(1,editor.history.items.size) }
                ui { editor.undo(); page.refresh() }; waitSaved(); ui { assertEquals(2,editor.history.items.size) }
                ui { editor.redo(); page.refresh() }; waitSaved(); ui { assertEquals(1,editor.history.items.size) }
                assertFalse(hasInkAt500650("03-erased-and-redone"))
            }
            "deleted" -> {
                ui { assertEquals(1,editor.history.items.size); assertEquals(Tool.HIGHLIGHTER,editor.history.items.single().data.tool) }
                assertFalse("Erased stroke returned after force-stop/relaunch",hasInkAt500650("04-deletion-survives-restart"))
            }
            else -> fail("Unknown phase")
        }
    }
}
