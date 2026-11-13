package com.gvn.notes

import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.*
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File

internal fun persistenceFixture(revision:Long,count:Int):PageSnapshot {
    val folder=Folder("persistence-folder",null,"Notlar","")
    val note=Note("persistence-note",folder.id,"Kayıt testi")
    val page=Page("persistence-page",note.id,0)
    val strokes=List(count) { index ->
        val y=650f+index*200f
        val samples=listOf(Sample(400f,y,.5f,.2f,.3f,0),Sample(500f,y,1f,.2f,.3f,20),Sample(600f,y,.7f,.2f,.3f,40))
        val tool=if(index==0)Tool.PEN else Tool.HIGHLIGHTER
        val color=if(index==0)0xff18222e.toInt() else 0xffe5a900.toInt()
        val size=if(index==0)8f else 24f
        RenderedStroke(PageStroke("saved-stroke-$index",page.id,1,tool,color,size,samples))
    }
    return PageSnapshot(revision,folder,note,page,strokes)
}

@RunWith(AndroidJUnit4::class)
class PageStoreDeviceTest {
    @Test fun exactStrokeMetadataAndInkInputsSurviveReload() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-roundtrip-test")
        dir.deleteRecursively()
        val original=persistenceFixture(9,2)
        PageStore(dir).save(original)
        val loaded=checkNotNull(PageStore(dir).open().snapshot)
        assertEquals(original.folder,loaded.folder); assertEquals(original.note,loaded.note); assertEquals(original.page,loaded.page)
        assertEquals(original.strokes.map { it.data },loaded.strokes.map { it.data })
        // GVNpen çizim görünümü örneklerden aynen kurulur (basınç katsayısı dahil).
        original.strokes.zip(loaded.strokes).forEach { (before,after) ->
            assertEquals(before.geo.points.size,after.geo.points.size)
            before.geo.points.zip(after.geo.points).forEach { (a,b) -> assertEquals(a.x,b.x,.001f); assertEquals(a.y,b.y,.001f); assertEquals(a.p,b.p,.001f) }
            assertEquals(before.geo.marker,after.geo.marker)
        }
    }
    @Test fun damagedDatabaseFailsWithoutDeletingTheFile() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-corruption-test")
        dir.deleteRecursively(); PageStore(dir).save(persistenceFixture(1,1))
        val db=PageStore.databaseFile(dir); db.writeBytes(ByteArray(256) { 7 })
        val before=db.readBytes()
        assertTrue(runCatching { PageStore(dir).open() }.isFailure)
        assertArrayEquals(before,db.readBytes())
    }
    @Test fun failedTransactionPreservesPreviousRevision() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-failed-commit-test")
        dir.deleteRecursively()
        val store=PageStore(dir); store.save(persistenceFixture(1,1))
        PageInkDatabase.connection(PageStore.databaseFile(dir),true).use {
            it.execSQL("CREATE TRIGGER reject_test BEFORE INSERT ON strokes WHEN NEW.id='saved-stroke-1' BEGIN SELECT RAISE(ABORT,'test rollback'); END")
        }
        assertTrue(runCatching { store.save(persistenceFixture(2,2)) }.isFailure)
        val restored=PageStore(dir).open().snapshot!!
        assertEquals(1L,restored.revision); assertEquals(1,restored.strokes.size)
    }
    @Test fun incompleteSampleSetCannotBeReadAsValidInk() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-incomplete-test")
        dir.deleteRecursively(); PageStore(dir).save(persistenceFixture(1,1))
        PageInkDatabase.connection(PageStore.databaseFile(dir),true).use { db ->
            db.execSQL("UPDATE sample_sets SET count=count+1")
            db.execSQL("UPDATE strokes SET count=count+1")
        }
        SampleMemory.clearUnpinned()
        val samples=PageStore(dir).open().snapshot!!.strokes.first().data.samples
        assertTrue(runCatching { samples.last() }.isFailure)
    }
    @Test fun lazyPagesAndIncrementalAppendDoNotRewriteOtherInk() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-paged-test")
        dir.deleteRecursively()
        val base=persistenceFixture(1,1)
        val pages=List(100) { base.page.copy(id="page-$it",order=it) }
        val store=PageStore(dir)
        // Sayfaları artımlı doldur: toplam 30.000 çizgi / 2.880.000 nokta (eski sınırların üstü).
        val history=PageHistory()
        for(page in pages) {
            val strokes=List(300) { n -> RenderedStroke(PageStroke("${page.id}-$n",page.id,1,Tool.PEN,0xff123456.toInt(),3f,
                List(96) { k -> Sample(10f+k,20f+n,.7f,0f,0f,k.toLong()) })) }
            history.replacePage(page.id,strokes)
            store.save(PageSnapshot(1L+page.order,base.folder,base.note,pages,history.items,null,history.grouped))
        }
        SampleMemory.clearUnpinned()
        val start=System.nanoTime()
        val reopened=PageStore(dir); val loaded=reopened.open().snapshot!!
        android.util.Log.i("GVNnotesPaged","metadataOpenMs=${(System.nanoTime()-start)/1e6}")
        assertEquals(30_000,loaded.strokes.size)
        assertFalse((loaded.strokes.first().data.samples as SampleHandle).ready())
        assertFalse((loaded.strokes.last().data.samples as SampleHandle).ready())
        assertEquals(105f,loaded.strokes.last().data.samples.last().x,0f)
        val added=RenderedStroke(base.strokes.first().data.copy(id="new-stroke",pageId=pages[50].id))
        // copy(strokes=...) geçerli yeni listeyi kullanmalı; eski pageInk haritasını kullanmamalı.
        reopened.save(loaded.copy(revision=101,strokes=loaded.strokes+added))
        assertEquals(3L,reopened.lastWrittenPoints); assertEquals(1,reopened.lastWrittenPages)
        val changed=reopened.open().snapshot!!
        reopened.save(changed.copy(revision=102,note=changed.note.copy(title="Yeni başlık")))
        assertEquals(0L,reopened.lastWrittenPoints); assertEquals(0,reopened.lastWrittenPages)
        assertEquals(30_001,PageStore(dir).open().snapshot!!.strokes.size)
    }
}
