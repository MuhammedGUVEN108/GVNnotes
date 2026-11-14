package com.gvn.notes

import android.content.Intent
import android.graphics.Color
import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import android.os.SystemClock
import androidx.compose.ui.test.*
import androidx.compose.ui.test.junit4.createEmptyComposeRule
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

/** Kütüphanede çoklu seçim (uzun bas) ve toplu taşıma/çöpe atma (14 Kasım 2026). Yalnız test-library. */
@RunWith(AndroidJUnit4::class)
class SelectionDeviceTest {
    @get:Rule val compose=createEmptyComposeRule()
    private val inst=InstrumentationRegistry.getInstrumentation()
    private val base get()=File(inst.targetContext.filesDir,"test-library")
    private val root get()=File(base,"notebooks")
    private val src get()=File(inst.targetContext.filesDir,"test-selection-src")
    private val library get()=NoteLibrary(root)

    private fun pdf(file:File,mark:Int) {
        file.parentFile!!.mkdirs()
        val d=PdfDocument(); val page=d.startPage(PdfDocument.PageInfo.Builder(600,800,1).create())
        page.canvas.drawRect(20f+mark*30f,20f,40f+mark*30f,40f,Paint().apply { color=Color.BLACK }); d.finishPage(page)
        file.outputStream().use { d.writeTo(it) }; d.close()
    }
    private fun waitFor(timeout:Long=15000,what:String,check:()->Boolean) {
        val end=SystemClock.uptimeMillis()+timeout
        while(SystemClock.uptimeMillis()<end) { var ok=false; inst.runOnMainSync { ok=runCatching(check).getOrDefault(false) }; if(ok) return; SystemClock.sleep(50) }
        fail("Zaman aşımı: $what")
    }
    private fun state(writable:Boolean=true):LibraryState {
        var s:LibraryState?=null
        inst.runOnMainSync { s=LibraryState(library) { FileSourceTree(File(android.net.Uri.parse(it).path!!),writable) } }
        return s!!
    }
    private fun idle(s:LibraryState)=waitFor(what="boşta") { !s.syncing && !s.loading }
    private fun message(s:LibraryState,expected:String) { waitFor(what=expected) { s.actionMessage==expected }; inst.runOnMainSync { s.actionMessage=null }; idle(s) }

    /** Toplu işlem mantığı: ad çakışması yalnız o notu atlar, yazma izni yoksa hiçbir şey yapılmaz, çöp geri yüklenir. */
    @Test fun batchMoveAndTrash() {
        base.deleteRecursively(); src.deleteRecursively()
        pdf(File(src,"A/x.pdf"),1); pdf(File(src,"A/y.pdf"),2); pdf(File(src,"B/y.pdf"),3)
        base.mkdirs(); File(base,"sync-source.txt").writeText("file://"+src.path)
        val own=library.create("Elle",0,"A")
        val s=state()
        waitFor(what="ilk eşitleme") { s.notes.size==4 && !s.syncing }; idle(s)
        val x=library.list().single { it.title=="x" }; val y=library.list().single { it.title=="y" && it.folderPath=="A" }

        inst.runOnMainSync { s.moveNotes(listOf(x.key,y.key,own),"B") }
        message(s,"2 not taşındı · 1 notun hedefinde aynı adlı dosya var, taşınmadı.")
        assertTrue(File(src,"B/x.pdf").isFile); assertTrue("Çakışan dosya yerinde kalmalı",File(src,"A/y.pdf").isFile)
        assertEquals("B",library.list().single { it.key==x.key }.folderPath)
        assertEquals("A",library.list().single { it.key==y.key }.folderPath)
        assertEquals("B",library.list().single { it.key==own }.folderPath)

        inst.runOnMainSync { s.trashNotes(listOf(x.key,own)) }
        message(s,"2 not çöp kutusuna taşındı.")
        assertEquals(setOf(x.key,own),library.trash().list().map { it.key }.toSet())
        assertTrue("Kaynak dosya silinmez, çöpe taşınır",File(src,"$TRASH_FOLDER/x.pdf").isFile)
        inst.runOnMainSync { s.restore(x.key) }; message(s,"Not geri yüklendi.")
        assertTrue(File(src,"B/x.pdf").isFile)

        // Yazma izni yok: kaynağı klasörde olan not varsa toplu taşıma hiçbir şeyi değiştirmez.
        val readOnly=state(writable=false); idle(readOnly)
        inst.runOnMainSync { readOnly.moveNotes(listOf(x.key,y.key),"A") }
        message(readOnly,"Kaynak klasöre yazma izni yok. Klasörü yeniden bağlayın.")
        assertTrue(File(src,"B/x.pdf").isFile); assertEquals("B",library.list().single { it.key==x.key }.folderPath)
        base.deleteRecursively(); src.deleteRecursively()
    }

    /** Arayüz: uzun bas seçer, seçimde dokunma not açmaz, tümünü seç, geri tuşu seçimi bırakır. */
    @Test fun longPressSelectionUi() {
        base.deleteRecursively(); src.deleteRecursively()
        val keys=listOf("Bir","İki","Üç").map { library.create(it,0) }
        library.create("Hedefteki",0,"Hedef")
        val activity=inst.startActivitySync(Intent(inst.targetContext,MainActivity::class.java)
            .putExtra("gvnnotes.testLibrary",true).addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)) as MainActivity
        try {
            compose.waitUntil(15000) { runCatching { compose.onAllNodesWithTag("note-${keys[0]}").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            fun waitText(text:String)=compose.waitUntil(15000) { runCatching { compose.onAllNodesWithText(text).fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.waitUntil(15000) { runCatching { compose.onAllNodesWithTag("note-${keys[0]}").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
            compose.onNodeWithTag("note-${keys[0]}").performTouchInput { longClick() }
            compose.onNodeWithTag("selection-count").assertTextEquals("1 not seçili")
            compose.onNodeWithTag("note-${keys[0]}").assertIsSelected()
            // Seçim sürerken dokunma notu açmaz, seçime ekler.
            compose.onNodeWithTag("note-${keys[1]}").performClick()
            compose.onNodeWithTag("selection-count").assertTextEquals("2 not seçili")
            compose.onAllNodesWithTag("editor").assertCountEquals(0)
            compose.onNodeWithTag("note-${keys[1]}").performClick()
            compose.onNodeWithTag("selection-count").assertTextEquals("1 not seçili")
            compose.onNodeWithTag("selection-all").performClick()
            compose.onNodeWithTag("selection-count").assertTextEquals("4 not seçili")
            // Geri tuşu önce seçimi bırakır; kütüphanede kalınır.
            inst.runOnMainSync { activity.onBackPressedDispatcher.onBackPressed() }
            compose.waitUntil(5000) { compose.onAllNodesWithTag("selection-count").fetchSemanticsNodes().isEmpty() }
            compose.onAllNodesWithTag("library").assertCountEquals(1)
            compose.onNodeWithTag("new-note").assertExists()
            // Kapat düğmesi de seçimi bırakır.
            compose.onNodeWithTag("note-${keys[2]}").performTouchInput { longClick() }
            compose.onNodeWithTag("selection-close").performClick()
            compose.waitUntil(5000) { compose.onAllNodesWithTag("selection-count").fetchSemanticsNodes().isEmpty() }
            // Düğmelerden toplu taşı: iki not Hedef'e; seçim kapanır.
            compose.onNodeWithTag("note-${keys[0]}").performTouchInput { longClick() }
            compose.onNodeWithTag("note-${keys[1]}").performClick()
            compose.onNodeWithTag("selection-move").performClick()
            compose.onNodeWithTag("move-to-Hedef").performClick()
            waitText("2 not taşındı.")
            assertEquals(listOf("Hedef","Hedef"),keys.take(2).map { k -> library.list().single { it.key==k }.folderPath })
            compose.waitUntil(5000) { compose.onAllNodesWithTag("selection-count").fetchSemanticsNodes().isEmpty() }
            // Zaten hedefte olan "taşındı" sayılmaz.
            compose.onNodeWithTag("note-${keys[0]}").performTouchInput { longClick() }
            compose.onNodeWithTag("note-${keys[2]}").performClick()
            compose.onNodeWithTag("selection-move").performClick()
            compose.onNodeWithTag("move-to-Hedef").performClick()
            waitText("1 not taşındı · 1 not zaten oradaydı.")
            // Tek not ⋯ menüsünden taşıma eski bildirimiyle sürer.
            compose.onNodeWithTag("note-menu-Üç").performClick()
            compose.onNodeWithTag("menu-move").performClick()
            compose.onNodeWithTag("move-to-root").performClick()
            waitText("Not taşındı.")
            assertEquals("",library.list().single { it.key==keys[2] }.folderPath)
            // Düğmelerden toplu çöpe atma, onayla.
            compose.onNodeWithTag("note-${keys[0]}").performTouchInput { longClick() }
            compose.onNodeWithTag("note-${keys[1]}").performClick()
            compose.onNodeWithTag("selection-trash").performClick()
            compose.onNodeWithTag("confirm-selection-trash").performClick()
            waitText("2 not çöp kutusuna taşındı.")
            assertEquals(keys.take(2).toSet(),library.trash().list().map { it.key }.toSet())
            // Seçim yokken dokunma notu açar.
            compose.onNodeWithTag("note-${keys[2]}").performClick()
            compose.waitUntil(15000) { runCatching { compose.onAllNodesWithTag("editor").fetchSemanticsNodes().isNotEmpty() }.getOrDefault(false) }
        } finally { inst.runOnMainSync { activity.finish() }; SystemClock.sleep(500); base.deleteRecursively() }
    }
}
