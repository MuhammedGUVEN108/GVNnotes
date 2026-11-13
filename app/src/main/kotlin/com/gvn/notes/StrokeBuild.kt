package com.gvn.notes

import android.graphics.RectF
import com.gvn.notes.core.PageStroke
import com.gvn.notes.core.PenGeometry
import com.gvn.notes.core.Sample
import com.gvn.notes.core.Tool
import com.gvn.notes.core.newId
import com.gvn.notes.pen.Geometry

/** Kayıtlı çizginin GVNpen çizim/silgi görünümü: sayfa koordinatında noktalar, basınç katsayısı (0,15..1,5). */
fun PageStroke.toGeo():Geometry.Stroke = Geometry.Stroke(color,width,tool==Tool.HIGHLIGHTER).also { g ->
    samples.forEach { g.points.add(Geometry.Point(it.x,it.y,it.pressure)) }
}

/** GVNpen çizgisini (silgi parçası, şekil) aynı araç/renk/kalınlıkta kayda çevirir. */
fun Geometry.Stroke.toSamples(startMillis:Long=0L):List<Sample> =
    points.mapIndexed { i,p -> Sample(p.x,p.y,p.p,0f,0f,startMillis+i*8L) }

fun Sample.toP()=PenGeometry.P(x,y,pressure,elapsedMillis)

/** Aynı araç/renk/kalınlıkta, yeni kimlikli çizgi. */
fun RenderedStroke.withSamples(samples:List<Sample>)=RenderedStroke(data.copy(id=newId(),samples=samples))

/** Şekil kenarı: sabit basınçlı, düzenli zamanlı örnekler. */
fun shapeSamples(points:List<PenGeometry.P>)=points.mapIndexed { i,p -> Sample(p.x,p.y,1f,0f,0f,i*8L) }

private val boundsCache=WeakIdentityCache<PageStroke,RectF>()
/** Çizginin sayfa koordinatındaki sınırı (önbellekli). Silgi ve kement ön elemesi için. */
fun PageStroke.bounds():RectF = (samples as? SampleHandle)?.bounds ?: boundsCache[this] ?: run {
    val r=RectF(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE)
    samples.forEach { r.left=minOf(r.left,it.x); r.top=minOf(r.top,it.y); r.right=maxOf(r.right,it.x); r.bottom=maxOf(r.bottom,it.y) }
    boundsCache[this]=r; r
}
