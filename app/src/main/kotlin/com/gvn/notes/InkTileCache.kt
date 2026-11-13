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
internal class InkTileCache(private val budget:Long=64L*1024*1024,private val async:Boolean=false,
    private val onReady:()->Unit={},private val onError:(Throwable)->Unit={}) {
    private data class Key(val page:String,val level:Int,val x:Int,val y:Int)
    private data class State(var list:List<RenderedStroke>,var shown:List<RenderedStroke>,val smooth:Boolean,var generation:Long)
    private val tiles=LinkedHashMap<Key,Bitmap>(16,.75f,true)
    private val states=HashMap<String,State>()
    private var used=0L
    private val bitmapPaint=Paint(Paint.FILTER_BITMAP_FLAG)
    private val source=Rect(GUTTER,GUTTER,GUTTER+SIDE,GUTTER+SIDE)
    private val destination=RectF()
    var rasterizedStrokes=0L; private set
    val retainedBytes get()=used
    private val pending=HashSet<Key>()
    private val main=android.os.Handler(android.os.Looper.getMainLooper())
    @Volatile private var epoch=0L
    @Volatile var closed=false; private set
    private var version=0L
    private var viewKey:Any?=null
    private var frameMissing=0
    val settled get()=pending.isEmpty() && frameMissing==0
    fun beginFrame(key:Any) { if(viewKey!=key) { viewKey=key; epoch++ }; frameMissing=0 }

    private fun resolution(level:Int)=2.0.pow(level).toFloat()
    private fun touches(stroke:RenderedStroke,key:Key):Boolean {
        val r=resolution(key.level); val pad=stroke.data.width*1.5f+GUTTER/r+1f
        val b=stroke.data.bounds()
        return b.left-pad<=(key.x+1)*SIDE/r && b.right+pad>=key.x*SIDE/r &&
            b.top-pad<=(key.y+1)*SIDE/r && b.bottom+pad>=key.y*SIDE/r
    }
    private fun drawInto(bitmap:Bitmap,key:Key,strokes:List<RenderedStroke>,smooth:Boolean):Long {
        val paint=Paint(Paint.ANTI_ALIAS_FLAG); val path=Path(); var count=0L
        val r=resolution(key.level)
        val canvas=Canvas(bitmap)
        canvas.translate(GUTTER-key.x*SIDE.toFloat(),GUTTER-key.y*SIDE.toFloat()); canvas.scale(r,r)
        val clip=RectF((key.x*SIDE-GUTTER)/r,(key.y*SIDE-GUTTER)/r,
            ((key.x+1)*SIDE+GUTTER)/r,((key.y+1)*SIDE+GUTTER)/r)
        for(stroke in strokes) if(touches(stroke,key)) {
            PenRenderer.drawStroke(canvas,stroke.geo,0,stroke.data.samples.size,paint,smooth,path,clip)
            count++
        }
        return count
    }
    private fun sync(page:String,strokes:List<RenderedStroke>,smooth:Boolean) {
        val old=states[page]
        if(old!=null && old.smooth==smooth && old.list===strokes && old.shown.size==strokes.size &&
            (strokes.isEmpty() || old.shown.last()===strokes.last())) return
        if(old==null || old.smooth!=smooth) {
            removePage(page); states[page]=State(strokes,strokes.toList(),smooth,++version); return
        }
        var common=0
        while(common<min(old.shown.size,strokes.size) && old.shown[common]===strokes[common]) common++
        if(common==old.shown.size && common==strokes.size) { old.list=strokes; return }
        old.generation=++version
        if(common==old.shown.size) {
            val added=strokes.subList(common,strokes.size)
            // Normal yazmada tamamlanmış sayfa yeniden çizilmez; yalnız yeni çizgi eklenir.
            val ready=added.all { (it.data.samples as SampleHandle).ready() }
            if(ready) { for((key,bitmap) in tiles) if(key.page==page) rasterizedStrokes+=drawInto(bitmap,key,added,smooth) }
            else removePageTiles(page)
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
    fun draw(canvas:Canvas,page:Page,strokes:List<RenderedStroke>,visible:RectF,scale:Float,smooth:Boolean,allowRasterize:Boolean=true) {
        sync(page.id,strokes,smooth)
        if(strokes.isEmpty()) return
        // İlk kısa çizgi canlı katmandan kalktığı karede hazır olmalı. Büyük sayfalar IO/raster işçisinde kalır.
        val immediate=allowRasterize && strokes.sumOf { it.data.samples.size.toLong() }<=4096 &&
            strokes.all { (it.data.samples as SampleHandle).ready() }
        val level=ceil(log2(scale.coerceIn(.125f,8f))).toInt()
        val r=resolution(level)
        val x0=floor(max(0f,visible.left)*r/SIDE).toInt()
        val y0=floor(max(0f,visible.top)*r/SIDE).toInt()
        val x1=ceil(min(page.width,visible.right)*r/SIDE).toInt()
        val y1=ceil(min(page.height,visible.bottom)*r/SIDE).toInt()
        for(y in y0 until y1) for(x in x0 until x1) {
            val key=Key(page.id,level,x,y)
            var bitmap=tiles[key]
            if(bitmap==null) {
                if(async && !immediate) { frameMissing++; if(allowRasterize) schedule(key,smooth); fallback(canvas,key) }
                else if(allowRasterize) {
                    bitmap=Bitmap.createBitmap(SIDE+GUTTER*2,SIDE+GUTTER*2,Bitmap.Config.ARGB_8888)
                    rasterizedStrokes+=drawInto(bitmap,key,strokes,smooth); put(key,bitmap)
                } else frameMissing++
            }
            bitmap?.let { drawTile(canvas,key,it) }
        }
    }
    private fun put(key:Key,bitmap:Bitmap) {
        used-=tiles.remove(key)?.byteCount ?: 0; tiles[key]=bitmap; used+=bitmap.byteCount
        val iterator=tiles.entries.iterator()
        while(used>budget && iterator.hasNext()) { used-=iterator.next().value.byteCount; iterator.remove() }
    }
    private fun drawTile(canvas:Canvas,key:Key,bitmap:Bitmap) {
        val r=resolution(key.level)
        destination.set(key.x*SIDE/r,key.y*SIDE/r,(key.x+1)*SIDE/r,(key.y+1)*SIDE/r)
        canvas.drawBitmap(bitmap,source,destination,bitmapPaint)
    }
    private fun fallback(canvas:Canvas,wanted:Key) {
        val level=tiles.keys.filter { it.page==wanted.page }.minByOrNull { abs(it.level-wanted.level) }?.level ?: return
        val r=resolution(wanted.level)
        val save=canvas.save(); canvas.clipRect(wanted.x*SIDE/r,wanted.y*SIDE/r,(wanted.x+1)*SIDE/r,(wanted.y+1)*SIDE/r)
        val oldScale=resolution(level)
        val left=wanted.x*SIDE/r; val top=wanted.y*SIDE/r
        val right=(wanted.x+1)*SIDE/r; val bottom=(wanted.y+1)*SIDE/r
        for((key,bitmap) in tiles) if(key.page==wanted.page && key.level==level &&
            key.x*SIDE/oldScale<right && (key.x+1)*SIDE/oldScale>left &&
            key.y*SIDE/oldScale<bottom && (key.y+1)*SIDE/oldScale>top) drawTile(canvas,key,bitmap)
        canvas.restoreToCount(save)
    }
    private fun schedule(key:Key,smooth:Boolean) {
        if(closed || pending.size>=96 || !pending.add(key)) return
        val state=states[key.page] ?: run { pending.remove(key); return }
        val generation=state.generation; val requestedEpoch=epoch; val strokes=state.shown
        WORKER.execute {
            var image:Bitmap?=null; var work=0L; var failure:Throwable?=null
            try {
                if(!closed && epoch==requestedEpoch) {
                    val rendered=Bitmap.createBitmap(SIDE+GUTTER*2,SIDE+GUTTER*2,Bitmap.Config.ARGB_8888)
                    image=rendered; work=drawInto(rendered,key,strokes,smooth)
                }
            } catch(e:Exception) { failure=e }
            main.post {
                pending.remove(key)
                if(!closed) {
                    if(states[key.page]?.generation==generation && image!=null) { put(key,image!!); rasterizedStrokes+=work }
                    failure?.let(onError); onReady()
                }
            }
        }
    }
    private fun removePageTiles(page:String) {
        val iterator=tiles.entries.iterator()
        while(iterator.hasNext()) { val entry=iterator.next(); if(entry.key.page==page) { used-=entry.value.byteCount; iterator.remove() } }
    }
    private fun removePage(page:String) {
        removePageTiles(page)
        states.remove(page)
    }
    fun retainPages(ids:Set<String>) { states.keys.toList().filter { it !in ids }.forEach { removePage(it) } }
    fun clear() { tiles.clear(); states.clear(); used=0 }
    fun close() { closed=true; epoch++; clear() }
    companion object {
        private const val SIDE=512; private const val GUTTER=2
        private val WORKER=java.util.concurrent.Executors.newSingleThreadExecutor { Thread(it,"gvnnotes-ink-tiles").apply { isDaemon=true } }
    }
}
