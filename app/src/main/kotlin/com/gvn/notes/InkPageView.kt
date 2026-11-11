package com.gvn.notes

import android.content.Context
import android.annotation.SuppressLint
import android.os.Build
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.graphics.Path
import android.graphics.DashPathEffect
import android.graphics.RectF
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.VelocityTracker
import android.view.View
import android.widget.FrameLayout
import android.widget.OverScroller
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.strokes.Stroke
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.input.motionprediction.MotionEventPredictor
import com.gvn.notes.core.*
import kotlin.math.max
import kotlin.math.min

/**
 * Sayfa içi yüzey. WindowManager, overlay servisi veya dosya erişimi yoktur.
 *
 * Belge koordinatı: sayfalar alt alta, aralarında [GAP] boşlukla dizilir. Görünüm (editor.viewport)
 * belge koordinatını ekrana taşır. Çizgiler kendi sayfalarının yerel koordinatında saklanır; böylece
 * sayfa eklemek veya sıralamak yazıların yerini değiştirmez.
 */
@SuppressLint("ViewConstructor") // Compose factory supplies the editor; this view is not inflated from XML.
class InkPageView(context: Context, private val editor: EditorState) : FrameLayout(context) {
    private var live: InProgressStrokesView = newLiveView()
    private val renderer = CanvasStrokeRenderer.create()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val viewMatrix = Matrix()
    private val pageMatrix = Matrix()
    private val rect = RectF()
    private val predictor = MotionEventPredictor.newInstance(this)
    private val density = resources.displayMetrics.density
    private var pointerId = -1
    private var strokeId: InProgressStrokeId? = null
    private var current: Pending? = null
    private val pending = linkedMapOf<InProgressStrokeId, Pending>()
    private var erasePreview: List<RenderedStroke>? = null
    private var lastErase: Pair<Float,Float>? = null
    private var fingerFocus: Pair<Float,Float>? = null
    private var fingerCount = 0
    private var palmGesture = false
    private var velocity: VelocityTracker? = null
    private val scroller = OverScroller(context)

    // Sayfa yerleşimi; editor.pages değişince yeniden hesaplanır.
    private var laidOut: List<Page>? = null
    private var lefts = FloatArray(0)
    private var tops = FloatArray(0)
    private var docWidth = 1f
    private var docHeight = 1f
    private var groupedFor: List<RenderedStroke>? = null
    private var grouped: Map<String,List<RenderedStroke>> = emptyMap()

    private val finished = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            layoutPages()
            canvas.drawColor(0xffe9eaec.toInt())
            val strokes = byPage(erasePreview ?: editor.history.items)
            val pages = editor.pages
            for (i in pages.indices) {
                val page = pages[i]
                val m = pageMatrix(i)
                rect.set(0f,0f,page.width,page.height); m.mapRect(rect)
                if (rect.bottom < 0 || rect.top > height || rect.right < 0 || rect.left > width) continue
                val save = canvas.save()
                canvas.clipRect(rect)
                paint.color = android.graphics.Color.WHITE; paint.style = Paint.Style.FILL
                canvas.drawRect(rect,paint)
                val index = page.sourcePageIndex
                val pdf = editor.pdf
                if (page.background == BackgroundType.PDF && index != null && pdf != null) {
                    pdf.bitmap(index, rect.width().toInt()) { invalidate() }?.let { canvas.drawBitmap(it,null,rect,bitmapPaint) }
                }
                // Ink'in transform parametresi geometri/kalite bilgisidir; Canvas'ı dönüştürmez.
                // Sayfa koordinatındaki çizgileri gerçekten ekrana taşımak için aynı matrisi uygula.
                canvas.concat(m)
                drawPattern(canvas,page)
                val dragging=dragFrom!=null && lassoPage==i
                strokes[page.id]?.forEach {
                    if(dragging && it.data.id in editor.selection) {
                        val k=canvas.save(); canvas.translate(dragDelta.first,dragDelta.second); renderer.draw(canvas,it.ink,m); canvas.restoreToCount(k)
                    } else renderer.draw(canvas,it.ink,m)
                }
                drawSelection(canvas,i,page.id)
                canvas.restoreToCount(save)
            }
        }
    }
    // --- Kement seçimi (sayfa yerel koordinatı) ---
    private var lassoPointer=-1
    private var lassoPage=-1
    private val lassoPath=ArrayList<PenGeometry.P>()
    private var dragFrom:Pair<Float,Float>?=null
    private var dragDelta=0f to 0f
    private val dashPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; color=0xff186c84.toInt() }
    private val selectionFill=Paint().apply { style=Paint.Style.FILL; color=0x14186c84 }
    /** Seçimin sayfası ve sınır kutusu (kenar payıyla), seçim yoksa null. */
    private fun selectionBox():Pair<String,RectF>? {
        val chosen=editor.history.items.filter { it.data.id in editor.selection }
        if(chosen.isEmpty()) return null
        val box=RectF(chosen.first().data.bounds())
        chosen.forEach { val b=it.data.bounds(); val w=it.data.width/2
            box.union(b.left-w,b.top-w,b.right+w,b.bottom+w) }
        box.inset(-10f,-10f)
        return chosen.first().data.pageId to box
    }
    private fun drawSelection(canvas:Canvas,pageIndex:Int,pageId:String) {
        val scale=editor.viewport.scale
        dashPaint.strokeWidth=2f*density/scale
        dashPaint.pathEffect=DashPathEffect(floatArrayOf(8f*density/scale,6f*density/scale),0f)
        if(lassoPage==pageIndex && lassoPath.size>1) {
            val path=Path(); path.moveTo(lassoPath[0].x,lassoPath[0].y); lassoPath.forEach { path.lineTo(it.x,it.y) }
            canvas.drawPath(path,dashPaint)
        }
        selectionBox()?.let { (id,box) ->
            if(id!=pageId) return
            if(dragFrom!=null) box.offset(dragDelta.first,dragDelta.second)
            canvas.drawRect(box,selectionFill); canvas.drawRect(box,dashPaint)
        }
    }
    /** Kement aracı: boş yerde döngü çizerek seçer, seçim çerçevesinin içinden sürükleyerek taşır. Parmaklar karışmaz. */
    private fun lassoTouch(e:MotionEvent):Boolean {
        val action=e.actionMasked; val ai=e.actionIndex
        if(lassoPointer<0) {
            if(!(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) || !isPen(e,ai)) return false
            if(e.getToolType(ai)==MotionEvent.TOOL_TYPE_ERASER || e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY)!=0) return false
            if(pending.isNotEmpty()) claimFinished()
            val doc=editor.viewport.toPage(e.getX(ai),e.getY(ai)); val i=pageAt(doc.first,doc.second)
            if(i<0) { editor.clearSelection(); refresh(); return true }
            val local=doc.first-lefts[i] to doc.second-tops[i]
            lassoPointer=e.getPointerId(ai); lassoPage=i; lassoPath.clear(); dragFrom=null; dragDelta=0f to 0f
            val sel=selectionBox()
            if(sel!=null && sel.first==editor.pages[i].id && sel.second.contains(local.first,local.second)) dragFrom=local
            else { editor.clearSelection(); lassoPath+=PenGeometry.P(local.first,local.second) }
            fingerFocus=null; stopFingerGesture(); refresh(); return true
        }
        val index=e.findPointerIndex(lassoPointer)
        if(index<0) return true
        val doc=editor.viewport.toPage(e.getX(index),e.getY(index))
        val local=doc.first-lefts[lassoPage] to doc.second-tops[lassoPage]
        when {
            action==MotionEvent.ACTION_MOVE -> {
                val from=dragFrom
                if(from!=null) dragDelta=local.first-from.first to local.second-from.second
                else for(h in 0 until e.historySize) {
                    val hd=editor.viewport.toPage(e.getHistoricalX(index,h),e.getHistoricalY(index,h))
                    lassoPath+=PenGeometry.P(hd.first-lefts[lassoPage],hd.second-tops[lassoPage])
                }
                if(from==null) lassoPath+=PenGeometry.P(local.first,local.second)
                finished.invalidate()
            }
            (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) && e.getPointerId(ai)==lassoPointer -> {
                // Kalemin kalktığı son konum da sayılır (yoksa bırakırken son hareket kadar geride kalır).
                dragFrom?.let { from -> dragDelta=local.first-from.first to local.second-from.second }
                if(dragFrom==null) lassoPath+=PenGeometry.P(local.first,local.second)
                if(dragFrom!=null) editor.moveSelection(dragDelta.first,dragDelta.second)
                else {
                    val pageId=editor.pages[lassoPage].id
                    editor.selection=editor.history.items.filter { it.data.pageId==pageId && PenGeometry.selectedBy(it.data.samples.map { s -> s.toP() },lassoPath) }
                        .map { it.data.id }.toSet()
                }
                lassoPointer=-1; lassoPage=-1; lassoPath.clear(); dragFrom=null; dragDelta=0f to 0f
                refresh()
            }
        }
        return true
    }
    private data class Pending(val tool: Tool,val color: Int,val size: Float,val pageId: String,
        val left: Float,val top: Float,val startTime: Long,val brush: Brush?=null,
        val samples: MutableList<Sample> = mutableListOf()) {
        /** Akıllı şekil: kalem uçta bekletildiyse çizginin yerine geçecek kenarlar. */
        var shape: List<List<PenGeometry.P>>? = null
    }
    private val scaleGesture = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (editor.busy || palmGesture) return false
            editor.viewport=clamp(editor.viewport.zoomAt(detector.scaleFactor,detector.focusX,detector.focusY))
            refresh(); return true
        }
    })
    init {
        contentDescription="Not sayfası; S Pen ile yazın, parmakla kaydırın, iki parmakla yakınlaştırın"
        addView(finished,LayoutParams(-1,-1))
        addView(live,LayoutParams(-1,-1))
    }
    private fun newLiveView(): InProgressStrokesView = InProgressStrokesView(context).apply {
        addFinishedStrokesListener(object: InProgressStrokesFinishedListener {
            override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) {
                if (this@apply === live) adopt(strokes)
            }
        })
    }

    private fun layoutPages() {
        val pages = editor.pages
        if (pages === laidOut) return
        docWidth = pages.maxOf { it.width }
        lefts = FloatArray(pages.size) { (docWidth - pages[it].width) / 2 }
        tops = FloatArray(pages.size)
        var y = 0f
        pages.forEachIndexed { i, p -> tops[i] = y; y += p.height + GAP }
        docHeight = y - GAP
        laidOut = pages
    }
    private fun byPage(list: List<RenderedStroke>): Map<String,List<RenderedStroke>> {
        if (list !== groupedFor) { grouped = list.groupBy { it.data.pageId }; groupedFor = list }
        return grouped
    }
    /** Belge → ekran dönüşümü. */
    private fun pageToScreen() = viewMatrix.apply {
        val v=editor.viewport
        setScale(v.scale,v.scale)
        postTranslate(v.x,v.y)
    }
    /** i. sayfanın yerel koordinatından ekrana dönüşüm. */
    private fun pageMatrix(i: Int) = pageMatrix.apply { set(pageToScreen()); preTranslate(lefts[i],tops[i]) }
    private fun pageAt(docX: Float, docY: Float): Int {
        layoutPages()
        val pages = editor.pages
        for (i in pages.indices) {
            if (docX in lefts[i]..lefts[i]+pages[i].width && docY in tops[i]..tops[i]+pages[i].height) return i
        }
        return -1
    }

    /** Ink'in teslim ettiği bitmiş çizgileri sayfa verisine alır; aynı UI döngüsünde canlı yüzeyden kaldırır. */
    private fun adopt(strokes: Map<InProgressStrokeId, Stroke>, fromLive: Boolean = true) {
        var next=editor.history.items
        strokes.forEach { (id, ink) ->
            pending.remove(id)?.let { p ->
                val shape=p.shape
                next += if(shape!=null && p.brush!=null) {
                    // Akıllı şekil: serbest çizginin yerine düzgün kenarlar (tek geri alma adımı).
                    shape.map { edge -> val samples=shapeSamples(edge)
                        RenderedStroke(PageStroke(pageId=p.pageId,tool=p.tool,color=p.color,width=p.size,samples=samples),inkFromSamples(p.brush,samples)) }
                } else listOf(RenderedStroke(PageStroke(pageId=p.pageId,tool=p.tool,
                    color=p.color,width=p.size,samples=p.samples.toList()),ink))
                editor.replace(next) // Ink birden çok çizgiyi beraber teslim etse de her çizgi ayrı geri alınır.
            }
        }
        // Aynı UI döngüsünde sahipliği devral; fosforlu kalem iki kez çizilmesin.
        finished.invalidate()
        if(fromLive && strokes.isNotEmpty()) live.removeFinishedStrokes(strokes.keys)
        editor.busy=pointerId != -1 || pending.isNotEmpty()
    }
    /**
     * Ink teslimi bazı durumlarda (ör. teslimden hemen sonra yüzey yeniden boyutlanınca) kendiliğinden
     * gelmeyebilir. Kalem kalktıktan sonra teslim gecikirse bitmiş çizgileri genel API ile kendimiz alırız;
     * böylece ekran "Çiziliyor" durumunda kilitli kalmaz ve yazı kaybolmaz.
     */
    private var watchdogRounds = 0
    private val handoffWatchdog=Runnable {
        if(pointerId!=-1 || pending.isEmpty()) { watchdogRounds=0; return@Runnable }
        android.util.Log.w("GVNnotes","Ink teslimi gecikti; ${pending.size} bitmiş çizgi elle alınıyor.")
        claimFinished()
        if(pending.isEmpty()) { watchdogRounds=0; return@Runnable }
        // Ink çizgiyi hiç tamamlamadıysa kaydedilen gerçek kalem örneklerinden yeniden kur.
        if(++watchdogRounds>=2) { rebuildStuck(); watchdogRounds=0 } else scheduleWatchdog()
    }
    private fun scheduleWatchdog() { removeCallbacks(handoffWatchdog); postDelayed(handoffWatchdog,HANDOFF_TIMEOUT_MS) }
    /**
     * Son çare: Ink'in tamamlamadığı çizgiler kalemden kaydedilen örneklerden oluşturulur ve sayfaya alınır.
     * Bozulmuş canlı yüzey değiştirilir; böylece ekranda yerinden oynamayan "hayalet" ıslak mürekkep kalmaz.
     */
    private fun rebuildStuck() {
        val stuck=pending.toMap()
        android.util.Log.w("GVNnotes","Ink ${stuck.size} çizgiyi tamamlamadı; örneklerden yeniden kuruluyor.")
        val rebuilt=stuck.mapNotNull { (id,p) -> runCatching { id to inkFromSamples(checkNotNull(p.brush),p.samples) }.getOrNull() }.toMap()
        val old=live
        live=newLiveView()
        removeView(old); addView(live,LayoutParams(-1,-1))
        adopt(rebuilt,fromLive=false)
        pending.clear() // Kurulamayan (örneksiz) çizgi kalmışsa kilit yaratmasın.
        editor.busy=pointerId!=-1
        refresh()
    }
    private fun claimFinished() = adopt(live.getFinishedStrokes().filterKeys { it in pending })

    fun refresh() {
        layoutPages()
        fitIfNeeded(width,height)
        // Canlı yüzey her zaman tüm alanı kaplar: zoom/kaydırmada boyutu değişirse Ink teslimi duraklı kalabiliyor.
        // Masa alanında ve sayfa aralarında ıslak mürekkep görünmesin diye kâğıt dışı maskeyle gizlenir.
        if(width>0 && height>0) {
            live.maskPath=Path().apply {
                fillType=Path.FillType.EVEN_ODD
                addRect(0f,0f,width.toFloat(),height.toFloat(),Path.Direction.CW)
                editor.pages.forEachIndexed { i, p ->
                    rect.set(0f,0f,p.width,p.height); pageMatrix(i).mapRect(rect)
                    if (rect.intersects(0f,0f,width.toFloat(),height.toFloat())) addRect(RectF(rect),Path.Direction.CW)
                }
            }
            // Görünür alanın ortasındaki sayfa üst çubukta gösterilir.
            val center=editor.viewport.toPage(width/2f,height/2f).second
            val index=tops.indices.lastOrNull { tops[it] <= center } ?: 0
            if (editor.currentPage != index) editor.currentPage = index
        }
        finished.invalidate()
    }
    /** Sayfanın üstünü görünümün üstüne getirir (sayfa düğmeleri için). */
    fun scrollToPage(index: Int) {
        layoutPages()
        if (editor.busy || index !in tops.indices) return
        scroller.forceFinished(true)
        val v = editor.viewport
        editor.viewport = clamp(v.copy(y = 16 * density - tops[index] * v.scale))
        refresh()
    }
    /** Görünümü başlangıç sığdırmasına döndürür (yakınlaştırma düğmesi). */
    fun fit() {
        if (editor.busy || width <= 0 || height <= 0) return
        scroller.forceFinished(true)
        val keepTop = editor.pages.size > 1 || editor.pages.first().background != BackgroundType.BLANK
        val page = editor.currentPage
        editor.fitted = false
        fitIfNeeded(width, height); refresh()
        if (keepTop) scrollToPage(page)
    }
    /** Belge en az kenar payı kadar görünür kalsın; küçükse tamamen ekranda kalsın. */
    private fun clamp(v: Viewport): Viewport {
        if (width <= 0 || height <= 0) return v
        layoutPages()
        val m = 24 * density
        fun axis(pos: Float, size: Float, view: Float): Float {
            val a = view - size - m; return pos.coerceIn(min(a, m), max(a, m))
        }
        return v.copy(x = axis(v.x, docWidth * v.scale, width.toFloat()), y = axis(v.y, docHeight * v.scale, height.toFloat()))
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        refresh()
    }
    /**
     * İlk sığdırma: not diskten yüklendikten sonra yapılır (yüklenmeden önce geçici boş sayfaya göre
     * sığdırılırsa PDF notu yanlış ölçekle açılır ve son sayfa bilgisi erken tüketilir).
     */
    private fun fitIfNeeded(w:Int,h:Int) {
        if (editor.fitted || !editor.ready || w<=0 || h<=0) return
        layoutPages()
        val margin=20*density
        val first=editor.pages.first()
        editor.viewport = if (editor.pages.size == 1 && first.background == BackgroundType.BLANK) {
            // Tek boş sayfa: sayfanın tamamı ekrana sığar (önceki sürümlerle aynı görünüm).
            val s=min((w-margin*2)/first.width,(h-margin*2)/first.height).coerceAtLeast(.1f)
            Viewport(s,(w-first.width*s)/2,(h-first.height*s)/2)
        } else {
            // Çok sayfa/PDF: sayfa genişliği ekrana sığar, okumaya üstten başlanır.
            val s=((w-margin*2)/docWidth).coerceIn(.1f,8f)
            Viewport(s,(w-docWidth*s)/2,margin)
        }
        editor.fitted=true
        // Son bırakılan sayfaya git; sayfa sayısı değiştiyse sınırla.
        val last=editor.initialPage; editor.initialPage=0
        if(last>0) post { scrollToPage(last.coerceAtMost(editor.pages.lastIndex)) }
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            editor.viewport = clamp(editor.viewport.copy(x = scroller.currX.toFloat(), y = scroller.currY.toFloat()))
            refresh(); postInvalidateOnAnimation()
        }
    }
    override fun onInterceptTouchEvent(event:MotionEvent) = true
    private fun isPen(e:MotionEvent,index:Int):Boolean = when(e.getToolType(index)) {
        MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER -> true
        MotionEvent.TOOL_TYPE_MOUSE -> editor.mouseInput
        // Parmakla çizim yalnız ayarda açıksa, tek parmakla ve avuç büyüklüğünde olmayan temasta.
        MotionEvent.TOOL_TYPE_FINGER -> editor.fingerDraw && e.pointerCount==1 && e.getTouchMajor(index)<=PALM_DP*density
        else -> false
    }
    /** Etkin çizgi parmakla mı başladı (ikinci parmak gelince iptal edilip kaydırmaya geçilir). */
    private var fingerStroke=false
    @SuppressLint("ClickableViewAccessibility") // This surface handles handwriting/pan gestures, not click actions; tools are accessible Compose buttons.
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!editor.ready || (editor.navigationPending && pointerId<0)) return true // Yükleme/çıkış sırasında yeni çizim başlamaz.
        predictor.record(event)
        val action=event.actionMasked
        val ai=event.actionIndex
        if (action==MotionEvent.ACTION_DOWN) scroller.forceFinished(true)
        val canceledPointer=Build.VERSION.SDK_INT>=33 && event.flags and MotionEvent.FLAG_CANCELED != 0 &&
            (pointerId<0 || event.getPointerId(ai)==pointerId)
        if (action==MotionEvent.ACTION_CANCEL || canceledPointer) {
            cancelGesture(event); return true
        }
        // Parmakla yazarken ikinci parmak değerse: yarım çizgi iptal, hareket kaydırma/yakınlaştırmaya döner.
        if(fingerStroke && pointerId>=0 && action==MotionEvent.ACTION_POINTER_DOWN && event.getToolType(ai)==MotionEvent.TOOL_TYPE_FINGER) {
            strokeId?.let { live.cancelStroke(it,event); pending.remove(it) }
            erasePreview=null; pointerId=-1; strokeId=null; current=null; lastErase=null; fingerStroke=false
            editor.busy=pending.isNotEmpty(); refresh()
        }
        if(editor.tool==Tool.LASSO && lassoTouch(event)) return true
        // Önceki çizginin asenkron teslimini beklemek hızlı el yazısında sonraki harfi düşürür.
        // Pending çizgiler varken kaleme izin ver; araç değişimi ve zoom yine kilitli kalır.
        if (pointerId == -1 &&
            (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) && isPen(event,ai)) {
            val doc=editor.viewport.toPage(event.getX(ai),event.getY(ai))
            val pageIndex=pageAt(doc.first,doc.second)
            if (pageIndex < 0) return true
            val page=editor.pages[pageIndex]
            requestUnbufferedDispatch(event)
            pointerId=event.getPointerId(ai); editor.busy=true; fingerFocus=null; stopFingerGesture()
            fingerStroke=event.getToolType(ai)==MotionEvent.TOOL_TYPE_FINGER
            // Yan düğme eşlemesi henüz ürün kararı değil; bu prototipte sadece araç seçicisi.
            // S Pen yan düğmesi basılıyken (veya kalemin silgi ucu) geçici silgi; araç seçimi değişmez.
            val sideButton=event.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_SECONDARY) != 0
            val tool=if(event.getToolType(ai)==MotionEvent.TOOL_TYPE_ERASER || sideButton) Tool.ERASER else editor.tool
            if(tool==Tool.ERASER && pending.isNotEmpty()) claimFinished() // Silmeden önce bitmiş mürekkebi sayfaya al.
            if(tool==Tool.ERASER && pending.isNotEmpty()) {
                pointerId=-1; editor.busy=true; scheduleWatchdog(); return true // Yarım çizgi üzerinde silme yarışı oluşturma.
            }
            val width=if(tool==Tool.HIGHLIGHTER) editor.size*5 else editor.size
            val color=if(tool==Tool.HIGHLIGHTER) (editor.color and 0x00ffffff) or 0x66000000 else editor.color
            current=Pending(tool,color,width,page.id,lefts[pageIndex],tops[pageIndex],event.eventTime)
            if(tool==Tool.ERASER) erasePreview=editor.history.items else {
                val inverse=Matrix(); pageMatrix(pageIndex).invert(inverse)
                val family=if(tool==Tool.HIGHLIGHTER) StockBrushes.highlighter() else StockBrushes.pressurePen()
                val brush=Brush.createWithColorIntArgb(family,color,width,.1f)
                current=current!!.copy(brush=brush)
                strokeId=live.startStroke(event,pointerId,brush,inverse)
                pending[strokeId!!]=current!!
            }
        }
        val index=if(pointerId>=0) event.findPointerIndex(pointerId) else -1
        if(index>=0) {
            if(action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_DOWN ||
                ((action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP || action==MotionEvent.ACTION_POINTER_DOWN) && event.getPointerId(ai)==pointerId)) {
                recordRealSamples(event,index)
                val id=strokeId
                if(action==MotionEvent.ACTION_MOVE && id!=null) {
                    val predicted=predictor.predict()
                    try { live.addToStroke(event,pointerId,id,predicted) } finally { predicted?.recycle() }
                }
            }
            if ((action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) && event.getPointerId(ai)==pointerId) {
                if(current?.tool==Tool.ERASER) {
                    editor.replace(erasePreview ?: editor.history.items)
                    erasePreview=null
                } else {
                    current?.let { p ->
                        if(editor.smartShape && p.tool!=Tool.ERASER) {
                            val points=p.samples.map { it.toP() }
                            if(PenGeometry.heldAtEnd(points,SHAPE_HOLD_RADIUS,SHAPE_HOLD_MS)) p.shape=PenGeometry.fitShape(points)
                        }
                    }
                    strokeId?.let { live.finishStroke(event,pointerId,it) }
                }
                pointerId=-1; strokeId=null; current=null; lastErase=null
                editor.busy=pending.isNotEmpty(); refresh()
                if(pending.isNotEmpty()) scheduleWatchdog()
            }
            return true // Kalem varken avuç/parmak zoom yapamaz.
        }
        if(editor.busy) return true
        if ((0 until event.pointerCount).any { event.getToolType(it)!=MotionEvent.TOOL_TYPE_FINGER }) return true
        fingerGesture(event)
        return true
    }
    /** Tek parmak kaydırır (bırakınca savrulur), iki parmak kaydırır ve yakınlaştırır. Avuç teması yok sayılır. */
    private fun fingerGesture(event: MotionEvent) {
        val action=event.actionMasked
        if (action==MotionEvent.ACTION_DOWN) { palmGesture=false; velocity?.recycle(); velocity=VelocityTracker.obtain() }
        if ((0 until event.pointerCount).any { event.getTouchMajor(it) > PALM_DP * density }) palmGesture=true
        if (palmGesture) { fingerFocus=null; if(action==MotionEvent.ACTION_UP) stopFingerGesture(); return }
        velocity?.addMovement(event)
        scaleGesture.onTouchEvent(event)
        val lifted=if(action==MotionEvent.ACTION_POINTER_UP) 1 else 0
        val count=event.pointerCount-lifted
        var x=0f; var y=0f
        for(i in 0 until event.pointerCount) if(!(lifted==1 && i==event.actionIndex)) { x+=event.getX(i); y+=event.getY(i) }
        val focus=x/count to y/count
        // Parmak sayısı değişince odak sıçramasın.
        if (count!=fingerCount || action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_POINTER_UP) {
            fingerFocus=focus; fingerCount=count
        } else if (action==MotionEvent.ACTION_MOVE) {
            fingerFocus?.let { before ->
                editor.viewport=clamp(editor.viewport.copy(x=editor.viewport.x+focus.first-before.first,y=editor.viewport.y+focus.second-before.second))
                refresh()
            }
            fingerFocus=focus
        }
        if (action==MotionEvent.ACTION_UP) {
            velocity?.let { tracker ->
                tracker.computeCurrentVelocity(1000)
                val v=editor.viewport
                layoutPages()
                val big=(max(docWidth,docHeight)*v.scale*2).toInt()
                scroller.fling(v.x.toInt(),v.y.toInt(),tracker.xVelocity.toInt(),tracker.yVelocity.toInt(),-big,big,-big,big)
                postInvalidateOnAnimation()
            }
            stopFingerGesture()
        }
    }
    private fun stopFingerGesture() { fingerFocus=null; fingerCount=0; velocity?.recycle(); velocity=null }
    private fun recordRealSamples(e:MotionEvent,i:Int) {
        val p=current ?: return
        for(h in 0..e.historySize) {
            val historical=h<e.historySize
            val x=if(historical)e.getHistoricalX(i,h) else e.getX(i)
            val y=if(historical)e.getHistoricalY(i,h) else e.getY(i)
            val doc=editor.viewport.toPage(x,y)
            val point=doc.first-p.left to doc.second-p.top
            val time=if(historical)e.getHistoricalEventTime(h) else e.eventTime
            val pressure=if(historical)e.getHistoricalPressure(i,h) else e.getPressure(i)
            fun axis(axis:Int)=if(historical)e.getHistoricalAxisValue(axis,i,h) else e.getAxisValue(axis,i)
            val sample=Sample(point.first,point.second,pressure,axis(MotionEvent.AXIS_TILT),axis(MotionEvent.AXIS_ORIENTATION),time-p.startTime)
            if(p.samples.lastOrNull()!=sample) p.samples+=sample
            if(p.tool==Tool.ERASER) {
                val from=lastErase ?: doc
                erasePreview=eraseAlong(erasePreview ?: editor.history.items,from,doc)
                lastErase=doc
            }
        }
        if(p.tool==Tool.ERASER) refresh()
    }
    /**
     * Silgi yolu belge koordinatındadır; her çizgi kendi sayfasının yerel koordinatında sınanır.
     * Tüm çizgi modunda dokunulan çizgi silinir; alan modunda yalnız silginin süpürdüğü kısım kesilir
     * (GVNpen'den taşınan süpürülen kapsül algoritması). Hiçbir şey değişmezse aynı liste döner.
     */
    private fun eraseAlong(list: List<RenderedStroke>, from: Pair<Float,Float>, to: Pair<Float,Float>): List<RenderedStroke> {
        val radius=editor.eraserSize; val whole=editor.eraserWhole
        val pageIndex=editor.pages.withIndex().associate { (i,p) -> p.id to i }
        var out: ArrayList<RenderedStroke>?=null
        list.forEachIndexed { k, rs ->
            val i=pageIndex[rs.data.pageId]
            val result=if(i==null) null else {
                val dx=lefts[i]; val dy=tops[i]
                val a=PenGeometry.P(from.first-dx,from.second-dy); val b=PenGeometry.P(to.first-dx,to.second-dy)
                val reach=radius+rs.data.width/2; val box=rs.data.bounds()
                if(box.left-reach>maxOf(a.x,b.x) || box.right+reach<minOf(a.x,b.x) || box.top-reach>maxOf(a.y,b.y) || box.bottom+reach<minOf(a.y,b.y)) null
                else PenGeometry.eraseStroke(rs.data.samples.map { it.toP() },rs.data.width,a,b,radius,whole)
            }
            if(result==null) { out?.add(rs); return@forEachIndexed }
            if(out==null) out=ArrayList<RenderedStroke>(list.size).apply { addAll(list.subList(0,k)) }
            result.forEach { piece -> runCatching { out!!.add(rs.withSamples(samplesOf(piece,rs.data.samples))) } }
        }
        return out ?: list
    }
    private fun cancelGesture(e:MotionEvent) {
        lassoPointer=-1; lassoPage=-1; lassoPath.clear(); dragFrom=null; dragDelta=0f to 0f
        strokeId?.let { live.cancelStroke(it,e); pending.remove(it) }
        pointerId=-1; strokeId=null; current=null; erasePreview=null; lastErase=null; stopFingerGesture(); palmGesture=false
        editor.busy=pending.isNotEmpty(); refresh()
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(handoffWatchdog)
        scroller.forceFinished(true)
        claimFinished() // Kalemi kalkmış ama henüz teslim edilmemiş çizgiler ekran kapanırken kaybolmasın.
        live.cancelUnfinishedStrokes()
        pending.clear(); pointerId=-1; current=null; strokeId=null; erasePreview=null
        editor.busy=false
        super.onDetachedFromWindow()
    }
    private companion object {
        const val HANDOFF_TIMEOUT_MS=700L
        /** Sayfalar arası boşluk (belge birimi). */
        const val GAP=32f
        /** Bundan geniş temas avuç sayılır ve kaydırma yapmaz. */
        const val PALM_DP=90f
        /** Akıllı şekil: kalem uçta bu süre/yarıçap içinde bekletilirse şekle çevrilir (sayfa birimi ≈ 0,2 mm). */
        const val SHAPE_HOLD_MS=550L
        const val SHAPE_HOLD_RADIUS=7f
    }
}
