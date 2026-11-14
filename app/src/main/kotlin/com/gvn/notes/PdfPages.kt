package com.gvn.notes

import android.graphics.Bitmap
import android.graphics.Color
import android.graphics.pdf.PdfRenderer
import android.os.Handler
import android.os.Looper
import android.os.ParcelFileDescriptor
import android.util.LruCache
import java.io.File
import java.util.concurrent.Executors

/**
 * Notun özgün PDF kopyasından sayfa görüntüleri üretir. PdfRenderer iş parçacığı güvenli değildir;
 * bu yüzden tüm açma/çizim işleri tek arka plan iş parçacığında yürür. Sonuçlar boyuta göre önbelleğe alınır.
 */
class PdfPages(private val file:File) {
    private val executor=Executors.newSingleThreadExecutor { Thread(it,"gvnnotes-pdf").apply { isDaemon=true } }
    private val main=Handler(Looper.getMainLooper())
    private var renderer:PdfRenderer?=null
    private var descriptor:ParcelFileDescriptor?=null
    private val requested=HashSet<String>()
    /** Üretilen sayfa görüntüsü sayısı (testler: durgun ekranda artmamalı). */
    @Volatile var renders=0; private set
    @Volatile private var closed=false
    /**
     * Ana iş parçacığında kullanılan LRU. Bu ve önceki karede istenen görüntüler bütçe yüzünden atılmaz (15 Kasım 2026):
     * dik A4 sayfa %310 üstünde 4096 px (~95 MB) olur, sınırda iki sayfa 160 MB'ı aşınca her yeni görüntü ekrandaki
     * diğerini atıyor ve sayfalar saniyede 10-19 kez yeniden üretiliyordu. Görünen sayfalar kadar geçici aşım olur.
     */
    private val cache=LinkedHashMap<String,Bitmap>(16,.75f,true)
    private var cachedBytes=0L
    private var frameKeys=HashSet<String>()
    private var lastFrameKeys=HashSet<String>()
    /** Her çizim karesinin başında çağrılır. */
    fun beginFrame() { lastFrameKeys=frameKeys; frameKeys=HashSet() }
    private fun put(key:String,bitmap:Bitmap) {
        cachedBytes-=cache.remove(key)?.let { sizeOf(it) } ?: 0; cache[key]=bitmap; cachedBytes+=sizeOf(bitmap)
        val iterator=cache.entries.iterator()
        while(cachedBytes>CACHE_BYTES && iterator.hasNext()) {
            val e=iterator.next()
            if(e.key==key || e.key in frameKeys || e.key in lastFrameKeys) continue
            cachedBytes-=sizeOf(e.value); iterator.remove()
            // Atılan görüntü bu ve önceki karede istenmedi, bir daha yalnız önbellekten okunur: GPU belleği GC beklemeden bırakılır.
            e.value.recycle()
        }
    }
    internal val retainedBytes get()=cachedBytes
    /** Donanım görüntüsünün baytı da GPU'da genişlik×yükseklik×4 kadar yer tutar. */
    private fun sizeOf(b:Bitmap)=b.width.toLong()*b.height*4

    /** İstenen genişliğe en yakın hazır görüntüyü döndürür; daha iyisi yoksa arka planda üretip [onReady] çağırır. */
    fun bitmap(index:Int,targetWidth:Int,onReady:()->Unit):Bitmap? {
        val bucket=bucket(targetWidth)
        val key="$index@$bucket"
        frameKeys+=key
        cache[key]?.let { return it }
        if(!closed && requested.add(key)) executor.execute {
            val result=runCatching { render(index,bucket) }.getOrNull()
            renders++
            main.post {
                requested.remove(key)
                if(result!=null && !closed) { put(key,result); onReady() }
            }
        }
        // Hazırlanırken aynı sayfanın başka boyuttaki görüntüsünü geçici olarak kullan.
        return BUCKETS.sortedBy { kotlin.math.abs(it-bucket) }.firstNotNullOfOrNull { b -> cache["$index@$b"]?.also { frameKeys+="$index@$b" } }
    }

    private fun render(index:Int,width:Int):Bitmap? {
        if(closed) return null
        val r=renderer ?: run {
            val fd=ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY)
            descriptor=fd; PdfRenderer(fd).also { renderer=it }
        }
        if(index !in 0 until r.pageCount) return null
        r.openPage(index).use { page ->
            val height=(width.toLong()*page.height/page.width).toInt().coerceIn(1,MAX_SIDE)
            val bitmap=Bitmap.createBitmap(width,height,Bitmap.Config.ARGB_8888)
            bitmap.eraseColor(Color.WHITE) // PDF şeffaf alanları kâğıt gibi beyaz görünsün.
            page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY)
            // GPU yüklemesi bu arka plan iş parçacığında olur (kare sırasında değil); yazılım kopyası bırakılır.
            // Kopya oluşmazsa yazılım görüntüsüyle devam edilir.
            val gpu=bitmap.copy(Bitmap.Config.HARDWARE,false) ?: return bitmap
            bitmap.recycle(); return gpu
        }
    }

    fun close() {
        closed=true
        executor.execute {
            runCatching { renderer?.close() }; runCatching { descriptor?.close() }
            renderer=null; descriptor=null
        }
        executor.shutdown()
        cache.clear(); cachedBytes=0; frameKeys.clear(); lastFrameKeys.clear()
    }

    companion object {
        private val BUCKETS=intArrayOf(512,1024,1536,2048,3072,4096)
        private const val MAX_SIDE=8192
        private const val CACHE_BYTES=160*1024*1024
        fun bucket(target:Int)=BUCKETS.firstOrNull { it>=target } ?: BUCKETS.last()

        /** PDF sayfa ölçüleri (pt). İçe aktarma sırasında not sayfalarını oluşturmak için kullanılır. */
        fun pageSizes(file:File):List<Pair<Int,Int>> =
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r -> List(r.pageCount) { i -> r.openPage(i).use { it.width to it.height } } }
            }

        /** Kütüphane önizlemesi için tek sayfayı eş zamanlı çizer (çağıran arka plan iş parçacığındadır). */
        fun renderOnce(file:File,index:Int,width:Int):Bitmap =
            ParcelFileDescriptor.open(file,ParcelFileDescriptor.MODE_READ_ONLY).use { fd ->
                PdfRenderer(fd).use { r ->
                    r.openPage(index).use { page ->
                        val bitmap=Bitmap.createBitmap(width,(width.toLong()*page.height/page.width).toInt().coerceIn(1,MAX_SIDE),Bitmap.Config.ARGB_8888)
                        bitmap.eraseColor(Color.WHITE)
                        page.render(bitmap,null,null,PdfRenderer.Page.RENDER_MODE_FOR_DISPLAY); bitmap
                    }
                }
            }
    }
}
