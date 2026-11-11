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

/** 0.6.0: alan/tüm çizgi silgisi, S Pen yan düğmesi, akıllı şekil, sayfayı araya ekleme, kalem ayarlarının kalıcılığı. */
@RunWith(AndroidJUnit4::class)
class PenToolsDeviceTest {
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
    /** Sayfa koordinatındaki noktalarla kalem hareketi; [holdEndMs] sonda bekletme, [button] S Pen yan düğmesi. */
    private fun pen(points:List<Pair<Float,Float>>,holdEndMs:Long=0,button:Boolean=false) {
        val start=SystemClock.uptimeMillis(); var time=start
        val all=points+if(holdEndMs>0) List((holdEndMs/40).toInt()) { points.last() } else emptyList()
        all.forEachIndexed { i,p ->
            ui {
                val s=editor.viewport.toScreen(p.first,p.second)
                val props=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val c=MotionEvent.PointerCoords().apply { x=s.first+(if(i>=points.size) (i%2)*.3f else 0f); y=s.second; pressure=.8f }
                val action=when(i) { 0->MotionEvent.ACTION_DOWN; all.lastIndex->MotionEvent.ACTION_UP; else->MotionEvent.ACTION_MOVE }
                val e=MotionEvent.obtain(start,time,action,1,arrayOf(props),arrayOf(c),0,if(button) MotionEvent.BUTTON_STYLUS_PRIMARY else 0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            time+=if(i>=points.size-1) 40 else 16; SystemClock.sleep(if(i>=points.size-1) 40 else 16)
        }
        settle()
    }

    @Test fun penTools() {
        File(inst.targetContext.filesDir,"test-document").deleteRecursively()
        File(inst.targetContext.filesDir,"pen.txt").delete()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(600)
        ui { page=checkNotNull(find(activity.window.decorView))
            editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState
            editor.tool=Tool.PEN; editor.size=6f; editor.smartShape=false }
        settle()

        // Alan silgisi: yatay çizginin ortasından dikey geçiş → iki parça.
        pen((0..20).map { 300f+it*20f to 500f })
        ui { editor.tool=Tool.ERASER; editor.eraserWhole=false; editor.eraserSize=10f }
        pen((0..10).map { 500f to 440f+it*12f })
        ui {
            assertEquals("alan silgisi çizgiyi ikiye kesmeli",2,editor.history.items.size)
            val xs=editor.history.items.map { s -> s.data.samples.maxOf { it.x } }.sorted()
            assertTrue(xs[0]<500f-10f)
        }
        // Tüm çizgi silgisi.
        ui { editor.eraserWhole=true }
        pen((0..10).map { 400f to 440f+it*12f })
        ui { assertEquals(1,editor.history.items.size) }

        // S Pen yan düğmesi: araç kalemken düğmeyle sürmek siler, araç değişmez.
        ui { editor.tool=Tool.PEN }
        pen((0..10).map { 600f to 440f+it*12f },button=true)
        ui { assertEquals(0,editor.history.items.size); assertEquals(Tool.PEN,editor.tool) }

        // Akıllı şekil: kabaca çizilmiş kare + uçta bekletme → 4 düz kenar.
        ui { editor.smartShape=true }
        val square=ArrayList<Pair<Float,Float>>()
        for(side in 0 until 4) for(i in 0 until 16) { val u=i/16f; val bow=6f*kotlin.math.sin(Math.PI*u).toFloat()
            square+=when(side) { 0->300+300*u to 700+bow; 1->600-bow to 700+300*u; 2->600-300*u to 1000-bow; else->300+bow to 1000-300*u } }
        square+=square.first()
        pen(square,holdEndMs=900)
        ui { assertEquals("bekletilen kare 4 kenar olmalı",4,editor.history.items.size)
            assertTrue(editor.history.items.all { it.data.samples.size==2 }) }
        // Bekletmeden çizilen aynı kare şekle dönmez.
        pen(square.map { it.first to it.second+150f })
        ui { assertEquals(5,editor.history.items.size) }

        // Sayfayı araya ekleme: 1. sayfanın arkasına.
        ui { editor.insertBlankPageAfter(0) }; settle()
        ui { assertEquals(2,editor.pages.size) }
        val firstId=editor.pages[0].id
        ui { editor.insertBlankPageAfter(0) }; settle()
        ui { assertEquals(firstId,editor.pages[0].id); assertEquals(3,editor.pages.size); assertEquals(listOf(0,1,2),editor.pages.map { it.order }) }

        // Kalem ayarları kalıcı.
        ui { editor.useColor(0xff00d9f5.toInt()); editor.eraserWhole=false; editor.eraserSize=22f; editor.savePen() }
        var reopened:EditorState?=null
        ui { reopened=EditorState(File(inst.targetContext.filesDir,"test-document")) }
        assertEquals(0xff00d9f5.toInt(),reopened!!.color)
        assertEquals(listOf(0xff00d9f5.toInt()),reopened!!.customColors)
        assertFalse(reopened!!.eraserWhole); assertEquals(22f,reopened!!.eraserSize,.01f); assertTrue(reopened!!.smartShape)
    }
}
