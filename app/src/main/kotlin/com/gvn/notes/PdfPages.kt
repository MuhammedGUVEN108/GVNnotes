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
    @Volatile private var closed=false
    private val cache=object:LruCache<String,Bitmap>(CACHE_BYTES) {
        override fun sizeOf(key:String,value:Bitmap)=value.byteCount
    }

    /** İstenen genişliğe en yakın hazır görüntüyü döndürür; daha iyisi yoksa arka planda üretip [onReady] çağırır. */
    fun bitmap(index:Int,targetWidth:Int,onReady:()->Unit):Bitmap? {
        val bucket=bucket(targetWidth)
        val key="$index@$bucket"
        cache.get(key)?.let { return it }
        if(requested.add(key)) executor.execute {
            val result=runCatching { render(index,bucket) }.getOrNull()
            main.post {
                requested.remove(key)
                if(result!=null && !closed) { cache.put(key,result); onReady() }
            }
        }
        // Hazırlanırken aynı sayfanın başka boyuttaki görüntüsünü geçici olarak kullan.
        return BUCKETS.sortedBy { kotlin.math.abs(it-bucket) }.firstNotNullOfOrNull { cache.get("$index@$it") }
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
            return bitmap
        }
    }

    fun close() {
        closed=true
        executor.execute {
            runCatching { renderer?.close() }; runCatching { descriptor?.close() }
            renderer=null; descriptor=null
        }
        executor.shutdown()
        cache.evictAll()
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
