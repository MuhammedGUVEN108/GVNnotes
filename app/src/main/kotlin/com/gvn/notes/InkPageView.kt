package com.gvn.notes

import android.animation.ValueAnimator
import android.view.animation.DecelerateInterpolator
import android.annotation.SuppressLint
import android.content.Context
import android.graphics.*
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.*
import android.widget.FrameLayout
import android.widget.OverScroller
import com.gvn.notes.core.*
import com.gvn.notes.pen.Geometry
import com.gvn.notes.pen.PenRenderer
import com.gvn.notes.pen.Prefs
import com.gvn.notes.pen.SmartShape
import kotlin.math.max
import kotlin.math.min

/**
 * Sayfa içi yazma yüzeyi: GVNpen InkView'ün davranışı (basınç, yumuşatma, fosforlu, akıllı şekil ve geçişi, 2B/3B
 * şekiller, kapsül silgi, S Pen düğmesi, imleç, tahmin) sayfa koordinatına uyarlanmıştır. Ayarlar GVNpen [Prefs]'tir.
 *
 * Belge koordinatı: sayfalar alt alta, aralarında [GAP] boşlukla dizilir. Görünüm (editor.viewport) belge koordinatını
 * ekrana taşır. Çizgiler kendi sayfalarının yerel koordinatında saklanır (birim ≈ dp; 1000 birim sayfa genişliği).
 */
@SuppressLint("ViewConstructor")
class InkPageView(context: Context, private val editor: EditorState) : FrameLayout(context) {
    /** GVNpen ayarları (araç, renk, kalınlıklar, silgi, akıllı şekil, düğme…). Ayarlar ekranından dönünce [reloadPrefs]. */
    var prefs: Prefs = Prefs.load(context); private set
    /** false = Gezinme (GVNpen "cursor"): kalem yazmaz, sayfayı kaydırır. */
    var drawing = true
        set(value) { field = value; cancel(); cursor.invalidate() }
    /** Araç şeridi seçimi/geri al düğmelerini tazelemek için. */
    var onStateChanged: (() -> Unit)? = null

    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint = Paint(Paint.FILTER_BITMAP_FLAG)
    private val viewMatrix = Matrix()
    private val pageMatrix = Matrix()
    private val wetMatrix = Matrix()
    private val rect = RectF()
    private val density = resources.displayMetrics.density
    private val handler = Handler(Looper.getMainLooper())
    private val strokePath = Path()
    private val wetPath = Path()
    private var inkTiles:InkTileCache=createInkCache()
    private fun createInkCache():InkTileCache=InkTileCache(async=true,onReady={ finished.postInvalidateOnAnimation() },onError={ editor.reportPageProblem() })
    internal fun inkSettled()=inkTiles.settled
    private val activeInk=ActiveInkCache()
    private val localClip=Rect()

    // --- Kalem teması (GVNpen InkView alanları; noktalar temas sayfasının yerel koordinatında) ---
    private var pointer = -1
    private var contact = false
    private var contactPage = -1
    private var active: Geometry.Stroke? = null
    private var preview: List<Geometry.Stroke>? = null
    private var predicted: Geometry.Stroke? = null
    private var start: Geometry.Point? = null
    private var last: Geometry.Point? = null
    private var motionAnchor: Geometry.Point? = null
    private var erasing = false
    private var temp = false
    private var buttonDown = false
    private var smartSnapped = false
    private var shapeInterrupted = false
    private var lastMotion = 0L
    private var smartAttempt = -1L
    private var snapStart = 0L
    private var fingerStroke = false
    /** Bu temas/silme sırasında değişen çizgi listesi; temas bitince tek geri alma adımı olarak kaydedilir. */
    private var working: MutableList<RenderedStroke>? = null
    private var workingPage:String?=null
    /** Kalem ekrana yakınken ekran koordinatı (imleç) ve sayfa yerel noktası (yaklaşınca silme). */
    private var hoverScreen: PointF? = null
    private var hoverPage = -1
    private var hoverLocal: Geometry.Point? = null

    // --- Parmak hareketleri ---
    private var fingerFocus: Pair<Float,Float>? = null
    private var fingerCount = 0
    private var palmGesture = false
    private var velocity: VelocityTracker? = null
    private val scroller = OverScroller(context)
    private var settling: ValueAnimator? = null
    private var flingPending = false
    internal fun navigationSettled() = !flingPending && scroller.isFinished && settling?.isRunning != true
    private fun stopNavigation() { settling?.cancel(); settling=null; scroller.forceFinished(true); flingPending=false }
    /** Gezinme modunda kalem de parmak gibi kaydırır. */
    private var penPanning = false

    // --- Sayfa yerleşimi ---
    private var laidOut: List<Page>? = null
    private var lefts = FloatArray(0)
    private var tops = FloatArray(0)
    private var docWidth = 1f
    private var docHeight = 1f
    private var groupedFor: List<RenderedStroke>? = null
    private var groupedCount = -1
    private var groupedLast: RenderedStroke? = null
    private var grouped: Map<String,List<RenderedStroke>> = emptyMap()

    // --- İmleç (GVNpen: kalem bitmap'i veya silgi halkası) ---
    private val pencil: Bitmap = BitmapFactory.decodeResource(resources,R.drawable.pencil_cursor,BitmapFactory.Options().apply { inScaled=false })
    private var pencilHotX = 0f
    private var pencilHotY = 0f
    private val cursorPaint = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val hiddenPointer: PointerIcon = PointerIcon.getSystemIcon(context,PointerIcon.TYPE_NULL)

    private val finished = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            layoutPages()
            inkTiles.beginFrame(Triple(editor.viewport,width,height))
            canvas.drawColor(0xffe3e9f1.toInt()) // Petrol Ada masası
            val strokes = editor.history.grouped
            val visibleIds=ArrayList<String>()
            val pages = editor.pages
            for (i in pages.indices) {
                val page = pages[i]
                val m = pageMatrix(i)
                rect.set(0f,0f,page.width,page.height); m.mapRect(rect)
                if (rect.bottom < 0 || rect.top > height || rect.right < 0 || rect.left > width) continue
                val save = canvas.save()
                canvas.clipRect(rect)
                paint.reset(); paint.isAntiAlias=true
                paint.color = Color.WHITE; paint.style = Paint.Style.FILL
                canvas.drawRect(rect,paint)
                val index = page.sourcePageIndex
                val pdf = editor.pdf
                if (page.background == BackgroundType.PDF && index != null && pdf != null) {
                    pdf.bitmap(index, rect.width().toInt()) { invalidate() }?.let { canvas.drawBitmap(it,null,rect,bitmapPaint) }
                }
                canvas.concat(m)
                drawPattern(canvas,page)
                val dragging = dragFrom!=null && lassoPage==i
                visibleIds+=page.id
                val own=if(workingPage==page.id) working ?: strokes[page.id].orEmpty() else strokes[page.id].orEmpty()
                canvas.getClipBounds(localClip)
                val settled=if(dragging) own.filter { it.data.id !in editor.selection } else own
                inkTiles.draw(canvas,page,settled,RectF(localClip),editor.viewport.scale,prefs.smooth,editor.pageReady(page.id))
                if(dragging) {
                    val k=canvas.save(); canvas.translate(dragDelta.first,dragDelta.second)
                    own.filter { it.data.id in editor.selection }.forEach { drawGeo(canvas,it.geo) }
                    canvas.restoreToCount(k)
                }
                drawSelection(canvas,i,page.id)
                canvas.restoreToCount(save)
            }
            editor.preparePages(visibleIds)
        }
    }
    /** Çizilmekte olan çizgi, şekil önizlemesi ve tahmin (GVNpen ıslak katmanı). */
    private val wet = object : View(context) {
        override fun onDraw(c: Canvas) {
            val i = contactPage
            if (i !in editor.pages.indices) return
            val page = editor.pages[i]
            wetMatrix.set(pageMatrix(i))
            rect.set(0f,0f,page.width,page.height); wetMatrix.mapRect(rect)
            val save = c.save(); c.clipRect(rect)
            val shape = preview; val stroke = active
            if(shape==null && stroke!=null && !stroke.marker) activeInk.drawHead(c,stroke,wetMatrix,width,height,prefs.smooth)
            c.concat(wetMatrix)
            if (shape != null) {
                val f = if (smartSnapped) min(1f,(SystemClock.uptimeMillis()-snapStart)/120f) else 1f
                if (f < 1f && stroke != null) {
                    val layer = c.saveLayerAlpha(null,Math.round(255*(1-f)))
                    PenRenderer.drawStroke(c,stroke,0,stroke.points.size,paint,prefs.smooth,wetPath,null); c.restoreToCount(layer)
                }
                val layer = c.saveLayerAlpha(null,Math.round(255*f))
                PenRenderer.drawStrokes(c,shape,paint,prefs.smooth); c.restoreToCount(layer)
                if (f < 1f) postInvalidateOnAnimation()
            } else if (stroke != null) {
                if(stroke.marker) PenRenderer.drawStroke(c,stroke,0,stroke.points.size,paint,prefs.smooth,wetPath,null)
                else activeInk.drawTail(c,stroke,prefs.smooth)
            }
            val p = predicted
            if (p != null && prefs.predict && shape == null && stroke != null) PenRenderer.drawStroke(c,p,0,p.points.size,paint,false,wetPath,null)
            c.restoreToCount(save)
        }
    }
    private val cursor = object : View(context) {
        override fun onDraw(c: Canvas) {
            val h = hoverScreen ?: return
            if (!drawing || editor.lassoMode) return
            if (eraserCursor()) {
                val radius = eraserScreenRadius()
                cursorPaint.style=Paint.Style.STROKE; cursorPaint.strokeWidth=2.5f*density; cursorPaint.color=Color.WHITE; c.drawCircle(h.x,h.y,radius,cursorPaint)
                cursorPaint.strokeWidth=density; cursorPaint.color=0xff18222e.toInt(); c.drawCircle(h.x,h.y,radius,cursorPaint)
            } else if (prefs.showCursor) {
                cursorPaint.alpha=255; cursorPaint.style=Paint.Style.FILL
                rect.set(h.x-pencilHotX*density,h.y-pencilHotY*density,h.x+(32-pencilHotX)*density,h.y+(32-pencilHotY)*density)
                c.drawBitmap(pencil,null,rect,cursorPaint)
            }
        }
    }

    init {
        contentDescription="Not sayfası; S Pen ile yazın, parmakla kaydırın, iki parmakla yakınlaştırın"
        addView(finished,LayoutParams(-1,-1)); addView(wet,LayoutParams(-1,-1)); addView(cursor,LayoutParams(-1,-1))
        // GVNpen: kalem imlecinin uç noktası, bitmap'teki ilk opak pikselden bulunur.
        var best=Int.MAX_VALUE
        for(y in 0 until pencil.height) for(x in 0 until pencil.width) {
            if(Color.alpha(pencil.getPixel(x,y))<100) continue
            val score=(x+y)*pencil.width+Math.abs(x-y)
            if(score<best) { best=score; pencilHotX=x*32f/pencil.width; pencilHotY=y*32f/pencil.height }
        }
    }
    override fun onAttachedToWindow() { super.onAttachedToWindow(); if(inkTiles.closed) inkTiles=createInkCache(); if(Build.VERSION.SDK_INT>=30) requestUnbufferedDispatch(InputDevice.SOURCE_CLASS_POINTER) }
    override fun onResolvePointerIcon(event: MotionEvent, pointerIndex: Int): PointerIcon? =
        if(drawing && !editor.lassoMode && event.getToolType(pointerIndex)==MotionEvent.TOOL_TYPE_STYLUS) hiddenPointer else super.onResolvePointerIcon(event,pointerIndex)

    /** Ayarlar ekranından dönünce: yeni GVNpen ayarlarını uygula. */
    fun reloadPrefs() { prefs=Prefs.load(context); cancel(); refresh(); onStateChanged?.invoke() }
    fun savePrefs() { prefs.save(context) }
    private fun drawGeo(c: Canvas, s: Geometry.Stroke) = PenRenderer.drawStroke(c,s,0,s.points.size,paint,prefs.smooth,strokePath,null)
    private fun eraserCursor() = temp || prefs.tool=="eraser"
    /**
     * Silgi yarıçapı ekran ölçüsündedir (GVNpen'deki gibi dp): yakınlaştırma ne olursa olsun imleç aynı boyda görünür
     * ve tam imlecin altını siler (kullanıcı isteği, 12 Kasım 2026). Sayfadaki karşılığı ölçeğe bölünerek bulunur.
     */
    private fun eraserScreenRadius() = prefs.eraser/2*density

    // ---------------- Sayfa yerleşimi ve görünüm ----------------
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
        inkTiles.retainPages(pages.map { it.id }.toSet())
    }
    private fun byPage(list: List<RenderedStroke>): Map<String,List<RenderedStroke>> {
        if (list !== groupedFor || list.size!=groupedCount || list.lastOrNull()!==groupedLast) {
            grouped = list.groupBy { it.data.pageId }; groupedFor = list
            groupedCount=list.size; groupedLast=list.lastOrNull()
        }
        return grouped
    }
    private fun pageToScreen() = viewMatrix.apply { val v=editor.viewport; setScale(v.scale,v.scale); postTranslate(v.x,v.y) }
    private fun pageMatrix(i: Int) = pageMatrix.apply { set(pageToScreen()); preTranslate(lefts[i],tops[i]) }
    private fun pageAt(docX: Float, docY: Float): Int {
        layoutPages()
        val pages = editor.pages
        for (i in pages.indices) if (docX in lefts[i]..lefts[i]+pages[i].width && docY in tops[i]..tops[i]+pages[i].height) return i
        return -1
    }
    /** Ekran noktası → (sayfa, yerel nokta). Sayfa dışındaysa sayfa -1. */
    private fun local(x: Float, y: Float, page: Int = -1): Pair<Int,PointF> {
        val doc = editor.viewport.toPage(x,y)
        val i = if (page >= 0) page else pageAt(doc.first,doc.second)
        return if (i < 0) -1 to PointF(doc.first,doc.second) else i to PointF(doc.first-lefts[i],doc.second-tops[i])
    }

    fun refresh() {
        layoutPages()
        fitIfNeeded(width,height)
        if (width>0 && height>0) {
            val center=editor.viewport.toPage(width/2f,height/2f).second
            val index=tops.indices.lastOrNull { tops[it] <= center } ?: 0
            if (editor.currentPage != index) editor.currentPage = index
        }
        finished.invalidate(); wet.invalidate(); cursor.invalidate()
    }
    fun scrollToPage(index: Int) {
        layoutPages()
        if (editor.busy || index !in tops.indices) return
        stopNavigation()
        val v = editor.viewport
        editor.viewport = clamp(v.copy(y = 16 * density - tops[index] * v.scale))
        refresh()
    }
    fun fit() {
        if (editor.busy || width <= 0 || height <= 0) return
        stopNavigation()
        val keepTop = editor.pages.size > 1 || editor.pages.first().background != BackgroundType.BLANK
        val page = editor.currentPage
        editor.fitted = false
        fitIfNeeded(width, height); refresh()
        if (keepTop) scrollToPage(page)
    }
    private fun singleBlank() = editor.pages.size == 1 && editor.pages.first().background == BackgroundType.BLANK
    internal fun fitScale(): Float {
        layoutPages()
        val m=20*density
        return (if(singleBlank()) min((width-2*m)/docWidth,(height-2*m)/docHeight)
            else (width-2*m)/docWidth).coerceIn(.1f,8f)
    }
    private fun clamp(v: Viewport): Viewport {
        if (width <= 0 || height <= 0) return v
        layoutPages()
        val m=20*density
        fun axis(pos:Float,size:Float,view:Float):Float =
            if(size<=view) (view-size)/2
            else pos.coerceIn(view-size-m,m)
        return v.copy(x=axis(v.x,docWidth*v.scale,width.toFloat()),
            y=axis(v.y,docHeight*v.scale,height.toFloat()))
    }
    /** Yalnız gezinme bitince oturur; büyük ölçekte okunan konum korunur. */
    internal fun settleViewport() {
        if(contact || editor.busy || !editor.ready || width<=0 || height<=0) return
        settling?.cancel()
        val from=editor.viewport
        val target=clamp(from)
        if(from==target) return
        settling=ValueAnimator.ofFloat(0f,1f).apply {
            duration=230; interpolator=DecelerateInterpolator()
            addUpdateListener {
                if(contact || editor.busy) { it.cancel(); return@addUpdateListener }
                val t=it.animatedValue as Float
                editor.viewport=Viewport(from.scale,
                    from.x+(target.x-from.x)*t,from.y+(target.y-from.y)*t)
                refresh()
            }
            start()
        }
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        // Sayfa paneli açıldığında yeni çalışma genişliğine sığdır; açık sayfayı koru.
        if(oldw>0 && oldh>0 && editor.ready && !editor.busy) fit() else refresh()
    }
    /** İlk sığdırma: not diskten yüklendikten sonra yapılır. */
    private fun fitIfNeeded(w:Int,h:Int) {
        if (editor.fitted || !editor.ready || w<=0 || h<=0) return
        layoutPages()
        val margin=20*density
        val first=editor.pages.first()
        editor.viewport = if (editor.pages.size == 1 && first.background == BackgroundType.BLANK) {
            val s=fitScale()
            Viewport(s,(w-first.width*s)/2,(h-first.height*s)/2)
        } else {
            val s=fitScale()
            Viewport(s,(w-docWidth*s)/2,margin)
        }
        editor.fitted=true
        val lastPage=editor.initialPage; editor.initialPage=0
        if(lastPage>0) post { scrollToPage(lastPage.coerceAtMost(editor.pages.lastIndex)) }
    }
    override fun computeScroll() {
        if (scroller.computeScrollOffset()) {
            editor.viewport = editor.viewport.copy(x = scroller.currX.toFloat(), y = scroller.currY.toFloat())
            refresh(); postInvalidateOnAnimation()
        } else if(flingPending) { flingPending=false; settleViewport() }
    }

    // ---------------- Girdi ----------------
    override fun onInterceptTouchEvent(event:MotionEvent) = true
    private fun isStylus(e:MotionEvent,i:Int)=e.getToolType(i).let { it==MotionEvent.TOOL_TYPE_STYLUS || it==MotionEvent.TOOL_TYPE_ERASER || (it==MotionEvent.TOOL_TYPE_MOUSE && editor.mouseInput) }
    /** Yazabilen temas: kalem; GVNpen "parmakla çizimi engelle" kapalıysa tek parmak da (avuç hariç). */
    private fun canInk(e:MotionEvent,i:Int)=isStylus(e,i) ||
        (!prefs.onlyPen && e.getToolType(i)==MotionEvent.TOOL_TYPE_FINGER && e.pointerCount==1 && e.getTouchMajor(i)<=PALM_DP*density)

    private val hoverExitTick = Runnable { if(!contact) { hoverScreen=null; hoverLocal=null; resetButton(); cursor.invalidate() } }
    private val buttonTick = Runnable {
        if(buttonDown && drawing) {
            shapeInterrupted=true; temp=true; if(contact) endPen()
            val h=hoverLocal; if(h!=null && hoverPage>=0 && (contact || prefs.hoverErase)) erase(hoverPage,h)
            onStateChanged?.invoke(); invalidateAll()
        }
    }
    private val smartTick = object : Runnable { override fun run() {
        if(!contact) return
        val a=active
        if(!temp && a!=null && preview==null && prefs.smart && prefs.tool=="pen" && smartAttempt!=lastMotion && SystemClock.uptimeMillis()-lastMotion>=prefs.smartDelay) {
            smartAttempt=lastMotion; preview=SmartShape.fit(a.points,a.color,a.width); smartSnapped=preview!=null
            if(smartSnapped) { snapStart=SystemClock.uptimeMillis(); predicted=null }
            wet.invalidate()
        }
        handler.postDelayed(this,50)
    } }
    /** GVNpen: düğme basılı tutma süresi dolunca geçici silgi; bırakınca önceki araca dönüş. */
    private fun button(e:MotionEvent) {
        val down=prefs.button && (e.buttonState and (MotionEvent.BUTTON_STYLUS_PRIMARY or MotionEvent.BUTTON_STYLUS_SECONDARY))!=0
        if(down && !buttonDown) { buttonDown=true; handler.postDelayed(buttonTick,prefs.hold.toLong()) }
        if(!down && buttonDown) { buttonDown=false; handler.removeCallbacks(buttonTick); temp=false; erasing=false; last=null; if(!contact) commitWorking(); onStateChanged?.invoke(); cursor.invalidate() }
    }
    override fun onGenericMotionEvent(e: MotionEvent): Boolean {
        val type=e.getToolType(e.actionIndex)
        if(type!=MotionEvent.TOOL_TYPE_STYLUS && type!=MotionEvent.TOOL_TYPE_ERASER) return super.onGenericMotionEvent(e)
        if(prefs.hoverErase && (temp || e.buttonState!=0)) stopNavigation()
        handler.removeCallbacks(hoverExitTick); button(e)
        hoverScreen=PointF(e.x,e.y)
        val (page,pt)=local(e.x,e.y); hoverPage=page; hoverLocal=if(page>=0) Geometry.Point(pt.x,pt.y) else null
        if(e.actionMasked==MotionEvent.ACTION_HOVER_EXIT) handler.postDelayed(hoverExitTick,160)
        else if(temp && prefs.hoverErase && drawing && page>=0) { erase(page,hoverLocal!!); invalidateAll() }
        cursor.invalidate(); return true
    }

    @SuppressLint("ClickableViewAccessibility")
    override fun onTouchEvent(e:MotionEvent):Boolean {
        if(!editor.ready || (editor.navigationPending && pointer<0)) return true
        val action=e.actionMasked; val ai=e.actionIndex
        if(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) stopNavigation()
        if(action==MotionEvent.ACTION_CANCEL) { val navigating=fingerFocus!=null || penPanning; cancel(); stopFingerGesture(); penPanning=false; if(navigating) settleViewport(); return true }
        // Parmakla yazarken ikinci parmak değerse: yarım çizgi bırakılır, hareket kaydırma/yakınlaştırmaya döner.
        if(fingerStroke && contact && action==MotionEvent.ACTION_POINTER_DOWN && e.getToolType(ai)==MotionEvent.TOOL_TYPE_FINGER) {
            active=null; preview=null; predicted=null; contact=false; pointer=-1; fingerStroke=false; working=null
            handler.removeCallbacks(smartTick); editor.busy=false; invalidateAll()
        }
        if(editor.lassoMode && lassoTouch(e)) return true
        // Gezinme modu: kalem parmak gibi kaydırır (GVNpen "cursor").
        if(!drawing && isStylus(e,ai) && pointer<0) { penPan(e); return true }
        if(pointer<0 && (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) && canInk(e,ai)) {
            val (page,_)=local(e.getX(ai),e.getY(ai))
            if(page<0) return true
            if(!editor.pageReady(editor.pages[page].id)) { editor.preparePages(listOf(editor.pages[page].id)); return true }
            requestUnbufferedDispatch(e); handler.removeCallbacks(hoverExitTick)
            stopFingerGesture()
            pointer=e.getPointerId(ai); contact=true; contactPage=page; fingerStroke=e.getToolType(ai)==MotionEvent.TOOL_TYPE_FINGER
            shapeInterrupted=false; last=null; smartSnapped=false; preview=null; active=null
            val p=local(e.getX(ai),e.getY(ai),page).second
            start=Geometry.Point(p.x,p.y); motionAnchor=start; lastMotion=SystemClock.uptimeMillis()
            handler.removeCallbacks(smartTick); handler.postDelayed(smartTick,50)
            editor.busy=true
        }
        val idx=if(pointer<0) -1 else e.findPointerIndex(pointer)
        if(idx<0) {
            if(pointer<0 && !isStylus(e,ai) && (0 until e.pointerCount).all { e.getToolType(it)==MotionEvent.TOOL_TYPE_FINGER }) fingerGesture(e)
            return true
        }
        button(e)
        val erase=temp || prefs.tool=="eraser" || e.getToolType(idx)==MotionEvent.TOOL_TYPE_ERASER
        if(prefs.predict && Build.VERSION.SDK_INT>=34 && !erase && prefs.tool=="pen") predictor.record(e) else predicted=null
        if(action==MotionEvent.ACTION_MOVE || action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_DOWN ||
            (action==MotionEvent.ACTION_POINTER_UP && e.getPointerId(ai)==pointer)) {
            for(h in 0..e.historySize) {
                val historical=h<e.historySize
                val x=if(historical) e.getHistoricalX(idx,h) else e.getX(idx)
                val y=if(historical) e.getHistoricalY(idx,h) else e.getY(idx)
                val pr=if(historical) e.getHistoricalPressure(idx,h) else e.getPressure(idx)
                val lp=local(x,y,contactPage).second
                val p=Geometry.Point(lp.x,lp.y,if(prefs.pressure) (pr*1.4f).coerceIn(.15f,1.5f) else 1f)
                hoverScreen=PointF(x,y)
                // Uydurulan şekil hareketin sonucudur; kalkış örnekleri ham çizgiyi geri getirmez.
                if(smartSnapped && !erase) { last=p; continue }
                if(erase) { shapeInterrupted=true; endPen(); erase(contactPage,p) }
                else if(prefs.tool=="pen" || prefs.tool=="highlighter") {
                    if(erasing) { erasing=false; last=null }
                    val a=active
                    if(a==null) {
                        if(action==MotionEvent.ACTION_UP || shapeInterrupted) continue
                        val marker=prefs.tool=="highlighter"
                        active=Geometry.Stroke(prefs.color,if(marker) prefs.marker else prefs.pen,marker).also {
                            it.color=(it.color and 0xffffff) or ((255*prefs.opacity).toInt() shl 24); it.points.add(p) }
                        lastMotion=SystemClock.uptimeMillis()
                    } else if(Geometry.distance(a.points[a.points.size-1],p)>.15f) {
                        a.points.add(p)
                        val anchor=motionAnchor
                        if(anchor==null || Geometry.distance(anchor,p)>3) { motionAnchor=p; lastMotion=SystemClock.uptimeMillis(); preview=null }
                    }
                }
                // Geçici silmeden dönüş yeni şekil başlatmaz; yalnız yeni kalem teması 2B/3B şekil başlatır.
                else if(!shapeInterrupted) runCatching { preview=Geometry.shape(prefs.tool,start,p,prefs.color,prefs.shape) }
                last=p
            }
        }
        if(action==MotionEvent.ACTION_UP || (action==MotionEvent.ACTION_POINTER_UP && e.getPointerId(ai)==pointer)) {
            endPen(); contact=false; pointer=-1; erasing=false; last=null; fingerStroke=false
            handler.removeCallbacks(smartTick); commitWorking()
            // Kalkıştan sonra yaklaşma olayı gelmezse (kalem uzaklaştı) imleç 160 ms sonra gizlenir; gelirse iptal olur.
            handler.removeCallbacks(hoverExitTick); handler.postDelayed(hoverExitTick,160)
        }
        if(prefs.predict && Build.VERSION.SDK_INT>=34 && action==MotionEvent.ACTION_MOVE && !smartSnapped && active?.marker==false) updatePrediction()
        invalidateAll()
        return true
    }
    private fun invalidateAll() { wet.invalidate(); cursor.invalidate(); if(working!=null) finished.invalidate() }

    /** Temasın çizgisini/şeklini çalışma listesine ekler (GVNpen endPen). */
    private fun endPen() {
        predicted=null
        val page=editor.pages.getOrNull(contactPage)
        val shape=preview; val stroke=active
        if(page!=null && (shape!=null || stroke!=null)) {
            val list=workingList()
            (shape ?: listOf(stroke!!)).forEach { g -> if(g.points.isNotEmpty()) list+=RenderedStroke(PageStroke(pageId=page.id,
                tool=if(g.marker) Tool.HIGHLIGHTER else Tool.PEN,color=g.color,width=g.width,samples=g.toSamples())) }
            finished.invalidate()
        }
        preview=null; active=null; smartSnapped=false; activeInk.reset()
    }
    private fun workingList():MutableList<RenderedStroke> {
        val id=editor.pages.getOrNull(contactPage)?.id ?: workingPage ?: error("Temas sayfası yok")
        if(workingPage!=id && working!=null) commitWorking()
        workingPage=id
        return working ?: editor.history.forPage(id).toMutableList().also { working=it }
    }
    /** Temas/silme bitti: değişiklik tek geri alma adımı olarak kaydedilir. */
    private fun commitWorking() {
        val w=working; val id=workingPage; working=null; workingPage=null
        if(w!=null && id!=null) editor.replacePage(id,w)
        editor.busy=contact
        finished.invalidate(); onStateChanged?.invoke()
    }
    /** GVNpen silgisi: önceki örnekten bu örneğe süpürülen kapsül; uzak sıçramada yalnız burada siler. */
    private fun erase(pageIndex:Int,p:Geometry.Point) {
        val page=editor.pages.getOrNull(pageIndex) ?: return
        if(!erasing) { erasing=true; last=null }
        val radius=eraserScreenRadius()/editor.viewport.scale // Ekranda sabit boy: yakınlaştırınca sayfada küçülür.
        val prev=last
        val from=if(prev!=null && Geometry.distance(prev,p)<max(64f,prefs.eraser*4)*density/editor.viewport.scale) prev else p
        if(!editor.pageReady(page.id)) { editor.preparePages(listOf(page.id)); return }
        if(workingPage!=page.id && working!=null) commitWorking()
        workingPage=page.id
        val list=working ?: editor.history.forPage(page.id)
        var out:MutableList<RenderedStroke>?=null
        list.forEachIndexed { k,rs ->
            var result:List<Geometry.Stroke>?=null
            if(rs.data.pageId==page.id) {
                val b=rs.data.bounds(); val reach=radius+rs.data.width
                if(!(b.left-reach>max(from.x,p.x) || b.right+reach<min(from.x,p.x) || b.top-reach>max(from.y,p.y) || b.bottom+reach<min(from.y,p.y))) {
                    val single=listOf(rs.geo)
                    val after=Geometry.erase(single,from,p,radius,prefs.whole)
                    if(after!==single) result=after
                }
            }
            if(result==null) { out?.add(rs); return@forEachIndexed }
            if(out==null) out=ArrayList<RenderedStroke>(list.size).apply { addAll(list.subList(0,k)) }
            result.forEach { piece -> out!!.add(rs.withSamples(piece.toSamples(rs.data.samples.firstOrNull()?.elapsedMillis ?: 0))) }
        }
        out?.let { working=it; finished.invalidate() }
        last=p
    }
    /** Temas iptali (sistem iptali, araç değişimi). Yarım çizgi kaydedilmez; silinenler korunur. */
    fun cancel() {
        active=null; preview=null; predicted=null; smartSnapped=false; activeInk.reset()
        contact=false; pointer=-1; last=null; erasing=false; fingerStroke=false
        handler.removeCallbacks(smartTick); handler.removeCallbacks(buttonTick)
        if(working!=null) commitWorking() else editor.busy=false
        lassoPointer=-1; lassoPage=-1; lassoPath.clear(); dragFrom=null; dragDelta=0f to 0f
        wet.invalidate(); finished.invalidate()
    }
    private fun resetButton() { buttonDown=false; temp=false; erasing=false; handler.removeCallbacks(buttonTick); commitWorking(); onStateChanged?.invoke() }
    /** Sayfayı temizle (GVNpen "Temizle · geri alınabilir"): yalnız görünür sayfanın çizgileri. */
    fun clearCurrentPage() {
        val page=editor.pages.getOrNull(editor.currentPage) ?: return
        cancel()
        if(editor.history.forPage(page.id).isNotEmpty()) editor.replacePage(page.id,emptyList())
        refresh()
    }

    // --- Kalem ucu tahmini (GVNpen: Android 14+ MotionPredictor) ---
    private val predictor = Prediction(context)
    private class Prediction(context: Context) {
        private val predictor: Any? = if(Build.VERSION.SDK_INT>=34) MotionPredictor(context) else null
        private var available=false
        fun record(e:MotionEvent) { if(Build.VERSION.SDK_INT>=34) { val p=predictor as MotionPredictor; available=p.isPredictionAvailable(e.deviceId,e.source); if(available) p.record(e) } }
        fun predict():MotionEvent? = if(Build.VERSION.SDK_INT>=34 && available) (predictor as MotionPredictor).predict(System.nanoTime()+8_000_000L) else null
    }
    private val predictionExpiry = Runnable { predicted=null; wet.invalidate() }
    private fun updatePrediction() {
        predicted=null; handler.removeCallbacks(predictionExpiry)
        val a=active ?: return
        if(a.points.isEmpty()) return
        val e=predictor.predict() ?: return
        try {
            val i=e.findPointerIndex(pointer); if(i<0) return
            val s=Geometry.Stroke(a.color,a.width,false); val lastPoint=a.points[a.points.size-1]; s.points.add(lastPoint)
            for(h in 0..e.historySize) {
                val x=if(h<e.historySize) e.getHistoricalX(i,h) else e.getX(i); val y=if(h<e.historySize) e.getHistoricalY(i,h) else e.getY(i)
                val lp=local(x,y,contactPage).second; s.points.add(Geometry.Point(lp.x,lp.y,lastPoint.p))
            }
            predicted=s; handler.postDelayed(predictionExpiry,32)
        } finally { e.recycle() }
    }

    // ---------------- Parmak / gezinme ----------------
    private val scaleGesture = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (contact || palmGesture) return false
            editor.viewport=editor.viewport.zoomAt(detector.scaleFactor,detector.focusX,detector.focusY)
            refresh(); return true
        }
    })
    private fun penPan(e:MotionEvent) {
        val action=e.actionMasked
        if(action==MotionEvent.ACTION_DOWN) { penPanning=true; fingerFocus=e.x to e.y; velocity?.recycle(); velocity=VelocityTracker.obtain() }
        velocity?.addMovement(e)
        if(action==MotionEvent.ACTION_MOVE && penPanning) fingerFocus?.let { b ->
            editor.viewport=editor.viewport.copy(x=editor.viewport.x+e.x-b.first,y=editor.viewport.y+e.y-b.second); fingerFocus=e.x to e.y; refresh() }
        if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL) { fling(); penPanning=false; stopFingerGesture() }
    }
    private fun fling() {
        velocity?.let { tracker ->
            val config=ViewConfiguration.get(context)
            tracker.computeCurrentVelocity(1000,config.scaledMaximumFlingVelocity.toFloat())
            if(max(kotlin.math.abs(tracker.xVelocity),kotlin.math.abs(tracker.yVelocity)) < config.scaledMinimumFlingVelocity) {
                settleViewport(); return
            }
            val v=editor.viewport; layoutPages()
            val low=clamp(v.copy(x=-Float.MAX_VALUE,y=-Float.MAX_VALUE))
            val high=clamp(v.copy(x=Float.MAX_VALUE,y=Float.MAX_VALUE))
            // Hareket serbesttir; savrulma belge dışındaki boşluğu daha da büyütmez.
            scroller.fling(v.x.toInt(),v.y.toInt(),tracker.xVelocity.toInt(),tracker.yVelocity.toInt(),
                min(low.x,v.x).toInt(),max(high.x,v.x).toInt(),min(low.y,v.y).toInt(),max(high.y,v.y).toInt())
            flingPending=true
            postInvalidateOnAnimation()
        } ?: settleViewport()
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
        if (count!=fingerCount || action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN || action==MotionEvent.ACTION_POINTER_UP) {
            fingerFocus=focus; fingerCount=count
        } else if (action==MotionEvent.ACTION_MOVE) {
            fingerFocus?.let { before ->
                editor.viewport=editor.viewport.copy(x=editor.viewport.x+focus.first-before.first,y=editor.viewport.y+focus.second-before.second)
                refresh()
            }
            fingerFocus=focus
        }
        if (action==MotionEvent.ACTION_UP) { fling(); stopFingerGesture() }
    }
    private fun stopFingerGesture() { fingerFocus=null; fingerCount=0; velocity?.recycle(); velocity=null }

    // ---------------- Kement seçimi (sayfa yerel koordinatı) ----------------
    private var lassoPointer=-1
    private var lassoPage=-1
    private val lassoPath=ArrayList<PenGeometry.P>()
    private var dragFrom:Pair<Float,Float>?=null
    private var dragDelta=0f to 0f
    private val dashPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { style=Paint.Style.STROKE; color=0xff4f57b0.toInt() }
    private val selectionFill=Paint().apply { style=Paint.Style.FILL; color=0x144f57b0 }
    private fun selectionBox():Pair<String,RectF>? {
        val chosen=editor.history.selected(editor.selection)
        if(chosen.isEmpty()) return null
        val box=RectF(chosen.first().data.bounds())
        chosen.forEach { val b=it.data.bounds(); val w=it.data.width/2; box.union(b.left-w,b.top-w,b.right+w,b.bottom+w) }
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
    private fun lassoTouch(e:MotionEvent):Boolean {
        val action=e.actionMasked; val ai=e.actionIndex
        if(lassoPointer<0) {
            if(!(action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) || !isStylus(e,ai)) return false
            val (i,local)=local(e.getX(ai),e.getY(ai))
            if(i<0) { editor.clearSelection(); refresh(); return true }
            lassoPointer=e.getPointerId(ai); lassoPage=i; lassoPath.clear(); dragFrom=null; dragDelta=0f to 0f
            val sel=selectionBox()
            if(sel!=null && sel.first==editor.pages[i].id && sel.second.contains(local.x,local.y)) dragFrom=local.x to local.y
            else { editor.clearSelection(); lassoPath+=PenGeometry.P(local.x,local.y) }
            stopFingerGesture(); refresh(); return true
        }
        val index=e.findPointerIndex(lassoPointer)
        if(index<0) return true
        val local=local(e.getX(index),e.getY(index),lassoPage).second
        when {
            action==MotionEvent.ACTION_MOVE -> {
                val from=dragFrom
                if(from!=null) dragDelta=local.x-from.first to local.y-from.second
                else {
                    for(h in 0 until e.historySize) { val hl=local(e.getHistoricalX(index,h),e.getHistoricalY(index,h),lassoPage).second; lassoPath+=PenGeometry.P(hl.x,hl.y) }
                    lassoPath+=PenGeometry.P(local.x,local.y)
                }
                finished.invalidate()
            }
            (action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_POINTER_UP) && e.getPointerId(ai)==lassoPointer -> {
                // Kalemin kalktığı son konum da sayılır.
                dragFrom?.let { from -> dragDelta=local.x-from.first to local.y-from.second }
                if(dragFrom!=null) editor.moveSelection(dragDelta.first,dragDelta.second)
                else {
                    lassoPath+=PenGeometry.P(local.x,local.y)
                    val pageId=editor.pages[lassoPage].id
                    editor.selection=editor.history.forPage(pageId).filter { PenGeometry.selectedBy(it.data.samples.map { s -> s.toP() },lassoPath) }.map { it.data.id }.toSet()
                }
                lassoPointer=-1; lassoPage=-1; lassoPath.clear(); dragFrom=null; dragDelta=0f to 0f
                refresh()
            }
        }
        return true
    }

    override fun onDetachedFromWindow() {
        stopNavigation()
        handler.removeCallbacksAndMessages(null)
        scroller.forceFinished(true)
        if(contact) endPen()
        contact=false; pointer=-1
        if(working!=null) commitWorking()
        editor.busy=false
        inkTiles.close(); activeInk.release()
        super.onDetachedFromWindow()
    }
    companion object {
        /** Sayfalar arası boşluk (belge birimi). */
        const val GAP=32f
        /** Bundan geniş temas avuç sayılır ve kaydırma/yazı yapmaz. */
        const val PALM_DP=90f
    }
}
