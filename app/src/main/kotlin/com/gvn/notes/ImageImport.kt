package com.gvn.notes

import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.graphics.Matrix
import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import android.media.ExifInterface
import java.io.File

/**
 * Resimleri (JPEG/PNG/WebP) not zemini olarak kullanılacak PDF'e çevirir: her resim bir sayfa.
 * EXIF yönü uygulanır (telefonla çekilmiş dikey fotoğraf yan dönmez). Çok büyük resimler
 * [MAX_SIDE] px'e küçültülerek çözülür; özgün dosyaya dokunulmaz.
 */
fun imagesToPdf(images:List<File>,out:File) {
    require(images.isNotEmpty())
    val document=PdfDocument()
    try {
        images.forEachIndexed { i,file ->
            val bitmap=decodeOriented(file)
            try {
                // Sayfa genişliği 595 pt (A4 genişliği), yükseklik resmin oranında.
                val w=PAGE_PT; val h=(PAGE_PT.toLong()*bitmap.height/bitmap.width).toInt().coerceIn(1,PAGE_PT*20)
                val page=document.startPage(PdfDocument.PageInfo.Builder(w,h,i+1).create())
                page.canvas.drawBitmap(bitmap,null,Rect(0,0,w,h),android.graphics.Paint(android.graphics.Paint.FILTER_BITMAP_FLAG))
                document.finishPage(page)
            } finally { bitmap.recycle() }
        }
        out.outputStream().use { document.writeTo(it) }
    } finally { document.close() }
}

private fun decodeOriented(file:File):Bitmap {
    val bounds=BitmapFactory.Options().apply { inJustDecodeBounds=true }
    BitmapFactory.decodeFile(file.path,bounds)
    require(bounds.outWidth>0 && bounds.outHeight>0) { "Resim okunamadı." }
    var sample=1
    while(maxOf(bounds.outWidth,bounds.outHeight)/sample>MAX_SIDE) sample*=2
    val bitmap=checkNotNull(BitmapFactory.decodeFile(file.path,BitmapFactory.Options().apply { inSampleSize=sample })) { "Resim çözülemedi." }
    val degrees=runCatching {
        when(ExifInterface(file.path).getAttributeInt(ExifInterface.TAG_ORIENTATION,ExifInterface.ORIENTATION_NORMAL)) {
            ExifInterface.ORIENTATION_ROTATE_90->90f; ExifInterface.ORIENTATION_ROTATE_180->180f
            ExifInterface.ORIENTATION_ROTATE_270->270f; else->0f
        }
    }.getOrDefault(0f)
    if(degrees==0f) return bitmap
    val rotated=Bitmap.createBitmap(bitmap,0,0,bitmap.width,bitmap.height,Matrix().apply { postRotate(degrees) },true)
    if(rotated!==bitmap) bitmap.recycle()
    return rotated
}

private const val PAGE_PT=595
private const val MAX_SIDE=3000
