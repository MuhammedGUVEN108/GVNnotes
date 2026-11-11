package com.gvn.notes

import android.graphics.Color
import android.os.SystemClock
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** 0.8.0: sayfa şablonu kaydı (biçim 3), eklenen sayfanın şablonu devralması, şablon değiştirme ve çizimi. */
@RunWith(AndroidJUnit4::class)
class TemplateDeviceTest {
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val root get()=File(inst.targetContext.filesDir,"test-library/notebooks")
    @Test fun templates() {
        File(inst.targetContext.filesDir,"test-library").deleteRecursively()
        val library=NoteLibrary(root)
        val key=library.create("Çizgili not",PagePattern.LINED.code)
        assertEquals(PagePattern.LINED.code,PageStore(library.directory(key)).open().snapshot!!.page.pattern)
        var editor:EditorState?=null
        inst.runOnMainSync { editor=EditorState(library.directory(key)) }
        val end=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<end) { var r=false; inst.runOnMainSync { r=editor!!.ready }; if(r) break; SystemClock.sleep(30) }
        inst.runOnMainSync { editor!!.insertBlankPageAfter(0); editor!!.setPattern(0,PagePattern.GRID.code) }
        val saveEnd=SystemClock.uptimeMillis()+5000
        while(SystemClock.uptimeMillis()<saveEnd) { var s=false; inst.runOnMainSync { s=editor!!.savedVersion==editor!!.editVersion }; if(s) break; SystemClock.sleep(30) }
        val saved=PageStore(library.directory(key)).open().snapshot!!
        assertEquals(listOf(PagePattern.GRID.code,PagePattern.LINED.code),saved.pages.map { it.pattern })
        // Çizgili sayfada 3. satır (y=114 birim) çizilir; düz sayfada aynı yer beyaz.
        val lined=renderPage(library.directory(key),saved.pages[1],emptyList(),1000)
        assertTrue("çizgili şablon çizilmedi",(0 until 4).any { d -> Color.red(lined.getPixel(500,113+d))<240 })
        lined.recycle()
        val plain=renderPage(library.directory(key),saved.pages[1].copy(pattern=0),emptyList(),1000)
        assertEquals(Color.WHITE,plain.getPixel(500,114)); plain.recycle()
    }
}
