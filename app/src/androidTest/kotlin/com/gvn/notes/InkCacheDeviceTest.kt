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
import kotlin.math.max
import kotlin.math.min

@RunWith(AndroidJUnit4::class)
class InkCacheDeviceTest {
    @Test fun denseAsyncEditsKeepUnchangedInkInEveryFrame() {
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val page=Page(noteId="erase-flicker",order=0,width=1000f,height=800f)
        val original=List(120) { n -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,
            color=0xc0182230.toInt(),width=4f,samples=List(96) { k ->
                Sample(30f+k*9f,40f+n*5f,.8f,0f,0f,k.toLong()) })) }
        var strokes=original
        val cache=InkTileCache(async=true); val reference=InkTileCache()
        val image=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888)
        val expected=image.copy(Bitmap.Config.ARGB_8888,true)
        var checkInk=false
        fun frame():Boolean {
            image.eraseColor(Color.WHITE)
            cache.beginFrame(1f)
            cache.draw(Canvas(image),page,strokes,RectF(0f,0f,1000f,800f),1f,true)
            if(checkInk) {
                // Silinmeyen çizgi her iki yatay karoda da görünmeli; saydam zemin de sınanır.
                for(x in listOf(200,700)) assertNotEquals("Silgi sırasında boş kare",Color.WHITE,image.getPixel(x,50))
            }
            return cache.settled
        }
        fun settle() {
            val deadline=android.os.SystemClock.uptimeMillis()+20_000
            while(android.os.SystemClock.uptimeMillis()<deadline) {
                var done=false; inst.runOnMainSync { done=frame() }
                if(done) return
                android.os.SystemClock.sleep(16)
            }
            fail("Silgi karoları tamamlanmadı")
        }
        fun exact() {
            inst.runOnMainSync {
                expected.eraseColor(Color.WHITE)
                reference.draw(Canvas(expected),page,strokes,RectF(0f,0f,1000f,800f),1f,true)
                assertTrue("Son görüntü senkron referanstan farklı",image.sameAs(expected))
            }
        }
        try {
            settle(); checkInk=true
            // Tüm çizgi, alan parçalama, geri al/yinele, kement taşı/sil ve kirli karoya ekleme.
            val erased=original.filterIndexed { i,_ -> i !in 20..35 }
            val split=erased.flatMapIndexed { i,s -> if(i==45) listOf(
                s.withSamples(s.data.samples.take(30)),s.withSamples(s.data.samples.drop(60))) else listOf(s) }
            val moved=original.mapIndexed { i,s -> if(i in 60..70)
                s.withSamples(s.data.samples.map { it.copy(y=it.y+80) }) else s }
            for(target in listOf(erased,split,original,split,moved,erased)) {
                inst.runOnMainSync {
                    strokes=target
                    val work=cache.rasterizedStrokes
                    // İşçinin callback'i çalışmadan art arda silgi örnekleri: her kare dolu kalmalı.
                    repeat(4) { step ->
                        strokes=strokes.filterIndexed { i,_ -> i!=80-step }
                        frame()
                    }
                    strokes=target; frame()
                    assertEquals("Yoğun silgi ana thread'de rasterleşti",work,cache.rasterizedStrokes)
                }
                settle(); exact()
            }
            inst.runOnMainSync {
                strokes=original; frame()
                strokes=erased; frame()
                strokes=erased+original[60].withSamples(original[60].data.samples.map { it.copy(y=it.y+110) })
                frame()
            }
            settle(); exact()
        } finally { inst.runOnMainSync { cache.close(); reference.close(); image.recycle(); expected.recycle() } }
    }
    @Test fun continuousAsyncEraseMakesProgressBeforePenStops() {
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val page=Page(noteId="continuous-erase",order=0,width=500f,height=500f)
        // Tek karoda yoğun geometri: 4096 eşiğinin üstünde, son adımda da async kalır.
        val original=List(120) { n -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,
            color=0xc0182230.toInt(),width=1.5f,samples=List(96) { k ->
                Sample(20f+k*4.8f,20f+n*3.6f,.8f,0f,0f,k.toLong()) })) }
        var strokes=original
        val cache=InkTileCache(async=true); val reference=InkTileCache()
        val image=Bitmap.createBitmap(500,500,Bitmap.Config.ARGB_8888)
        val expected=image.copy(Bitmap.Config.ARGB_8888,true)
        val erasedAt=LongArray(48)
        var removed=0; var checkInk=false; var maxLag=0L; var progress=0
        fun frame():Boolean {
            image.eraseColor(Color.WHITE); cache.beginFrame(1f)
            val work=cache.rasterizedStrokes
            cache.draw(Canvas(image),page,strokes,RectF(0f,0f,500f,500f),1f,true)
            assertEquals("Sürekli silgi ana thread'de rasterleşti",work,cache.rasterizedStrokes)
            if(checkInk) {
                assertNotEquals("Sürekli silgide kalan yazı boşaldı",Color.WHITE,image.getPixel(200,380))
                val now=android.os.SystemClock.uptimeMillis()
                for(i in 0 until removed) {
                    if(erasedAt[i]<0) continue
                    val blank=image.getPixel(200,(20f+i*3.6f).toInt())==Color.WHITE
                    val lag=now-erasedAt[i]
                    assertTrue("Silgi sürerken bölge güncellenmedi: adım=$i gecikme=$lag ms",blank || lag<=150)
                    if(blank && erasedAt[i]>0) { maxLag=maxOf(maxLag,lag); progress++; erasedAt[i]=-1 }
                }
            }
            return cache.settled
        }
        fun settle() {
            val deadline=android.os.SystemClock.uptimeMillis()+20_000
            while(android.os.SystemClock.uptimeMillis()<deadline) {
                var done=false; inst.runOnMainSync { done=frame() }
                if(done) return
                android.os.SystemClock.sleep(8)
            }
            fail("Sürekli silgi karoları tamamlanmadı")
        }
        try {
            settle(); checkInk=true
            for(i in erasedAt.indices) assertNotEquals("Silinecek bölge başlangıçta boş",
                Color.WHITE,image.getPixel(200,(20f+i*3.6f).toInt()))
            repeat(48) { step ->
                inst.runOnMainSync {
                    removed=step+1; erasedAt[step]=android.os.SystemClock.uptimeMillis()
                    strokes=original.drop(removed)
                    frame()
                }
                // İşçi/settled beklenmez; ana looper callback'leri bu arada işleyebilir.
                android.os.SystemClock.sleep(8)
            }
            assertTrue("Silme bitmeden yeterli bölge güncellenmedi",progress>=30)
            android.util.Log.i("InkCacheRegression","Sürekli silme: 48 adım, ilerleyen bölge=$progress, en yüksek gecikme=$maxLag ms")
            settle()
            inst.runOnMainSync {
                expected.eraseColor(Color.WHITE)
                reference.draw(Canvas(expected),page,strokes,RectF(0f,0f,500f,500f),1f,true)
                assertTrue("Sürekli silgi sonucu referanstan farklı",image.sameAs(expected))
                assertEquals(reference.retainedBytes,cache.retainedBytes)
                cache.clear(); assertEquals(0L,cache.retainedBytes)
            }
        } finally { inst.runOnMainSync { cache.close(); reference.close(); image.recycle(); expected.recycle() } }
    }
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
    @Test fun firstStrokeOnNewPageIsVisibleInTheHandoffFrame() {
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        inst.runOnMainSync {
            for(tool in listOf(Tool.PEN,Tool.HIGHLIGHTER)) {
                val page=Page(noteId="first-ink",order=0,width=1000f,height=800f)
                val stroke=RenderedStroke(PageStroke(pageId=page.id,tool=tool,color=0x9f1832b0.toInt(),width=12f,
                    samples=List(90) { k -> Sample(100f+k*6f,200f,.8f,0f,0f,k.toLong()) }))
                val cache=InkTileCache(async=true); val direct=InkTileCache()
                val a=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888); val b=a.copy(Bitmap.Config.ARGB_8888,true)
                try {
                    // Boş sayfa → kalem kalkışı. Ana thread'den çıkmadan ilk bitmiş kareyi doğrula.
                    cache.beginFrame(1f); cache.draw(Canvas(a),page,emptyList(),RectF(0f,0f,1000f,800f),1f,true)
                    a.eraseColor(Color.WHITE); b.eraseColor(Color.WHITE)
                    cache.beginFrame(1f); cache.draw(Canvas(a),page,listOf(stroke),RectF(0f,0f,1000f,800f),1f,true)
                    direct.draw(Canvas(b),page,listOf(stroke),RectF(0f,0f,1000f,800f),1f,true)
                    compare(a,b,.01)
                    assertNotEquals(Color.WHITE,a.getPixel(300,200))
                    assertTrue(cache.settled)
                } finally { cache.close(); direct.close(); a.recycle(); b.recycle() }
            }
        }
    }
    @Test fun asynchronousZoomKeepsFallbackInkAndEventuallyMatchesExactTiles() {
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val page=Page(noteId="async",order=0,width=1000f,height=800f)
        val strokes=List(120) { n -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,color=Color.BLACK,width=4f,
            samples=List(96) { k -> Sample(30f+k*9f,40f+n*5f,.8f,0f,0f,k.toLong()) })) }
        val cache=InkTileCache(async=true); val sync=InkTileCache()
        val a=Bitmap.createBitmap(1000,800,Bitmap.Config.ARGB_8888); val b=a.copy(Bitmap.Config.ARGB_8888,true)
        fun frame(scale:Float, offset:Float=0f):Boolean {
            a.eraseColor(Color.WHITE); val canvas=Canvas(a); canvas.translate(offset,offset); canvas.scale(scale,scale)
            cache.beginFrame(scale); cache.draw(canvas,page,strokes,RectF(0f,0f,1000f/scale,800f/scale),scale,true)
            return cache.settled
        }
        fun settle(scale:Float) {
            val until=android.os.SystemClock.uptimeMillis()+20_000
            while(android.os.SystemClock.uptimeMillis()<until) {
                var done=false; inst.runOnMainSync { done=frame(scale) }
                if(done) return
                android.os.SystemClock.sleep(16)
            }
            fail("Arka plan karoları tamamlanmadı")
        }
        try {
            settle(1f)
            inst.runOnMainSync {
                frame(1.5f)
                val pixels=IntArray(a.width*a.height); a.getPixels(pixels,0,a.width,0,0,a.width,a.height)
                assertTrue("Yakınlaştırma sırasında mürekkep kayboldu",pixels.count { it!=Color.WHITE }>10_000)
            }
            // Sabit ölçekli konum oturmasında her karede hazır mürekkep görünür kalır.
            repeat(16) { n ->
                inst.runOnMainSync {
                    frame(1.5f,n*2f)
                    val pixels=IntArray(a.width*a.height); a.getPixels(pixels,0,a.width,0,0,a.width,a.height)
                    assertTrue("Oturma karesinde mürekkep kayboldu: $n",pixels.count { it!=Color.WHITE }>10_000)
                }
                android.os.SystemClock.sleep(16)
            }
            settle(1.5f)
            inst.runOnMainSync {
                b.eraseColor(Color.WHITE); val canvas=Canvas(b); canvas.scale(1.5f,1.5f)
                sync.draw(canvas,page,strokes,RectF(0f,0f,1000f/1.5f,800f/1.5f),1.5f,true)
                compare(a,b,.01)
            }
        } finally { inst.runOnMainSync { cache.close(); sync.close(); a.recycle(); b.recycle() } }
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
    /**
     * 14 Kasım 2026 kullanıcı hatası: yatay tablette ~%200 üstü ölçekte mürekkep sürekli yanıp söndü, ölçek değişince geçti.
     * Gerçek ekran boyunda (2800×1568) iki yoğun sayfanın sınırı görünürken görünen karo sayısı 64 MiB bütçesini aşar;
     * her yeni karo görünen başka karoyu atıp sonsuz yeniden çizim döngüsü oluşturuyordu. Önceki testler 1000×800 alan kullandığı için yakalamadı.
     */
    @Test fun realScreenZoomBandSettlesWithoutEvictingVisibleTiles() {
        val inst=androidx.test.platform.app.InstrumentationRegistry.getInstrumentation()
        val w=2800; val h=1568; val gap=32f
        val pages=List(2) { Page(id="band-$it",noteId="band",order=it,width=1000f,height=1414f) }
        // Sayfayı baştan sona kaplayan yoğun çizgiler: sayfa başına 70 × 120 = 8400 nokta (>4096, arka plan yolu).
        val ink=pages.associate { page -> page.id to List(70) { n -> RenderedStroke(PageStroke(pageId=page.id,tool=Tool.PEN,
            color=Color.BLACK,width=3f,samples=List(120) { k -> Sample(10f+k*8.3f,10f+n*20f,.8f,0f,0f,k.toLong()) })) } }
        val image=Bitmap.createBitmap(w,h,Bitmap.Config.ARGB_8888)
        for(scale in listOf(1.6f,2.05f,2.3f,3.1f,4.1f,4.6f,6.2f,8f)) {
            val cache=InkTileCache(async=true)
            // Yatayda ortalı, iki sayfanın sınırı ekranın ortasında.
            val vx=(w-1000f*scale)/2; val vy=h/2f-(1414f+gap/2)*scale
            fun frame():Boolean {
                image.eraseColor(Color.WHITE)
                cache.beginFrame(scale)
                pages.forEachIndexed { i,page ->
                    val top=i*(1414f+gap)
                    val left=max(0f,-vx/scale); val right=min(page.width,(w-vx)/scale)
                    val upper=max(0f,(-vy/scale)-top); val lower=min(page.height,(h-vy)/scale-top)
                    if(right<=left || lower<=upper) return@forEachIndexed
                    val canvas=Canvas(image); canvas.translate(vx,vy+top*scale); canvas.scale(scale,scale)
                    canvas.clipRect(left,upper,right,lower)
                    cache.draw(canvas,page,ink.getValue(page.id),RectF(left,upper,right,lower),scale,false)
                }
                return cache.settled
            }
            try {
                val deadline=android.os.SystemClock.uptimeMillis()+20_000
                var settled=false
                while(!settled && android.os.SystemClock.uptimeMillis()<deadline) {
                    inst.runOnMainSync { settled=frame() }
                    if(!settled) android.os.SystemClock.sleep(16)
                }
                assertTrue("%${(scale*100).toInt()} ölçekte karolar hiç oturmadı (görünen karolar birbirini atıyor)",settled)
                var before=0L; inst.runOnMainSync { before=cache.rasterizedStrokes }
                // Oturduktan sonra ekran durağan: yeni karo üretilmez, her kare hazır.
                repeat(40) {
                    inst.runOnMainSync { assertTrue("%${(scale*100).toInt()} ölçekte oturmuş görüntü yeniden boşaldı",frame()) }
                    android.os.SystemClock.sleep(16)
                }
                inst.runOnMainSync { assertEquals("Durağan ekranda karolar yeniden çizildi",before,cache.rasterizedStrokes) }
                inst.runOnMainSync {
                    android.util.Log.i("GVNnotesTest","zoom-band %${(scale*100).toInt()} karo bellegi ${cache.retainedBytes/1048576} MiB")
                    // Görünen alan kadar geçici aşım kabul; yatay tam ekranda bile sınırlı kalmalı.
                    assertTrue("Karo belleği sınırsız büyüdü: ${cache.retainedBytes}",cache.retainedBytes<=160L*1024*1024)
                }
            } finally { inst.runOnMainSync { cache.close() } }
        }
        image.recycle()
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
