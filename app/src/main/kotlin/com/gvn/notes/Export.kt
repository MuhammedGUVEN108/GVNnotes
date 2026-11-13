package com.gvn.notes

import android.graphics.Paint
import android.graphics.pdf.PdfDocument
import com.gvn.notes.core.BackgroundType
import com.gvn.notes.core.Page
import com.gvn.notes.pen.PenRenderer
import com.tom_roush.pdfbox.io.MemoryUsageSetting
import com.tom_roush.pdfbox.multipdf.LayerUtility
import com.tom_roush.pdfbox.pdmodel.PDDocument
import com.tom_roush.pdfbox.pdmodel.PDPage
import com.tom_roush.pdfbox.pdmodel.PDPageContentStream
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.util.Matrix
import java.io.ByteArrayOutputStream
import java.io.File
import java.io.OutputStream

/** Özgün PDF sayfalarını korur; yazıları ve boş sayfa şablonlarını vektör olarak ekler. */
fun exportPdf(directory:File,pages:List<Page>,strokes:List<RenderedStroke>,out:OutputStream,onPage:(Int)->Unit={}) {
    val byPage=strokes.groupBy { it.data.pageId }
    val source=if(pages.any { it.background==BackgroundType.PDF })
        PDDocument.load(PageStore.sourceFile(directory),MemoryUsageSetting.setupTempFileOnly()) else null
    try {
        PDDocument(MemoryUsageSetting.setupTempFileOnly()).use { document ->
            val layers=LayerUtility(document)
            pages.forEachIndexed { i,page ->
                val original=if(page.background==BackgroundType.PDF) {
                    val index=requireNotNull(page.sourcePageIndex) { "Kaynak sayfa bulunamadı." }
                    requireNotNull(source).getPage(index)
                } else null
                val target=if(original!=null) document.importPage(original).also { it.resources=original.resources }
                    else PDPage(PDRectangle(page.width*PT_PER_UNIT,page.height*PT_PER_UNIT)).also { document.addPage(it) }
                val own=byPage[page.id].orEmpty()
                if(original==null || own.isNotEmpty()) {
                    // Aynı kalem geometrisi, bitmap oluşturmadan PDF yollarına yazılır.
                    val bytes=ByteArrayOutputStream()
                    val w=(page.width*PT_PER_UNIT).toInt().coerceAtLeast(1)
                    val h=(page.height*PT_PER_UNIT).toInt().coerceAtLeast(1)
                    val inkPdf=PdfDocument()
                    try {
                        val sheet=inkPdf.startPage(PdfDocument.PageInfo.Builder(w,h,1).create())
                        sheet.canvas.scale(w/page.width,h/page.height)
                        sheet.canvas.clipRect(0f,0f,page.width,page.height)
                        drawPattern(sheet.canvas,page)
                        PenRenderer.drawStrokes(sheet.canvas,own.map { it.geo },Paint(Paint.ANTI_ALIAS_FLAG),false)
                        inkPdf.finishPage(sheet); inkPdf.writeTo(bytes)
                    } finally { inkPdf.close() }
                    PDDocument.load(bytes.toByteArray()).use { ink ->
                        val form=layers.importPageAsForm(ink,0)
                        PDPageContentStream(document,target,PDPageContentStream.AppendMode.APPEND,true,true).use { stream ->
                            stream.transform(overlayMatrix(target,w.toFloat(),h.toFloat()))
                            stream.drawForm(form)
                        }
                    }
                }
                onPage(i+1)
            }
            document.save(out)
        }
    } finally { source?.close() }
}

/** Görünen sayfa koordinatını kırpılmış ve dönmüş PDF koordinatına eşler. */
internal fun overlayMatrix(page:PDPage,width:Float,height:Float):Matrix {
    val b=page.cropBox; val x=b.lowerLeftX; val y=b.lowerLeftY
    return when((page.rotation%360+360)%360) {
        90 -> Matrix(0f,b.height/width,-b.width/height,0f,x+b.width,y)
        180 -> Matrix(-b.width/width,0f,0f,-b.height/height,x+b.width,y+b.height)
        270 -> Matrix(0f,-b.height/width,b.width/height,0f,x,y+b.height)
        else -> Matrix(b.width/width,0f,0f,b.height/height,x,y)
    }
}

private const val PT_PER_UNIT=0.595f
