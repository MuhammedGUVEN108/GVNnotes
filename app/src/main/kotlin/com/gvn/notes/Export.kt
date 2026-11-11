package com.gvn.notes

import android.graphics.Rect
import android.graphics.pdf.PdfDocument
import com.gvn.notes.core.Page
import java.io.File
import java.io.OutputStream

/**
 * Notu PDF olarak dışa aktarır: her sayfa PDF zemini + yazılarla birlikte görüntü olarak yazılır (raster PDF).
 * Sayfa ölçüsü: 1000 not birimi = 595 pt (A4 genişliği). Görüntü genişliği [EXPORT_WIDTH] px (~180 dpi).
 * Özgün PDF ve not kaydı değişmez.
 */
fun exportPdf(directory:File,pages:List<Page>,strokes:List<RenderedStroke>,out:OutputStream,onPage:(Int)->Unit={}) {
    val document=PdfDocument()
    try {
        pages.forEachIndexed { i,page ->
            val w=(page.width*PT_PER_UNIT).toInt().coerceAtLeast(1)
            val h=(page.height*PT_PER_UNIT).toInt().coerceAtLeast(1)
            val pdfPage=document.startPage(PdfDocument.PageInfo.Builder(w,h,i+1).create())
            val bitmap=renderPage(directory,page,strokes,EXPORT_WIDTH)
            try { pdfPage.canvas.drawBitmap(bitmap,null,Rect(0,0,w,h),null) } finally { bitmap.recycle() }
            document.finishPage(pdfPage)
            onPage(i+1)
        }
        document.writeTo(out)
    } finally { document.close() }
}

private const val PT_PER_UNIT=0.595f
private const val EXPORT_WIDTH=1500
