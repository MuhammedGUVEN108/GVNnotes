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

/** 0.9.0: parmakla çizim ayarı; ikinci parmak yarım çizgiyi iptal edip kaydırmaya geçer; ayar kapalıyken parmak yazmaz. */
@RunWith(AndroidJUnit4::class)
class FingerDrawDeviceTest {
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
    private fun send(start:Long,time:Long,action:Int,points:List<Pair<Float,Float>>) = ui {
        val props=Array(points.size) { i -> MotionEvent.PointerProperties().apply { id=i; toolType=MotionEvent.TOOL_TYPE_FINGER } }
        val coords=Array(points.size) { i -> val s=editor.viewport.toScreen(points[i].first,points[i].second)
            MotionEvent.PointerCoords().apply { x=s.first; y=s.second; pressure=.5f; touchMajor=12f } }
        val e=MotionEvent.obtain(start,time,action,points.size,props,coords,0,0,1f,1f,0,0,InputDevice.SOURCE_TOUCHSCREEN,0)
        try { page.dispatchTouchEvent(e) } finally { e.recycle() }
    }
    private fun fingerLine(y:Float) {
        val start=SystemClock.uptimeMillis()
        (0..10).forEach { i -> send(start,start+i*16L,when(i) { 0->MotionEvent.ACTION_DOWN; 10->MotionEvent.ACTION_UP; else->MotionEvent.ACTION_MOVE },listOf(300f+i*20f to y)); SystemClock.sleep(16) }
        settle()
    }
    @Test fun fingerDrawing() {
        File(inst.targetContext.filesDir,"test-document").deleteRecursively()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(600)
        ui { page=checkNotNull(find(activity.window.decorView))
            editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState
            editor.tool=Tool.PEN; editor.smartShape=false; editor.fingerDraw=false }
        settle()
        val before=editor.viewport
        fingerLine(500f)
        ui { assertEquals("ayar kapalıyken parmak yazmaz",0,editor.history.items.size); assertNotEquals("parmak kaydırmalı",before,editor.viewport) }
        ui { editor.fingerDraw=true }
        fingerLine(500f)
        ui { assertEquals("ayar açıkken parmak yazar",1,editor.history.items.size) }
        // Yazarken ikinci parmak: yarım çizgi iptal, çizgi eklenmez.
        val start=SystemClock.uptimeMillis()
        send(start,start,MotionEvent.ACTION_DOWN,listOf(300f to 700f)); SystemClock.sleep(16)
        send(start,start+16,MotionEvent.ACTION_MOVE,listOf(330f to 700f)); SystemClock.sleep(16)
        send(start,start+32,MotionEvent.ACTION_POINTER_DOWN or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(330f to 700f,500f to 900f)); SystemClock.sleep(16)
        send(start,start+48,MotionEvent.ACTION_MOVE,listOf(340f to 720f,510f to 920f)); SystemClock.sleep(16)
        send(start,start+64,MotionEvent.ACTION_POINTER_UP or (1 shl MotionEvent.ACTION_POINTER_INDEX_SHIFT),listOf(340f to 720f,510f to 920f)); SystemClock.sleep(16)
        send(start,start+80,MotionEvent.ACTION_UP,listOf(340f to 720f))
        settle()
        ui { assertEquals("ikinci parmak yarım çizgiyi iptal etmeli",1,editor.history.items.size) }
    }
}
