package com.gvn.notes

import android.graphics.*
import com.gvn.notes.core.Page
import com.gvn.notes.pen.PenRenderer
import java.util.Collections
import java.util.IdentityHashMap
import kotlin.math.*

/**
 * GVNpen'in tamamlanmış mürekkebi yeniden kullanma yaklaşımı, kalıcı çok sayfalı belgeye uyarlandı.
 * Yalnız görünen sayfa parçaları çizilir; 100 sayfanın tamamı için bitmap ayrılmaz.
 * Yakınlaştırma/kaydırma aynı parçaları kullanır; bellek bütçesini aşan eskiler atılır.
 */
internal class InkTileCache(private val budget:Long=64L*1024*1024) {
    private data class Key(val page:String,val level:Int,val x:Int,val y:Int)
    private data class State(var list:List<RenderedStroke>,var shown:List<RenderedStroke>,val smooth:Boolean)
    private val tiles=LinkedHashMap<Key,Bitmap>(16,.75f,true)
    private val states=HashMap<String,State>()
    private var used=0L
    private val paint=Paint(Paint.ANTI_ALIAS_FLAG)
    private val bitmapPaint=Paint(Paint.FILTER_BITMAP_FLAG)
    private val path=Path()
    private val source=Rect(GUTTER,GUTTER,GUTTER+SIDE,GUTTER+SIDE)
    private val destination=RectF()
    var rasterizedStrokes=0L; private set
    val retainedBytes get()=used

    private fun resolution(level:Int)=2.0.pow(level).toFloat()
    private fun touches(stroke:RenderedStroke,key:Key):Boolean {
        val r=resolution(key.level); val pad=stroke.data.width*1.5f+GUTTER/r+1f
        val b=stroke.data.bounds()
        return b.left-pad<=(key.x+1)*SIDE/r && b.right+pad>=key.x*SIDE/r &&
            b.top-pad<=(key.y+1)*SIDE/r && b.bottom+pad>=key.y*SIDE/r
    }
    private fun drawInto(bitmap:Bitmap,key:Key,strokes:List<RenderedStroke>,smooth:Boolean) {
        val r=resolution(key.level)
        val canvas=Canvas(bitmap)
        canvas.translate(GUTTER-key.x*SIDE.toFloat(),GUTTER-key.y*SIDE.toFloat()); canvas.scale(r,r)
        val clip=RectF((key.x*SIDE-GUTTER)/r,(key.y*SIDE-GUTTER)/r,
            ((key.x+1)*SIDE+GUTTER)/r,((key.y+1)*SIDE+GUTTER)/r)
        for(stroke in strokes) if(touches(stroke,key)) {
            PenRenderer.drawStroke(canvas,stroke.geo,0,stroke.data.samples.size,paint,smooth,path,clip)
            rasterizedStrokes++
        }
    }
    private fun sync(page:String,strokes:List<RenderedStroke>,smooth:Boolean) {
        val old=states[page]
        if(old!=null && old.smooth==smooth && old.list===strokes && old.shown.size==strokes.size &&
            (strokes.isEmpty() || old.shown.last()===strokes.last())) return
        if(old==null || old.smooth!=smooth) {
            removePage(page); states[page]=State(strokes,strokes.toList(),smooth); return
        }
        var common=0
        while(common<min(old.shown.size,strokes.size) && old.shown[common]===strokes[common]) common++
        if(common==old.shown.size && common==strokes.size) { old.list=strokes; return }
        if(common==old.shown.size) {
            val added=strokes.subList(common,strokes.size)
            // Normal yazmada tamamlanmış sayfa yeniden çizilmez; yalnız yeni çizgi eklenir.
            for((key,bitmap) in tiles) if(key.page==page) drawInto(bitmap,key,added,smooth)
        } else {
            val before=Collections.newSetFromMap(IdentityHashMap<RenderedStroke,Boolean>()).apply { addAll(old.shown) }
            val after=Collections.newSetFromMap(IdentityHashMap<RenderedStroke,Boolean>()).apply { addAll(strokes) }
            val changed=old.shown.filter { it !in after }+strokes.filter { it !in before }
            val iterator=tiles.entries.iterator()
            // Silgi/geri alma/renk/kement yalnız değişen çizgilerin değdiği parçaları geçersiz kılar.
            while(iterator.hasNext()) {
                val entry=iterator.next()
                if(entry.key.page==page && changed.any { touches(it,entry.key) }) {
                    used-=entry.value.byteCount; iterator.remove()
                }
            }
        }
        old.list=strokes; old.shown=strokes.toList()
    }
    fun draw(canvas:Canvas,page:Page,strokes:List<RenderedStroke>,visible:RectF,scale:Float,smooth:Boolean) {
        sync(page.id,strokes,smooth)
        if(strokes.isEmpty()) return
        val level=ceil(log2(scale.coerceIn(.125f,8f))).toInt()
        val r=resolution(level)
        val x0=floor(max(0f,visible.left)*r/SIDE).toInt()
        val y0=floor(max(0f,visible.top)*r/SIDE).toInt()
        val x1=ceil(min(page.width,visible.right)*r/SIDE).toInt()
        val y1=ceil(min(page.height,visible.bottom)*r/SIDE).toInt()
        for(y in y0 until y1) for(x in x0 until x1) {
            val key=Key(page.id,level,x,y)
            val bitmap=tiles[key] ?: Bitmap.createBitmap(SIDE+GUTTER*2,SIDE+GUTTER*2,Bitmap.Config.ARGB_8888).also {
                drawInto(it,key,strokes,smooth); tiles[key]=it; used+=it.byteCount
                val iterator=tiles.entries.iterator()
                while(used>budget && iterator.hasNext()) { used-=iterator.next().value.byteCount; iterator.remove() }
            }
            destination.set(x*SIDE/r,y*SIDE/r,(x+1)*SIDE/r,(y+1)*SIDE/r)
            canvas.drawBitmap(bitmap,source,destination,bitmapPaint)
        }
    }
    private fun removePage(page:String) {
        val iterator=tiles.entries.iterator()
        while(iterator.hasNext()) { val entry=iterator.next(); if(entry.key.page==page) { used-=entry.value.byteCount; iterator.remove() } }
        states.remove(page)
    }
    fun retainPages(ids:Set<String>) { states.keys.toList().filter { it !in ids }.forEach { removePage(it) } }
    fun clear() { tiles.clear(); states.clear(); used=0 }
    companion object { private const val SIDE=512; private const val GUTTER=2 }
}
