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

/**
 * 0.10.0 GVNpen motoru: kalem/fosforlu, alan ve tüm çizgi silgisi, S Pen yan düğmesi (yaklaşırken basılı tutma),
 * akıllı şekil (bekletme), 2B/3B sürükleme şekli, sayfayı temizleme ve sayfayı araya ekleme.
 */
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
    private fun event(down:Long,time:Long,action:Int,p:Pair<Float,Float>,button:Boolean,hover:Boolean=false):MotionEvent {
        val s=editor.viewport.toScreen(p.first,p.second)
        val props=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
        val c=MotionEvent.PointerCoords().apply { x=s.first; y=s.second; pressure=if(hover) 0f else .7f }
        return MotionEvent.obtain(down,time,action,1,arrayOf(props),arrayOf(c),0,if(button) MotionEvent.BUTTON_STYLUS_PRIMARY else 0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
    }
    /** Sayfa koordinatındaki noktalarla kalem hareketi; [holdEndMs] sonda bekletme, [button] yan düğme basılı. */
    private fun pen(points:List<Pair<Float,Float>>,holdEndMs:Long=0,button:Boolean=false) {
        val start=SystemClock.uptimeMillis(); var time=start
        if(button) { // GVNpen: düğme kalem yaklaşırken basılı tutulur; süre dolunca silgi olur.
            ui { val e=event(start,time,MotionEvent.ACTION_HOVER_MOVE,points.first(),true,hover=true); try { page.dispatchGenericMotionEvent(e) } finally { e.recycle() } }
            SystemClock.sleep(page.prefs.hold+150L); time=SystemClock.uptimeMillis()
        }
        val all=points+if(holdEndMs>0) List((holdEndMs/40).toInt()) { points.last() } else emptyList()
        all.forEachIndexed { i,p ->
            val jitter=if(i>=points.size) (i%2)*.3f else 0f
            ui {
                val action=when(i) { 0->MotionEvent.ACTION_DOWN; all.lastIndex->MotionEvent.ACTION_UP; else->MotionEvent.ACTION_MOVE }
                val e=event(time-16L*i,time,action,p.first+jitter to p.second,button)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            val step=if(i>=points.size-1) 40L else 16L
            time+=step; SystemClock.sleep(step)
        }
        if(button) { ui { val e=event(time,time,MotionEvent.ACTION_HOVER_EXIT,points.last(),false,hover=true); try { page.dispatchGenericMotionEvent(e) } finally { e.recycle() } }; SystemClock.sleep(250) }
        settle()
    }

    @Test fun penTools() {
        File(inst.targetContext.filesDir,"test-document").deleteRecursively()
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        inst.waitForIdleSync(); SystemClock.sleep(600)
        ui { page=checkNotNull(find(activity.window.decorView))
            editor=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState
            page.usePen(width=6f) }
        settle()

        // Kalem: GVNpen basınç katsayısı (0,7×1,4=0,98) örneklere yazılır.
        pen((0..20).map { 300f+it*20f to 500f })
        ui { val s=editor.history.items.single().data; assertEquals(Tool.PEN,s.tool); assertEquals(.98f,s.samples[1].pressure,.02f) }
        // Alan silgisi: ortadan dikey geçiş → iki parça.
        ui { page.usePen(tool="eraser",whole=false,eraser=20f) }
        pen((0..10).map { 500f to 440f+it*12f })
        ui {
            assertEquals("alan silgisi çizgiyi ikiye kesmeli",2,editor.history.items.size)
            assertTrue(editor.history.items.map { s -> s.data.samples.maxOf { it.x } }.sorted()[0]<500f-10f)
        }
        // Tüm çizgi silgisi.
        ui { page.usePen(tool="eraser",whole=true,eraser=20f) }
        pen((0..10).map { 400f to 440f+it*12f })
        ui { assertEquals(1,editor.history.items.size) }
        // Yan düğme: araç kalemken düğme basılıyla sürmek siler; araç değişmez.
        ui { page.usePen(); page.prefs.hold=75 }
        pen((0..10).map { 600f to 440f+it*12f },button=true)
        ui { assertEquals(0,editor.history.items.size); assertEquals("pen",page.prefs.tool) }
        // Fosforlu: tam renk, GVNpen işaretleyici olarak saklanır.
        ui { page.usePen(tool="highlighter",color=0xfffacc15.toInt()) }
        pen((0..10).map { 300f+it*20f to 300f })
        ui { val s=editor.history.items.single().data; assertEquals(Tool.HIGHLIGHTER,s.tool); assertTrue(s.color ushr 24==0xff); assertTrue(editor.history.items.single().geo.marker) }

        // Akıllı şekil: kabaca kare + uçta bekletme → 4 düz kenar; bekletmeden çizilen aynı kare şekle dönmez.
        ui { page.usePen(smart=true); page.prefs.smartDelay=300 }
        val square=ArrayList<Pair<Float,Float>>()
        for(side in 0 until 4) for(i in 0 until 16) { val u=i/16f; val bow=6f*kotlin.math.sin(Math.PI*u).toFloat()
            square+=when(side) { 0->300+300*u to 700+bow; 1->600-bow to 700+300*u; 2->600-300*u to 1000-bow; else->300+bow to 1000-300*u } }
        square+=square.first()
        pen(square,holdEndMs=900)
        ui { assertEquals("bekletilen kare 4 kenar olmalı",5,editor.history.items.size) }
        pen(square.map { it.first to it.second+150f })
        ui { assertEquals(6,editor.history.items.size) }

        // 2B şekil (dikdörtgen) ve 3B cisim (küp): sürükleyerek; küp 12 bağımsız kenar.
        ui { page.usePen(tool="rectangle"); page.prefs.shape=4f }
        pen((0..10).map { 200f+it*20f to 1150f+it*10f })
        ui { assertEquals(10,editor.history.items.size) }
        ui { page.usePen(tool="cube") }
        pen((0..10).map { 700f+it*15f to 1150f+it*15f })
        ui { assertEquals(22,editor.history.items.size) }

        // Sayfayı temizle: tek geri alma adımı.
        ui { page.clearCurrentPage() }; settle()
        ui { assertEquals(0,editor.history.items.size); editor.undo() }; settle()
        ui { assertEquals(22,editor.history.items.size) }

        // Sayfayı araya ekleme: 1. sayfanın arkasına.
        val firstId=editor.pages[0].id
        ui { editor.insertBlankPageAfter(0) }; settle()
        ui { editor.insertBlankPageAfter(0) }; settle()
        ui { assertEquals(firstId,editor.pages[0].id); assertEquals(3,editor.pages.size); assertEquals(listOf(0,1,2),editor.pages.map { it.order }) }
    }
}
