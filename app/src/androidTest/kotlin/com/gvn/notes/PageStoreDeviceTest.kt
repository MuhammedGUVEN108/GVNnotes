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
    @Test fun damagedLatestFallsBackAndNoValidSlotFailsWithoutOverwrite() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-recovery-test")
        dir.deleteRecursively()
        val store=PageStore(dir); store.save(persistenceFixture(1,1)); store.save(persistenceFixture(2,2))
        val latest=File(dir,"document-a.gvn"); latest.writeBytes(latest.readBytes().copyOf(30))
        val recovered=PageStore(dir).open()
        assertTrue(recovered.recovered); assertEquals(1L,recovered.snapshot!!.revision)
        assertEquals(1,recovered.snapshot.strokes.size)
        val other=File(dir,"document-b.gvn"); other.writeBytes(byteArrayOf(7,8,9))
        val before=latest.readBytes(); val beforeOther=other.readBytes()
        assertTrue(runCatching { PageStore(dir).open() }.isFailure)
        assertArrayEquals(before,latest.readBytes()); assertArrayEquals(beforeOther,other.readBytes())
    }
    @Test fun failedCommitDoesNotReportSuccessOrDestroyPreviousSnapshot() {
        val dir=File(InstrumentationRegistry.getInstrumentation().targetContext.cacheDir,"store-failed-commit-test")
        dir.deleteRecursively()
        val store=PageStore(dir); store.save(persistenceFixture(1,1))
        // Sonraki slotun hedefini klasör yaparak gerçek dosya commit hatası üret.
        File(dir,"document-a.gvn").apply { mkdirs(); File(this,"blocker").writeText("test-owned commit blocker") }
        assertTrue(runCatching { store.save(persistenceFixture(2,2)) }.isFailure)
        val restored=PageStore(dir).open()
        assertEquals(1L,restored.snapshot!!.revision); assertEquals(1,restored.snapshot.strokes.size)
    }
}
