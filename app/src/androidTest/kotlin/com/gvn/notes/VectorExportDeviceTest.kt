package com.gvn.notes

import android.graphics.Color
import androidx.test.ext.junit.runners.AndroidJUnit4
import androidx.test.platform.app.InstrumentationRegistry
import com.gvn.notes.core.*
import com.tom_roush.pdfbox.android.PDFBoxResourceLoader
import com.tom_roush.pdfbox.pdmodel.*
import com.tom_roush.pdfbox.pdmodel.common.PDRectangle
import com.tom_roush.pdfbox.pdmodel.font.PDType1Font
import com.tom_roush.pdfbox.text.PDFTextStripper
import org.junit.Assert.*
import org.junit.Test
import org.junit.runner.RunWith
import java.io.File
import java.security.MessageDigest

/** Gerçek Android PDF çizicisiyle metin, vektör, döndürme, kırpma ve not sırasını birlikte sınar. */
@RunWith(AndroidJUnit4::class)
class VectorExportDeviceTest {
    @Test fun preservesSourceAndPlacesInkOnRotatedCroppedPages() {
        val context=InstrumentationRegistry.getInstrumentation().targetContext
        PDFBoxResourceLoader.init(context)
        val directory=File(context.cacheDir,"vector-export-test").apply { mkdirs() }
        try {
            val source=PageStore.sourceFile(directory)
            PDDocument().use { pdf ->
                listOf(0,90,180,270).forEach { rotation ->
                    val p=PDPage(PDRectangle(400f,500f)).apply {
                        cropBox=PDRectangle(20f,30f,300f,400f); this.rotation=rotation
                    }
                    pdf.addPage(p)
                    PDPageContentStream(pdf,p).use { stream ->
                        stream.addRect(50f,200f,60f,50f); stream.fill()
                        stream.beginText(); stream.setFont(PDType1Font.HELVETICA,18f)
                        stream.newLineAtOffset(45f,350f); stream.showText("SOURCE-$rotation"); stream.endText()
                    }
                }
                pdf.save(source)
            }
            val before=MessageDigest.getInstance("SHA-256").digest(source.readBytes())
            val sizes=PdfPages.pageSizes(source)
            val originals=sizes.mapIndexed { i,(w,h) -> Page(noteId="test",order=i,width=1000f,
                height=1000f*h/w,background=BackgroundType.PDF,sourcePageIndex=i) }
            val blank=Page(noteId="test",order=0,pattern=2)
            val pages=listOf(originals[3],blank,originals[1],originals[0],originals[2])
            val strokes=pages.map { p -> RenderedStroke(PageStroke(pageId=p.id,tool=Tool.PEN,
                color=Color.RED,width=16f,samples=listOf(Sample(400f,300f,1f,0f,0f,0),Sample(600f,300f,1f,0f,0f,10)))) }
            val result=File(directory,"result.pdf")
            result.outputStream().use { exportPdf(directory,pages,strokes,it) }
            val evidence=File(context.getExternalFilesDir(null),"export-test").apply { mkdirs() }
            source.copyTo(File(evidence,"source.pdf"),overwrite=true)
            result.copyTo(File(evidence,"result.pdf"),overwrite=true)
            assertArrayEquals(before,MessageDigest.getInstance("SHA-256").digest(source.readBytes()))
            PDDocument.load(result).use { pdf ->
                assertEquals(5,pdf.numberOfPages)
                val text=PDFTextStripper().getText(pdf).replace(Regex("\\s+"),"")
                listOf(0,90,180,270).forEach { assertTrue("Kaynak metin kayboldu: $it; metin=$text",text.contains("SOURCE-$it")) }
                assertTrue(text.indexOf("SOURCE-270")<text.indexOf("SOURCE-90"))
                // Test kaynağı resimsiz: tam sayfa bitmap eklenmemiş olmalı.
                pdf.pages.forEach { p -> p.resources.xObjectNames.forEach { name ->
                    assertFalse(p.resources.isImageXObject(name))
                } }
            }
            pages.forEachIndexed { i,p ->
                val image=PdfPages.renderOnce(result,i,1000)
                try {
                    val y=(300f/p.height*image.height).toInt()
                    val pixel=image.getPixel(500,y)
                    assertTrue("Sayfa $i üzerinde kalem yanlış yerde",Color.red(pixel)>180 && Color.green(pixel)<100)
                    p.sourcePageIndex?.let { index ->
                        val baseline=PdfPages.renderOnce(source,index,1000)
                        try {
                            // Metin çıkarılması tek başına yetmez: alttaki özgün çizim görünür de kalmalı.
                            for(yy in 5 until baseline.height step 10) for(xx in 5 until baseline.width step 10) {
                                if(kotlin.math.abs(yy-y)>20) {
                                    val a=baseline.getPixel(xx,yy)
                                    if(Color.red(a)<50 && Color.green(a)<50 && Color.blue(a)<50) {
                                        val b=image.getPixel(xx,yy)
                                        assertTrue("Kaynak çizim örtüldü: sayfa $i",Color.red(b)<80 && Color.green(b)<80 && Color.blue(b)<80)
                                    }
                                }
                            }
                        } finally { baseline.recycle() }
                    }
                } finally { image.recycle() }
            }
        } finally { directory.deleteRecursively() }
    }
}
