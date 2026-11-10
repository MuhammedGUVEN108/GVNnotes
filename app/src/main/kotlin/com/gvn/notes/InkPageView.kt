package com.gvn.notes

import android.content.Context
import android.annotation.SuppressLint
import android.os.Build
import android.graphics.Canvas
import android.graphics.Matrix
import android.graphics.Paint
import android.view.MotionEvent
import android.view.ScaleGestureDetector
import android.view.View
import android.widget.FrameLayout
import androidx.ink.authoring.InProgressStrokeId
import androidx.ink.authoring.InProgressStrokesView
import androidx.ink.authoring.InProgressStrokesFinishedListener
import androidx.ink.strokes.Stroke
import androidx.ink.brush.Brush
import androidx.ink.brush.StockBrushes
import androidx.ink.rendering.android.canvas.CanvasStrokeRenderer
import androidx.input.motionprediction.MotionEventPredictor
import com.gvn.notes.core.*
import kotlin.math.min

/** Sayfa içi yüzey. WindowManager, overlay servisi veya dosya erişimi yoktur. */
@SuppressLint("ViewConstructor") // Compose factory supplies the editor; this view is not inflated from XML.
class InkPageView(context: Context, private val editor: EditorState) : FrameLayout(context) {
    private val live = InProgressStrokesView(context)
    private val renderer = CanvasStrokeRenderer.create()
    private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
    private val viewMatrix = Matrix()
    private val pageBounds = android.graphics.RectF()
    private val predictor = MotionEventPredictor.newInstance(this)
    private var pointerId = -1
    private var strokeId: InProgressStrokeId? = null
    private var current: Pending? = null
    private val pending = linkedMapOf<InProgressStrokeId, Pending>()
    private var erasePreview: List<RenderedStroke>? = null
    private var lastErase: Pair<Float,Float>? = null
    private var fingerFocus: Pair<Float,Float>? = null
    private val finished = object : View(context) {
        override fun onDraw(canvas: Canvas) {
            val matrix = pageToScreen()
            canvas.drawColor(0xffe5ebef.toInt())
            val save=canvas.save()
            canvas.concat(matrix)
            paint.color=android.graphics.Color.WHITE; paint.style=Paint.Style.FILL
            canvas.drawRect(0f,0f,editor.page.width,editor.page.height,paint)
            canvas.restoreToCount(save)
            val clip=canvas.save()
            val bounds=pageBounds.apply { set(0f,0f,editor.page.width,editor.page.height) }
            matrix.mapRect(bounds); canvas.clipRect(bounds)
            // Ink'in transform parametresi geometri/kalite bilgisidir; Canvas'ı dönüştürmez.
            // Sayfa koordinatındaki çizgileri gerçekten ekrana taşımak için aynı matrisi uygula.
            canvas.concat(matrix)
            (erasePreview ?: editor.history.items).forEach { renderer.draw(canvas,it.ink,matrix) }
            canvas.restoreToCount(clip)
        }
    }
    private data class Pending(val tool: Tool,val color: Int,val size: Float,
        val startTime: Long,val samples: MutableList<Sample> = mutableListOf())
    private val scaleGesture = ScaleGestureDetector(context, object: ScaleGestureDetector.SimpleOnScaleGestureListener() {
        override fun onScale(detector: ScaleGestureDetector): Boolean {
            if (editor.busy) return false
            editor.viewport=editor.viewport.zoomAt(detector.scaleFactor,detector.focusX,detector.focusY)
            refresh(); return true
        }
    })
    init {
        contentDescription="Not sayfası; S Pen ile yazın, iki parmakla yakınlaştırın"
        addView(finished,LayoutParams(-1,-1))
        addView(live,LayoutParams(-1,-1))
        live.addFinishedStrokesListener(object: InProgressStrokesFinishedListener {
          override fun onStrokesFinished(strokes: Map<InProgressStrokeId, Stroke>) = adopt(strokes)
        })
    }
    /** Ink'in teslim ettiği bitmiş çizgileri sayfa verisine alır; aynı UI döngüsünde canlı yüzeyden kaldırır. */
    private fun adopt(strokes: Map<InProgressStrokeId, Stroke>) {
        var next=editor.history.items
        strokes.forEach { (id, ink) ->
            pending.remove(id)?.let { p ->
                next += RenderedStroke(PageStroke(pageId=editor.page.id,tool=p.tool,
                    color=p.color,width=p.size,samples=p.samples.toList()),ink)
                editor.replace(next) // Ink birden çok çizgiyi beraber teslim etse de her çizgi ayrı geri alınır.
            }
        }
        // Aynı UI döngüsünde sahipliği devral; fosforlu kalem iki kez çizilmesin.
        finished.invalidate()
        if(strokes.isNotEmpty()) live.removeFinishedStrokes(strokes.keys)
        editor.busy=pointerId != -1 || pending.isNotEmpty()
    }
    /**
     * Ink teslimi bazı durumlarda (ör. teslimden hemen sonra yüzey yeniden boyutlanınca) kendiliğinden
     * gelmeyebilir. Kalem kalktıktan sonra teslim gecikirse bitmiş çizgileri genel API ile kendimiz alırız;
     * böylece ekran "Çiziliyor" durumunda kilitli kalmaz ve yazı kaybolmaz.
     */
    private val handoffWatchdog=Runnable {
        if(pointerId==-1 && pending.isNotEmpty()) {
            android.util.Log.w("GVNnotes","Ink teslimi gecikti; ${pending.size} bitmiş çizgi elle alınıyor.")
            claimFinished()
        }
        if(pointerId==-1 && pending.isNotEmpty()) scheduleWatchdog()
    }
    private fun scheduleWatchdog() { removeCallbacks(handoffWatchdog); postDelayed(handoffWatchdog,HANDOFF_TIMEOUT_MS) }
    private fun claimFinished() = adopt(live.getFinishedStrokes().filterKeys { it in pending })
    fun refresh() {
        // Canlı yüzey her zaman tüm alanı kaplar: zoom/kaydırmada boyutu değişirse Ink teslimi duraklı kalabiliyor.
        // Masa alanında ıslak mürekkep görünmesin diye kâğıt dışı maskeyle gizlenir.
        if(width>0 && height>0) {
            val bounds=pageBounds.apply { set(0f,0f,editor.page.width,editor.page.height) }
            pageToScreen().mapRect(bounds)
            live.maskPath=android.graphics.Path().apply {
                fillType=android.graphics.Path.FillType.EVEN_ODD
                addRect(0f,0f,width.toFloat(),height.toFloat(),android.graphics.Path.Direction.CW)
                addRect(bounds,android.graphics.Path.Direction.CW)
            }
        }
        finished.invalidate()
    }
    private fun pageToScreen() = viewMatrix.apply {
        val v=editor.viewport
        setScale(v.scale,v.scale)
        postTranslate(v.x,v.y)
    }
    override fun onSizeChanged(w:Int,h:Int,oldw:Int,oldh:Int) {
        super.onSizeChanged(w,h,oldw,oldh)
        if (!editor.fitted && w>0 && h>0) {
            val margin=20*resources.displayMetrics.density
            val s=min((w-margin*2)/editor.page.width,(h-margin*2)/editor.page.height).coerceAtLeast(.1f)
            editor.viewport=Viewport(s,(w-editor.page.width*s)/2,(h-editor.page.height*s)/2)
            editor.fitted=true
        }
        refresh()
    }
    override fun onInterceptTouchEvent(event:MotionEvent) = true
    private fun isPen(e:MotionEvent,index:Int):Boolean = when(e.getToolType(index)) {
        MotionEvent.TOOL_TYPE_STYLUS,MotionEvent.TOOL_TYPE_ERASER -> true
        MotionEvent.TOOL_TYPE_MOUSE -> editor.mouseInput
        else -> false // Avuç ve tek parmak hiçbir zaman mürekkep üretmez.
    }
    @SuppressLint("ClickableViewAccessibility") // This surface handles handwriting/pan gestures, not click actions; tools are accessible Compose buttons.
    override fun onTouchEvent(event:MotionEvent):Boolean {
        if(!editor.ready || (editor.navigationPending && pointerId<0)) return true // Yükleme/çıkış sırasında yeni çizim başlamaz.
        predictor.record(event)
        val action=event.actionMasked
        val ai=event.actionIndex
        val canceledPointer=Build.VERSION.SDK_INT>=33 && event.flags and MotionEvent.FLAG_CANCELED != 0 &&
            (pointerId<0 || event.getPointerId(ai)==pointerId)
        if (action==MotionEvent.ACTION_CANCEL || canceledPointer) {
            cancelGesture(event); return true
        }
        // Önceki çizginin asenkron teslimini beklemek hızlı el yazısında sonraki harfi düşürür.
        // Pending çizgiler varken kaleme izin ver; araç değişimi ve zoom yine kilitli kalır.
        if (pointerId == -1 &&
            (action==MotionEvent.ACTION_DOWN || action==MotionEvent.ACTION_POINTER_DOWN) && isPen(event,ai)) {
            val point=editor.viewport.toPage(event.getX(ai),event.getY(ai))
            if (point.first !in 0f..editor.page.width || point.second !in 0f..editor.page.height) return true
            requestUnbufferedDispatch(event)
            pointerId=event.getPointerId(ai); editor.busy=true; fingerFocus=null
            // Yan düğme eşlemesi henüz ürün kararı değil; bu prototipte sadece araç seçicisi.
            val tool=if(event.getToolType(ai)==MotionEvent.TOOL_TYPE_ERASER) Tool.ERASER else editor.tool
            if(tool==Tool.ERASER && pending.isNotEmpty()) claimFinished() // Silmeden önce bitmiş mürekkebi sayfaya al.
            if(tool==Tool.ERASER && pending.isNotEmpty()) {
                pointerId=-1; editor.busy=true; scheduleWatchdog(); return true // Yarım çizgi üzerinde silme yarışı oluşturma.
            }
            val width=if(tool==Tool.HIGHLIGHTER) editor.size*5 else editor.size
            val color=if(tool==Tool.HIGHLIGHTER) (editor.color and 0x00ffffff) or 0x66000000 else editor.color
            current=Pending(tool,color,width,event.eventTime)
            if(tool==Tool.ERASER) erasePreview=editor.history.items else {
                val inverse=Matrix(); pageToScreen().invert(inverse)
                val family=if(tool==Tool.HIGHLIGHTER) StockBrushes.highlighter() else StockBrushes.pressurePen()
                val brush=Brush.createWithColorIntArgb(family,color,width,.1f)
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
                } else strokeId?.let { live.finishStroke(event,pointerId,it) }
                pointerId=-1; strokeId=null; current=null; lastErase=null
                editor.busy=pending.isNotEmpty(); refresh()
                if(pending.isNotEmpty()) scheduleWatchdog()
            }
            return true // Kalem varken avuç/parmak zoom yapamaz.
        }
        if(editor.busy) return true
        if ((0 until event.pointerCount).any { event.getToolType(it)!=MotionEvent.TOOL_TYPE_FINGER }) return true
        scaleGesture.onTouchEvent(event)
        if(event.pointerCount>=2 && action==MotionEvent.ACTION_MOVE) {
            var x=0f; var y=0f
            for(i in 0 until event.pointerCount) { x+=event.getX(i); y+=event.getY(i) }
            val focus=x/event.pointerCount to y/event.pointerCount
            fingerFocus?.let { before ->
                editor.viewport=editor.viewport.copy(x=editor.viewport.x+focus.first-before.first,y=editor.viewport.y+focus.second-before.second)
                refresh()
            }
            fingerFocus=focus
        } else fingerFocus=null
        return true
    }
    private fun recordRealSamples(e:MotionEvent,i:Int) {
        val p=current ?: return
        for(h in 0..e.historySize) {
            val historical=h<e.historySize
            val x=if(historical)e.getHistoricalX(i,h) else e.getX(i)
            val y=if(historical)e.getHistoricalY(i,h) else e.getY(i)
            val point=editor.viewport.toPage(x,y)
            val time=if(historical)e.getHistoricalEventTime(h) else e.eventTime
            val pressure=if(historical)e.getHistoricalPressure(i,h) else e.getPressure(i)
            fun axis(axis:Int)=if(historical)e.getHistoricalAxisValue(axis,i,h) else e.getAxisValue(axis,i)
            val sample=Sample(point.first,point.second,pressure,axis(MotionEvent.AXIS_TILT),axis(MotionEvent.AXIS_ORIENTATION),time-p.startTime)
            if(p.samples.lastOrNull()!=sample) p.samples+=sample
            if(p.tool==Tool.ERASER) {
                val from=lastErase ?: point
                erasePreview=(erasePreview ?: editor.history.items).filterNot { hitStroke(it.data,from,point,editor.size*3) }
                lastErase=point
            }
        }
        if(p.tool==Tool.ERASER) refresh()
    }
    private fun cancelGesture(e:MotionEvent) {
        strokeId?.let { live.cancelStroke(it,e); pending.remove(it) }
        pointerId=-1; strokeId=null; current=null; erasePreview=null; lastErase=null; fingerFocus=null
        editor.busy=pending.isNotEmpty(); refresh()
    }
    override fun onDetachedFromWindow() {
        removeCallbacks(handoffWatchdog)
        claimFinished() // Kalemi kalkmış ama henüz teslim edilmemiş çizgiler ekran kapanırken kaybolmasın.
        live.cancelUnfinishedStrokes()
        pending.clear(); pointerId=-1; current=null; strokeId=null; erasePreview=null
        editor.busy=false
        super.onDetachedFromWindow()
    }
    private companion object { const val HANDOFF_TIMEOUT_MS=700L }
}
