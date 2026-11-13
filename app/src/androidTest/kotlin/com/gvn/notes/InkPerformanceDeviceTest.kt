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
        inst.runOnMainSync {
            v.usePen(); image=Bitmap.createBitmap(v.width,v.height,Bitmap.Config.ARGB_8888)
            val canvas=Canvas(image!!)
            fun measure(name:String) {
                val times=List(7) { val start=System.nanoTime(); v.draw(canvas); (System.nanoTime()-start)/1e6 }.sorted()
                output.put(name,JSONObject().put("median_ms",times[3]).put("max_ms",times.last()))
            }
            val page=editor.pages.first()
            fun fixture(n:Int)=List(n) { i -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,color=0xff18222e.toInt(),width=2f,
                samples=List(96) { k -> Sample(70f+k*8.8f,80f+(i%110)*10f+kotlin.math.sin(k*.35f+i)*3f,1f,0f,0f,k*8L) })) }
            editor.history.restore(fixture(20)); v.refresh(); v.draw(canvas); measure("light")
            editor.history.restore(fixture(2000)); v.refresh(); v.draw(canvas); measure("dense")
            val active=Geometry.Stroke(0xff18222e.toInt(),3f,false).apply {
                repeat(12000) { k -> points.add(Geometry.Point(100f+(k%700),400f+kotlin.math.sin(k*.04f)*70f)) }
            }
            InkPageView::class.java.getDeclaredField("active").apply { isAccessible=true }.set(v,active)
            InkPageView::class.java.getDeclaredField("contactPage").apply { isAccessible=true }.setInt(v,0)
            v.refresh(); v.draw(canvas); measure("dense_long_active")
            InkPageView::class.java.getDeclaredField("active").apply { isAccessible=true }.set(v,null)
            val pages=listOf(page)+List(99) { Page(noteId=editor.note.id,order=it+1) }
            val many=pages.flatMapIndexed { index,p -> List(if(index==0) 20 else 100) { i ->
                RenderedStroke(PageStroke(pageId=p.id,tool=Tool.PEN,color=0xff18222e.toInt(),width=2f,
                    samples=List(96) { k -> Sample(70f+k*8.8f,80f+(i%110)*10f+kotlin.math.sin(k*.35f+i)*3f,1f,0f,0f,k*8L) }))
            } }
            @Suppress("UNCHECKED_CAST")
            val pagesState=EditorState::class.java.getDeclaredField("pages\$delegate").apply { isAccessible=true }.get(editor) as MutableState<List<Page>>
            pagesState.value=pages
            editor.history.restore(many); v.refresh(); v.draw(canvas); measure("light_with_100_pages")
            accumulated=PageSnapshot(1,editor.folder,editor.note,pages,many)
            editor.history.restore(emptyList()); v.refresh()
        }
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
        } finally { saveDirectory.deleteRecursively() }
        val version=context.packageManager.getPackageInfo(context.packageName,0).versionName
        val dir=File(context.getExternalFilesDir(null),"performance").apply { mkdirs() }
        File(dir,"ink-$version.json").writeText(output.toString(2))
        println("GVNnotesPerf $version $output")
    }
}
