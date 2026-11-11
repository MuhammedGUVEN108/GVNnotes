package com.gvn.notes

import android.content.Intent
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

/** 0.7.0: kement ile seçme, sürükleyerek taşıma, çoğaltma, renk uygulama, silme ve geri alma. */
@RunWith(AndroidJUnit4::class)
class LassoDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var page:InkPageView
    private lateinit var editor:EditorState
    private fun ui(a:()->Unit)=inst.runOnMainSync(a)
    private fun find(v:View):InkPageView?{ if(v is InkPageView) return v; if(v is ViewGroup) for(i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }; return null }
    private fun settle() {
        val end=SystemClock.uptimeMillis()+10000
        while(SystemClock.uptimeMillis()<end) { var idle=false; ui { idle=editor.ready && !editor.busy && editor.savedVersion==editor.editVersion }; if(idle) return; SystemClock.sleep(30) }
        fail("Çizim/kayıt tamamlanmadı")
    }
    private fun pen(points:List<Pair<Float,Float>>) {
        val start=SystemClock.uptimeMillis()
        points.forEachIndexed { i,p ->
            ui {
                val s=editor.viewport.toScreen(p.first,p.second)
                val props=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val c=MotionEvent.PointerCoords().apply { x=s.first; y=s.second; pressure=.8f }
                val action=when(i) { 0->MotionEvent.ACTION_DOWN; points.lastIndex->MotionEvent.ACTION_UP; else->MotionEvent.ACTION_MOVE }
                val e=MotionEvent.obtain(start,start+i*16L,action,1,arrayOf(props),arrayOf(c),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            SystemClock.sleep(16)
        }
        settle()
    }
    @Test fun lasso() {
        File(inst.targetContext.filesDir,"test-document").deleteRecursively()
        File(inst.targetContext.filesDir,"pen.txt").delete()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(600)
        ui { page=checkNotNull(find(activity.window.decorView))
            editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState
            editor.tool=Tool.PEN; editor.smartShape=false; editor.size=4f }
        settle()
        pen((0..10).map { 300f+it*20f to 400f })   // A
        pen((0..10).map { 300f+it*20f to 800f })   // B
        val a=editor.history.items[0].data.id
        ui { editor.tool=Tool.LASSO }
        // Yalnız A'nın çevresine döngü.
        val loop=(0..36).map { k -> val t=k*Math.PI*2/36; (400f+180f*kotlin.math.cos(t).toFloat()) to (400f+80f*kotlin.math.sin(t).toFloat()) }
        pen(loop)
        ui { assertEquals(setOf(a),editor.selection) }
        // Çerçevenin içinden sürükle: +100 y.
        pen((0..10).map { 400f to 400f+it*10f })
        ui {
            val moved=editor.history.items.single { it.data.id==a }
            assertEquals(500f,moved.data.samples.first().y,1f)
            assertEquals(setOf(a),editor.selection)
        }
        ui { editor.duplicateSelection() }; settle()
        ui { assertEquals(3,editor.history.items.size); assertFalse(a in editor.selection); assertEquals(1,editor.selection.size) }
        ui { editor.recolorSelection(0xffd33f49.toInt()) }; settle()
        ui { assertEquals(0xffd33f49.toInt(),editor.history.items.single { it.data.id in editor.selection }.data.color) }
        ui { editor.deleteSelection() }; settle()
        ui { assertEquals(2,editor.history.items.size); assertTrue(editor.selection.isEmpty()) }
        ui { editor.undo() }; settle()
        ui { assertEquals(3,editor.history.items.size) }
        // Kayıttan geri okununca taşınmış konum korunur.
        val saved=PageStore(File(inst.targetContext.filesDir,"test-document")).open().snapshot!!
        assertEquals(500f,saved.strokes.single { it.data.id==a }.data.samples.first().y,1f)
    }
}
