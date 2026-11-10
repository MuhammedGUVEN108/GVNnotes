package com.gvn.notes.core
import org.junit.Assert.*
import org.junit.Test

class ModelTest {
    @Test fun zoomKeepsFocusAndRoundTrips() {
        val v=Viewport(.8f, 33f, -80f); val p=v.toPage(400f,600f)
        val n=v.zoomAt(2.5f,400f,600f); val after=n.toPage(400f,600f)
        assertEquals(p.first,after.first,.001f); assertEquals(p.second,after.second,.001f)
        val screen=n.toScreen(p.first,p.second)
        assertEquals(400f,screen.first,.001f); assertEquals(600f,screen.second,.001f)
    }
    @Test fun equalContentDoesNotDefineNoteIdentity() {
        val a=Note(folderId="a",title="same.pdf"); val b=Note(folderId="b",title="same.pdf")
        assertNotEquals(a.id,b.id)
    }
    @Test fun newEditInvalidatesRedoAndUndoRestoresErasure() {
        val h=History<Int>(); h.replace(listOf(1)); h.replace(listOf(1,2))
        h.undo(); assertEquals(listOf(1),h.items); h.redo(); assertEquals(listOf(1,2),h.items)
        h.replace(emptyList()); h.undo(); assertEquals(listOf(1,2),h.items)
        h.replace(listOf(3)); assertFalse(h.canRedo)
    }
    @Test fun fastEraserCrossesLongStrokeBetweenSparseSamples() {
        val s=PageStroke(pageId="p",tool=Tool.PEN,color=0,width=2f,
            samples=listOf(Sample(0f,50f,1f,0f,0f,0),Sample(100f,50f,1f,0f,0f,1)))
        assertTrue(hitStroke(s,50f to 0f,50f to 100f,2f))
        assertFalse(hitStroke(s,120f to 0f,120f to 100f,2f))
        assertTrue(hitStroke(s,50f to 50f,50f to 50f,2f))
    }
}
