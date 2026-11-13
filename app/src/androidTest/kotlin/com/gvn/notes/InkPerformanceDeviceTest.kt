package com.gvn.notes

import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.*
import com.gvn.notes.pen.Geometry
import org.json.JSONObject
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import androidx.compose.runtime.MutableState

/** Yalnız sentetik test notunda aynı iş yükünü sürümler arasında ölçer. */
@RunWith(AndroidJUnit4::class)
class InkPerformanceDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private fun find(v:View):InkPageView? {
        if(v is InkPageView) return v
        if(v is ViewGroup) for(i in 0 until v.childCount) find(v.getChildAt(i))?.let { return it }
        return null
    }
    @Test fun denseInkDrawBenchmark() {
        val context=inst.targetContext
        val activity=inst.startActivitySync(Intent(context,MainActivity::class.java)
            .putExtra("gvnnotes.testDocument",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
        var view:InkPageView?=null; var state:EditorState?=null
        repeat(100) {
            inst.runOnMainSync {
                view=find(activity.window.decorView)
                state=view?.let { InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(it) as EditorState }
            }
            if(state?.ready==true) return@repeat
            SystemClock.sleep(20)
        }
        val v=checkNotNull(view); val editor=checkNotNull(state); assertTrue(editor.ready)
        val output=JSONObject()
        var image:Bitmap?=null
        var accumulated:PageSnapshot?=null
        inst.runOnMainSync { v.usePen(); image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888) }
        val canvas=Canvas(image!!)
        fun settle() {
            val deadline=SystemClock.uptimeMillis()+30_000
            var settled=false
            while(SystemClock.uptimeMillis()<deadline) {
                inst.runOnMainSync { v.draw(canvas); settled=v.inkSettled() && editor.pageReady(editor.pages[editor.currentPage].id) }
                if(settled) return
                SystemClock.sleep(16)
            }
            fail("Mürekkep hazır olmadı; boş kare ölçülmedi")
        }
        fun measure(name:String) {
            settle()
            val times=List(7) {
                var ms=0.0
                inst.runOnMainSync { val start=System.nanoTime(); v.draw(canvas); ms=(System.nanoTime()-start)/1e6 }
                ms
            }.sorted()
            output.put(name,JSONObject().put("median_ms",times[3]).put("max_ms",times.last()))
        }
        val page=editor.pages.first()
            fun fixture(n:Int)=List(n) { i -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,color=0xff18222e.toInt(),width=2f,
                samples=List(96) { k -> Sample(70f+k*8.8f,80f+(i%110)*10f+kotlin.math.sin(k*.35f+i)*3f,1f,0f,0f,k*8L) })) }
        inst.runOnMainSync { editor.history.restore(fixture(20)); v.refresh()
        }; measure("light"); inst.runOnMainSync {
            editor.history.restore(fixture(2000)); v.refresh()
        }; measure("dense"); inst.runOnMainSync {
            val active=Geometry.Stroke(0xff18222e.toInt(),3f,false).apply {
                repeat(12000) { k -> points.add(Geometry.Point(100f+(k%700),400f+kotlin.math.sin(k*.04f)*70f)) }
            }
            InkPageView::class.java.getDeclaredField("active").apply { isAccessible=true }.set(v,active)
            InkPageView::class.java.getDeclaredField("contactPage").apply { isAccessible=true }.setInt(v,0)
            v.refresh()
        }; measure("dense_long_active"); inst.runOnMainSync {
            InkPageView::class.java.getDeclaredField("active").apply { isAccessible=true }.set(v,null)
            val pages=listOf(page)+List(99) { Page(noteId=editor.note.id,order=it+1) }
            val many=pages.flatMapIndexed { index,p -> List(if(index==0) 20 else 100) { i ->
                RenderedStroke(PageStroke(pageId=p.id,tool=Tool.PEN,color=0xff18222e.toInt(),width=2f,
                    samples=List(96) { k -> Sample(70f+k*8.8f,80f+(i%110)*10f+kotlin.math.sin(k*.35f+i)*3f,1f,0f,0f,k*8L) }))
            } }
            @Suppress("UNCHECKED_CAST")
            val pagesState=EditorState::class.java.getDeclaredField("pages\$delegate").apply { isAccessible=true }.get(editor) as MutableState<List<Page>>
            pagesState.value=pages
            editor.history.restore(many); v.refresh()
        }; measure("light_with_100_pages"); inst.runOnMainSync {
            val pages=editor.pages; val many=editor.history.items
            accumulated=PageSnapshot(1,editor.folder,editor.note,pages,many)
            editor.history.restore(emptyList()); v.refresh()
        }
        inst.runOnMainSync { editor.history.restore(List(2000) { i -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,color=0xff18222e.toInt(),width=2f,samples=List(96) { k -> Sample(70f+k*8.8f,80f+(i%110)*10f,1f,0f,0f,k*8L) })) }); v.refresh() }
        settle()
        val zoomTimes=ArrayList<Double>()
        repeat(80) { n ->
            inst.runOnMainSync {
                editor.viewport=editor.viewport.copy(scale=.7f+(n%40)*.04f)
                val start=System.nanoTime(); v.refresh(); v.draw(canvas); zoomTimes+=(System.nanoTime()-start)/1e6
            }
            SystemClock.sleep(8)
        }
        zoomTimes.sort(); output.put("zoom_main_frame",JSONObject().put("median_ms",zoomTimes[40]).put("max_ms",zoomTimes.last()))
        settle()
        inst.runOnMainSync { editor.history.restore(emptyList()); v.refresh() }
        image?.recycle()
        val saveDirectory=File(context.cacheDir,"performance-save").apply { mkdirs() }
        try {
            val store=PageStore(saveDirectory); val snapshot=checkNotNull(accumulated)
            var start=System.nanoTime(); store.save(snapshot)
            output.put("save_first_ms",(System.nanoTime()-start)/1e6)
            start=System.nanoTime(); store.save(snapshot.copy(revision=2))
            output.put("save_cached_ms",(System.nanoTime()-start)/1e6)
            output.put("document_pages",100).put("document_samples",snapshot.strokes.sumOf { it.data.samples.size })
            start=System.nanoTime()
            val reopened=checkNotNull(store.open().snapshot)
            output.put("reopen_ms",(System.nanoTime()-start)/1e6)
            assertEquals(100,reopened.pages.size)
            assertEquals(snapshot.strokes.size,reopened.strokes.size)
            assertEquals(952320,reopened.strokes.sumOf { it.data.samples.size })
            assertEquals(snapshot.strokes.first().data,reopened.strokes.first().data)
            assertEquals(snapshot.strokes.last().data,reopened.strokes.last().data)
            inst.runOnMainSync { editor.history.restorePages(reopened.groupedInk()); SampleMemory.release(editor); SampleMemory.clearUnpinned() }
            val transitions=org.json.JSONArray()
            image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
            // Eski Canvas recycle edilen bitmap'e bağlı; geçişler için yeni çizim hedefi.
            val transitionCanvas=Canvas(image!!)
            for(index in listOf(1,2,50,51,99)) {
                var frameMs=0.0
                val startReady=SystemClock.uptimeMillis()
                inst.runOnMainSync {
                    val startFrame=System.nanoTime(); v.scrollToPage(index); v.draw(transitionCanvas); frameMs=(System.nanoTime()-startFrame)/1e6
                }
                var done=false
                while(SystemClock.uptimeMillis()-startReady<20_000) {
                    inst.runOnMainSync { v.draw(transitionCanvas); done=editor.pageReady(editor.pages[index].id) && v.inkSettled() }
                    if(done) break
                    SystemClock.sleep(16)
                }
                assertTrue("Sayfa $index hazırlanamadı",done)
                transitions.put(JSONObject().put("page",index+1).put("first_main_frame_ms",frameMs).put("ready_ms",SystemClock.uptimeMillis()-startReady))
            }
            output.put("page_transitions",transitions)
            inst.runOnMainSync { editor.history.restore(emptyList()); SampleMemory.release(editor); v.refresh() }
            image?.recycle()

        } finally { saveDirectory.deleteRecursively() }
        val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
        val dir=File(context.getExternalFilesDir(null),"performance").apply { mkdirs() }
        File(dir,"ink-$version.json").writeText(output.toString(2))
        println("GVNnotesPerf $version $output")
    }
}
