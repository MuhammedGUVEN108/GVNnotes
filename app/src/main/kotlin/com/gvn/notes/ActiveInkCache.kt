package com.gvn.notes

import android.graphics.*
import com.gvn.notes.pen.Geometry

/** Uzun kalem çizgisinin kesinleşen başını biriktirir; yalnız son 128 nokta canlı çizilir. */
internal class ActiveInkCache {
    private var bitmap:Bitmap?=null
    private var target:Geometry.Stroke?=null
    private var segments=0
    private var smoothBefore=false
    private val previous=FloatArray(9)
    private val current=FloatArray(9)
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val path=Path()
    var liveSegments=0; private set
    fun drawHead(canvas:Canvas,stroke:Geometry.Stroke,matrix:Matrix,w:Int,h:Int,smooth:Boolean) {
        matrix.getValues(current)
        val old=bitmap
        if(target!==stroke || old==null || old.width!=w || old.height!=h || smoothBefore!=smooth ||
            !current.contentEquals(previous) || segments>=stroke.points.size) {
            target=stroke; segments=0; smoothBefore=smooth; current.copyInto(previous)
            if(old!=null && old.width==w && old.height==h) old.eraseColor(Color.TRANSPARENT) else bitmap=null
        }
        val settled=((stroke.points.size-2).coerceAtLeast(0)/CHUNK)*CHUNK
        if(settled>segments && w>0 && h>0) {
            val image=bitmap ?: Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888).also { bitmap=it }
            val cache=Canvas(image); cache.concat(matrix)
            drawRange(cache,stroke,segments+1,settled,smooth,segments==0)
            segments=settled
        }
        if(segments>0) bitmap?.let { canvas.drawBitmap(it,0f,0f,null) }
    }
    fun drawTail(canvas:Canvas,stroke:Geometry.Stroke,smooth:Boolean) {
        liveSegments=(stroke.points.size-1-segments).coerceAtLeast(0)
        drawRange(canvas,stroke,segments+1,stroke.points.lastIndex,smooth,segments==0)
    }
    /** GVNpen ile aynı basınç ve eğri; parça sınırında fazladan nokta/yarı saydam ek yeri oluşturmaz. */
    private fun drawRange(canvas:Canvas,stroke:Geometry.Stroke,from:Int,to:Int,smooth:Boolean,dot:Boolean) {
        if(stroke.points.isEmpty()) return
        paint.color=stroke.color; paint.style=Paint.Style.STROKE
        paint.strokeCap=Paint.Cap.ROUND; paint.strokeJoin=Paint.Join.ROUND
        val points=stroke.points
        if(dot) { val p=points[0]; paint.strokeWidth=stroke.width*p.p; canvas.drawPoint(p.x,p.y,paint) }
        for(i in from..to) {
            val previous=points[i-1]; val b=points[i]
            val ax=if(smooth && i>1) (previous.x+b.x)/2 else previous.x
            val ay=if(smooth && i>1) (previous.y+b.y)/2 else previous.y
            paint.strokeWidth=stroke.width*(previous.p+b.p)/2
            if(smooth && i<points.lastIndex) {
                val next=points[i+1]
                path.reset(); path.moveTo(ax,ay); path.quadTo(b.x,b.y,(b.x+next.x)/2,(b.y+next.y)/2)
                canvas.drawPath(path,paint)
            } else canvas.drawLine(ax,ay,b.x,b.y,paint)
        }
    }
    fun reset() { target=null; segments=0; liveSegments=0 }
    fun release() { reset(); bitmap=null }
    companion object { private const val CHUNK=128 }
}
