package com.gvn.notes

import android.content.Intent
import android.content.pm.ActivityInfo
import android.content.res.Configuration
import android.os.SystemClock
import android.view.*
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.platform.app.InstrumentationRegistry
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.runner.lifecycle.ActivityLifecycleMonitorRegistry
import androidx.test.runner.lifecycle.Stage
import com.gvn.notes.core.Tool
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

@RunWith(AndroidJUnit4::class)
class LibraryDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private lateinit var activity:MainActivity
    private val root get()=File(inst.targetContext.filesDir,"test-library/notebooks")
    private fun find(view:View):InkPageView? {
        if(view is InkPageView)return view
        if(view is ViewGroup)for(i in 0 until view.childCount)find(view.getChildAt(i))?.let { return it }
        return null
    }
    private fun editor():Pair<InkPageView,EditorState> {
        var result:Pair<InkPageView,EditorState>?=null
        val read={
            val page=checkNotNull(find(activity.window.decorView))
            result=page to (InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(page) as EditorState)
        }
        if(android.os.Looper.myLooper()==android.os.Looper.getMainLooper())read() else inst.runOnMainSync(read)
        return result!!
    }
    private fun waitLibrary()=compose.waitUntil(15000) { runCatching {
        compose.onAllNodesWithTag("library").fetchSemanticsNodes().isNotEmpty() && compose.onAllNodesWithText("Notlar açılıyor…").fetchSemanticsNodes().isEmpty()
    }.getOrDefault(false) }
    private fun waitEditor() {
        compose.waitUntil(15000) { runCatching { compose.onAllNodesWithTag("editor").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        compose.waitUntil(15000) { var ready=false; inst.runOnMainSync { ready=editor().second.ready }; ready }
    }
    private fun create(title:String) {
        compose.onNodeWithTag("new-note").performClick()
        compose.onNodeWithTag("note-title").performTextReplacement(title)
        if(title=="Anatomi")screenshot("new-note-dialog")
        compose.onNodeWithTag("create-note").performClick()
        waitEditor()
    }
    private fun back() {
        compose.onNodeWithTag("back-to-notes").performClick()
        try { waitLibrary() } catch(e:Throwable) {
            var diagnostic=""
            inst.runOnMainSync {
                val view=find(activity.window.decorView)
                if(view!=null) {
                    val state=InkPageView::class.java.getDeclaredField("editor").apply { isAccessible=true }.get(view) as EditorState
                    diagnostic="NAVIGATION STATE ready=${state.ready} busy=${state.busy} saved=${state.savedVersion} edit=${state.editVersion} status=${state.saveStatus} problem=${state.problem} pending=${state.navigationPending} count=${state.history.items.size}"
                } else diagnostic="NAVIGATION STATE no InkPageView; finishing=${activity.isFinishing}"
            }
            val image=inst.uiAutomation.takeScreenshot()
            val dir=File(inst.targetContext.getExternalFilesDir(null),"library-test").apply { mkdirs() }
            image?.let { File(dir,"failure.png").outputStream().use { out -> it.compress(android.graphics.Bitmap.CompressFormat.PNG,100,out) }; it.recycle() }
            throw AssertionError(diagnostic,e)
        }
    }
    private fun draw(y:Float) {
        val now=SystemClock.uptimeMillis(); val (page,state)=editor()
        inst.runOnMainSync { state.tool=Tool.PEN; state.color=0xff186c84.toInt(); state.size=8f }
        listOf(400f,500f,600f).forEachIndexed { i,x ->
            inst.runOnMainSync {
                val p=state.viewport.toScreen(x,y)
                val prop=MotionEvent.PointerProperties().apply { id=0; toolType=MotionEvent.TOOL_TYPE_STYLUS }
                val c=MotionEvent.PointerCoords().apply { this.x=p.first; this.y=p.second; pressure=1f }
                val e=MotionEvent.obtain(now,now+i*20,when(i){0->MotionEvent.ACTION_DOWN;2->MotionEvent.ACTION_UP;else->MotionEvent.ACTION_MOVE},
                    1,arrayOf(prop),arrayOf(c),0,0,1f,1f,0,0,InputDevice.SOURCE_STYLUS,0)
                try { page.dispatchTouchEvent(e) } finally { e.recycle() }
            }
            SystemClock.sleep(20)
        }
        // Bilerek kaydı beklemeden Geri'ye basılır; gezinme kayıt tamamlanana kadar beklemeli.
    }
    private fun screenshot(name:String) {
        // IO tamamlandıktan sonra testin sanal Compose saatine de bir kare ver.
        // Aksi halde bitmap hazır olsa da gerçek ekran testi saati ilerlemediği için eski placeholder'ı gösterir.
        compose.waitForIdle(); SystemClock.sleep(250)
        compose.mainClock.advanceTimeBy(500); compose.waitForIdle()
        val image=checkNotNull(inst.uiAutomation.takeScreenshot())
        val directory=File(inst.targetContext.getExternalFilesDir(null),"library-test").apply { mkdirs() }
        File(directory,"$name.png").outputStream().use { image.compress(android.graphics.Bitmap.CompressFormat.PNG,100,it) }
        image.recycle()
    }
    private fun snapshots()=NoteLibrary(root).list().associate { it.title to PageStore(NoteLibrary(root).directory(it.key)).open().snapshot!! }
    private fun hashes(dir:File)=dir.listFiles()!!.filter { it.name.endsWith(".gvn") }.associate { it.name to MessageDigest.getInstance("SHA-256").digest(it.readBytes()).toList() }
    @Test fun libraryPhase() {
        val phase=InstrumentationRegistry.getArguments().getString("phase") ?: "seed"
        if(phase=="seed") {
            File(inst.targetContext.filesDir,"test-library").deleteRecursively()
            PageStore(File(root,"main")).save(persistenceFixture(1,1))
        }
        val oldHashes=hashes(File(root,"main"))
        activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        waitLibrary()
        compose.onNodeWithText("Kayıt testi").assertExists()
        if(phase=="seed") {
            screenshot("01-existing-note-listed")
            compose.onNodeWithText("Kayıt testi").performClick(); waitEditor()
            inst.runOnMainSync { assertEquals(1,editor().second.history.items.size) }
            back(); assertEquals(oldHashes,hashes(File(root,"main")))
            create("Anatomi"); inst.runOnMainSync { assertEquals(0,editor().second.history.items.size) }
            draw(750f); back()
            create("Fizyoloji"); inst.runOnMainSync { assertEquals(0,editor().second.history.items.size) }
            draw(950f); back()
            val all=snapshots()
            assertEquals(3,all.size)
            assertEquals(750f,all.getValue("Anatomi").strokes.single().data.samples.first().y,.01f)
            assertEquals(950f,all.getValue("Fizyoloji").strokes.single().data.samples.first().y,.01f)
            assertNotEquals(all.getValue("Anatomi").note.id,all.getValue("Fizyoloji").note.id)
            screenshot("02-three-independent-notes")
        } else {
            compose.onNodeWithText("Anatomi").assertExists(); compose.onNodeWithText("Fizyoloji").assertExists()
            compose.onNodeWithText("Anatomi").performClick(); waitEditor()
            inst.runOnMainSync {
                assertEquals(750f,editor().second.history.items.single().data.samples.first().y,.01f)
                assertFalse(editor().second.history.canUndo)
            }
            screenshot("03-reopened-anatomy")
            back(); compose.onNodeWithText("Fizyoloji").performClick(); waitEditor()
            inst.runOnMainSync { assertEquals(950f,editor().second.history.items.single().data.samples.first().y,.01f) }
            back(); screenshot("04-library-after-process-restart")
            val original=activity.requestedOrientation
            try {
                inst.runOnMainSync { activity.requestedOrientation=ActivityInfo.SCREEN_ORIENTATION_LANDSCAPE }
                compose.waitUntil(15000) {
                    var rotated=false
                    inst.runOnMainSync {
                        val current=ActivityLifecycleMonitorRegistry.getInstance().getActivitiesInStage(Stage.RESUMED).filterIsInstance<MainActivity>().firstOrNull()
                        if(current!=null) { activity=current; rotated=current.resources.configuration.orientation==Configuration.ORIENTATION_LANDSCAPE }
                    }
                    rotated
                }
                waitLibrary(); screenshot("05-library-landscape")
            } finally {
                inst.runOnMainSync { activity.requestedOrientation=original }
            }
        }
        assertEquals(oldHashes,hashes(File(root,"main")))
        assertTrue(NoteLibrary(root).list().all { it.preview?.isFile==true })
    }
}
