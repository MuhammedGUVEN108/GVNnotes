package com.gvn.notes

import android.graphics.*
import androidx.test.ext.junit.runners.AndroidJUnit4
import com.gvn.notes.core.*
import com.gvn.notes.pen.Geometry
import com.gvn.notes.pen.PenRenderer
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import kotlin.math.sin

@RunWith(AndroidJUnit4::class)
class InkCacheDeviceTest {
    private fun compare(a:Bitmap,b:Bitmap,tolerance:Double=1.5) {
        val aa=IntArray(a.width*a.height); val bb=IntArray(aa.size)
        a.getPixels(aa,0,a.width,0,0,a.width,a.height); b.getPixels(bb,0,b.width,0,0,b.width,b.height)
        var error=0L
        for(i in aa.indices) {
            error+=kotlin.math.abs((aa[i] and 255)-(bb[i] and 255))
            error+=kotlin.math.abs((aa[i] ushr 8 and 255)-(bb[i] ushr 8 and 255))
            error+=kotlin.math.abs((aa[i] ushr 16 and 255)-(bb[i] ushr 16 and 255))
        }
        assertTrue("Önbellek çizimi özgün mürekkepten saptı: ${error.toDouble()/(aa.size*3)}",error.toDouble()/(aa.size*3)<tolerance)
    }
    @Test fun tilesPreserveInkThroughAppendEraseUndoRecolorAndPan() {
        val page=Page(noteId="cache",order=0,width=1000f,height=800f)
        var strokes=List(50) { i -> RenderedStroke(PageStroke(pageId=page.id,
            tool=if(i%6==0) Tool.HIGHLIGHTER else Tool.PEN,color=if(i%6==0) 0xffffcc00.toInt() else 0x9f18222e.toInt(),
            width=if(i%6==0) 25f else 4f,samples=List(75) { k -> Sample(50f+k*12f,50f+i*12f+sin(k*.2f)*25, .4f+(k%7)/7f,0f,0f,k*8L) })) }
        val cache=InkTileCache(8L*1024*1024)
        val a=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888); val b=a.copy(Bitmap.Config.ARGB_8888,true)
        fun check(scale:Float=1f,x:Float=0f,y:Float=0f) {
            a.eraseColor(Color.WHITE); b.eraseColor(Color.WHITE)
            val direct=Canvas(a); val cached=Canvas(b)
            direct.translate(x,y); direct.scale(scale,scale); cached.translate(x,y); cached.scale(scale,scale)
            PenRenderer.drawStrokes(direct,strokes.map { it.geo },Paint(Paint.ANTI_ALIAS_FLAG),true)
            cache.draw(cached,page,strokes,RectF(-x/scale,-y/scale,(1000-x)/scale,(800-y)/scale),scale,true)
            // Kesirli ölçek bitmap örneklemesi yapar; 1× işlemlerdeki sıkı eşiği yalnız burada ayır.
            compare(a,b,if(scale==1f) 1.5 else 2.5); assertTrue(cache.retainedBytes<=8L*1024*1024)
        }
        try {
            check(); val warm=cache.rasterizedStrokes; check(); assertEquals("Sabit mürekkep tekrar rasterleşti",warm,cache.rasterizedStrokes)
            val original=strokes; strokes=strokes+strokes[0].withSamples(strokes[0].data.samples.map { it.copy(y=it.y+400) }); check()
            strokes=strokes.filterIndexed { i,_ -> i!=5 }; check()
            strokes=original; check()
            strokes=strokes.mapIndexed { i,s -> if(i==3) RenderedStroke(s.data.copy(color=Color.RED)) else s }; check()
            check(1.5f,-200f,-90f); check(1f,-140f,-40f)
        } finally { a.recycle(); b.recycle(); cache.clear() }
    }
    @Test fun longLivePenMatchesOriginalAcrossChunkBoundaries() {
        for(smooth in listOf(false,true)) {
            val cache=ActiveInkCache(); val stroke=Geometry.Stroke(0x9f1832b0.toInt(),8f,false)
            val a=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888); val b=a.copy(Bitmap.Config.ARGB_8888,true)
            val matrix=Matrix().apply { setScale(.85f,.85f); postTranslate(40f,30f) }
            try {
                for(count in listOf(1,129,130,258,350,700)) {
                    while(stroke.points.size<count) { val i=stroke.points.size; stroke.points.add(Geometry.Point(50f+i,350f+sin(i*.025f)*140f,.3f+(i%13)/13f)) }
                    a.eraseColor(Color.WHITE); b.eraseColor(Color.WHITE)
                    val direct=Canvas(a); direct.concat(matrix)
                    PenRenderer.drawStroke(direct,stroke,0,stroke.points.size,Paint(Paint.ANTI_ALIAS_FLAG),smooth,Path(),null)
                    val cached=Canvas(b); cache.drawHead(cached,stroke,matrix,b.width,b.height,smooth)
                    cached.concat(matrix); cache.drawTail(cached,stroke,smooth)
                    compare(a,b); assertTrue("Canlı uç sınırlı kalmadı",cache.liveSegments<=129)
                }
            } finally { a.recycle(); b.recycle(); cache.release() }
        }
    }
    @Test fun visitingOneHundredPagesKeepsCacheBoundedAndRebuildsEvictedInk() {
        val cache=InkTileCache(3L*1024*1024)
        val image=Bitmap.createBitmap(600,600,Bitmap.Config.ARGB_8888)
        val canvas=Canvas(image)
        val pages=List(100) { Page(id="page-$it",noteId="long-term",order=it,width=600f,height=600f) }
        fun strokes(page:Page)=listOf(RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,color=Color.BLACK,width=10f,
            samples=listOf(Sample(100f,100f,1f,0f,0f,0),Sample(400f,100f,1f,0f,0f,10)))))
        try {
            for(page in pages) {
                image.eraseColor(Color.WHITE)
                cache.draw(canvas,page,strokes(page),RectF(0f,0f,600f,600f),1f,false)
                assertTrue("Sayfa gezildikçe önbellek bütçeyi aştı",cache.retainedBytes<=3L*1024*1024)
                assertEquals(Color.BLACK,image.getPixel(250,100))
            }
            image.eraseColor(Color.WHITE)
            cache.draw(canvas,pages.first(),strokes(pages.first()),RectF(0f,0f,600f,600f),1f,false)
            assertEquals("Atılan ilk sayfanın mürekkebi geri üretilemedi",Color.BLACK,image.getPixel(250,100))
        } finally { image.recycle(); cache.clear() }
    }
}
