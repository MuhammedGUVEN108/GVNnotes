package com.gvn.notes

import android.graphics.Canvas
import android.graphics.Paint
import com.gvn.notes.core.BackgroundType
import com.gvn.notes.core.Page

/** Boş sayfa şablonları. Sayı değerleri kayıt biçimine yazılır; değiştirme, yalnız ekle. */
enum class PagePattern(val code:Int,val label:String) {
    NONE(0,"Düz"), LINED(1,"Çizgili"), GRID(2,"Kareli"), DOTS(3,"Noktalı");
    companion object { fun of(code:Int)=entries.firstOrNull { it.code==code } ?: NONE }
}

/** Satır/kare aralığı: 38 birim ≈ 8 mm (1000 birim = A4 genişliği). */
private const val STEP=38f
private val linePaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xffcfd8e3.toInt(); strokeWidth=0f; style=Paint.Style.STROKE }
private val marginPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xfff0b8bd.toInt(); strokeWidth=0f; style=Paint.Style.STROKE }
private val dotPaint=Paint(Paint.ANTI_ALIAS_FLAG).apply { color=0xffaebccb.toInt(); style=Paint.Style.FILL }

/**
 * Şablonu sayfa koordinatındaki tuvale çizer (tuval sayfa matrisiyle dönüştürülmüş olmalı). Çizgiler ekranda
 * her yakınlaştırmada 1 piksel kalır. PDF/resim sayfalarına şablon çizilmez.
 */
fun drawPattern(canvas:Canvas,page:Page) {
    if(page.background!=BackgroundType.BLANK) return
    when(PagePattern.of(page.pattern)) {
        PagePattern.NONE -> {}
        PagePattern.LINED -> {
            var y=STEP*3; while(y<page.height-STEP/2) { canvas.drawLine(0f,y,page.width,y,linePaint); y+=STEP }
            canvas.drawLine(STEP*2.5f,0f,STEP*2.5f,page.height,marginPaint)
        }
        PagePattern.GRID -> {
            var y=STEP; while(y<page.height) { canvas.drawLine(0f,y,page.width,y,linePaint); y+=STEP }
            var x=STEP; while(x<page.width) { canvas.drawLine(x,0f,x,page.height,linePaint); x+=STEP }
        }
        PagePattern.DOTS -> {
            var y=STEP; while(y<page.height) { var x=STEP; while(x<page.width) { canvas.drawCircle(x,y,1.6f,dotPaint); x+=STEP }; y+=STEP }
        }
    }
}
