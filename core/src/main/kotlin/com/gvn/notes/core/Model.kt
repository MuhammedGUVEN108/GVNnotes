package com.gvn.notes.core

import java.util.UUID

// UUID nesnenin kimliği; hash yalnız kaynak içeriğinin parmak izidir.
fun newId(): String = UUID.randomUUID().toString()
enum class SourceStatus { AVAILABLE, MISSING, UNKNOWN, TRASHED }
enum class BackgroundType { BLANK, PDF, IMAGE }
/** LASSO yalnız seçim aracıdır; kayıtlı çizgilerde bulunmaz. */
enum class Tool { PEN, HIGHLIGHTER, ERASER, LASSO }
data class Folder(val id: String = newId(), val parentId: String?, val name: String, val relativePath: String)
data class Note(val id: String = newId(), val folderId: String, val title: String)
data class SourceEntry(val id: String = newId(), val uri: String, val relativePath: String,
    val blobHash: String?, val status: SourceStatus = SourceStatus.UNKNOWN)
data class Page(val id: String = newId(), val noteId: String, val order: Int,
    val width: Float = 1000f, val height: Float = 1414f,
    val background: BackgroundType = BackgroundType.BLANK, val sourceEntryId: String? = null,
    val sourceBlobHash: String? = null, val sourcePageIndex: Int? = null,
    /** Boş sayfa şablonu kodu (0 düz, 1 çizgili, 2 kareli, 3 noktalı); kayıt biçimi 3'ten itibaren saklanır. */
    val pattern: Int = 0)
data class Sample(val x: Float, val y: Float, val pressure: Float, val tiltRadians: Float,
    val orientationRadians: Float, val elapsedMillis: Long)
data class PageStroke(val id: String = newId(), val pageId: String, val formatVersion: Int = 1,
    val tool: Tool, val color: Int, val width: Float, val samples: List<Sample>)

// Tek dönüşüm hem çizimi hem silgi/seçim koordinatını belirler.
data class Viewport(val scale: Float = 1f, val x: Float = 0f, val y: Float = 0f) {
    init { require(scale.isFinite() && scale > 0f && x.isFinite() && y.isFinite()) }
    fun toPage(screenX: Float, screenY: Float) = Pair((screenX - x) / scale, (screenY - y) / scale)
    fun toScreen(pageX: Float, pageY: Float) = Pair(pageX * scale + x, pageY * scale + y)
    fun zoomAt(factor: Float, focusX: Float, focusY: Float): Viewport {
        val s = (scale * factor).coerceIn(0.1f, 8f)
        val p = toPage(focusX, focusY)
        return Viewport(s, focusX - p.first * s, focusY - p.second * s)
    }
}

// İlk prototipte immutable listeler. Komut geçmişinin depolama formatı bundan bağımsızdır.
class History<T>(private val limit: Int = 80) {
    var items: List<T> = emptyList(); private set
    private val past = ArrayDeque<List<T>>()
    private val future = ArrayDeque<List<T>>()
    val canUndo get() = past.isNotEmpty()
    val canRedo get() = future.isNotEmpty()
    fun restore(saved: List<T>) { items=saved.toList(); past.clear(); future.clear() }
    fun replace(next: List<T>) {
        if (next == items) return
        past.addLast(items); if (past.size > limit) past.removeFirst()
        items = next.toList(); future.clear()
    }
    fun undo() { if (canUndo) { future.addLast(items); items = past.removeLast() } }
    fun redo() { if (canRedo) { past.addLast(items); items = future.removeLast() } }
}

fun segmentDistance(px: Float, py: Float, ax: Float, ay: Float, bx: Float, by: Float): Float {
    val dx = bx - ax; val dy = by - ay
    val length = dx * dx + dy * dy
    val t = if (length == 0f) 0f else (((px-ax)*dx+(py-ay)*dy)/length).coerceIn(0f,1f)
    return kotlin.math.hypot(px - ax - t*dx, py - ay - t*dy)
}

// Silgi yolu aralardaki segmentleri de sınar; hızlı harekette çizgilerin üzerinden atlamaz.
fun hitStroke(stroke: PageStroke, from: Pair<Float,Float>, to: Pair<Float,Float>, radius: Float): Boolean {
    val r = radius + stroke.width / 2
    fun distance(s: Sample) = segmentDistance(s.x,s.y,from.first,from.second,to.first,to.second)
    if (stroke.samples.any { distance(it) <= r }) return true
    return stroke.samples.zipWithNext().any { (a,b) ->
        val d1=segmentDistance(from.first,from.second,a.x,a.y,b.x,b.y)
        val d2=segmentDistance(to.first,to.second,a.x,a.y,b.x,b.y)
        val abx=b.x-a.x; val aby=b.y-a.y
        val c1=abx*(from.second-a.y)-aby*(from.first-a.x)
        val c2=abx*(to.second-a.y)-aby*(to.first-a.x)
        val ex=to.first-from.first; val ey=to.second-from.second
        val c3=ex*(a.y-from.second)-ey*(a.x-from.first)
        val c4=ex*(b.y-from.second)-ey*(b.x-from.first)
        minOf(d1,d2) <= r || (c1*c2 < 0f && c3*c4 < 0f)
    }
}
