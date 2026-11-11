package com.gvn.notes

import android.graphics.RectF
import androidx.ink.brush.Brush
import androidx.ink.brush.InputToolType
import androidx.ink.strokes.MutableStrokeInputBatch
import androidx.ink.strokes.Stroke
import com.gvn.notes.core.PageStroke
import com.gvn.notes.core.PenGeometry
import com.gvn.notes.core.Sample
import com.gvn.notes.core.newId
import java.util.WeakHashMap

/**
 * Kaydedilmiş gerçek kalem örneklerinden Ink çizgisi kurar (alan silgisi parçaları, akıllı şekil, taşıma ve
 * Ink'in tamamlamadığı çizgiler için). Ink'in reddettiği geriye giden zaman / aynı nokta örnekleri atlanır.
 */
fun inkFromSamples(brush:Brush,samples:List<Sample>):Stroke {
    val batch=MutableStrokeInputBatch()
    var last:Sample?=null
    for(s in samples) {
        val prev=last
        if(prev!=null && (s.elapsedMillis<prev.elapsedMillis || (s.x==prev.x && s.y==prev.y))) continue
        val orientation=if(s.orientationRadians<0) s.orientationRadians+(2*Math.PI).toFloat() else s.orientationRadians
        runCatching {
            batch.add(InputToolType.STYLUS,s.x,s.y,s.elapsedMillis,pressure=s.pressure.coerceIn(0f,1f),
                tiltRadians=s.tiltRadians.coerceIn(0f,(Math.PI/2).toFloat()),
                orientationRadians=orientation.coerceIn(0f,(2*Math.PI).toFloat()-1e-4f))
        }.onSuccess { last=s }
    }
    return Stroke(brush,batch)
}

fun Sample.toP()=PenGeometry.P(x,y,pressure,elapsedMillis)

/** Parça noktalarını örneğe çevirir; eğim/yön en yakın özgün örnekten alınır. */
fun samplesOf(points:List<PenGeometry.P>,originals:List<Sample>):List<Sample> {
    val byPoint=HashMap<PenGeometry.P,Sample>(originals.size*2); originals.forEach { byPoint[it.toP()]=it }
    var reference=originals.firstOrNull()
    var lastTime=-1L
    return points.map { p ->
        byPoint[p]?.let { reference=it }
        val ref=reference
        val time=maxOf(p.t,lastTime); lastTime=time
        Sample(p.x,p.y,p.p,ref?.tiltRadians ?: 0f,ref?.orientationRadians ?: 0f,time)
    }
}

/** Aynı araç/renk/kalınlıkta, yeni kimlikli çizgi. */
fun RenderedStroke.withSamples(samples:List<Sample>,brush:Brush=ink.brush)=
    RenderedStroke(data.copy(id=newId(),samples=samples),inkFromSamples(brush,samples))

/** Şekil kenarı: sabit basınçlı, düzenli zamanlı örnekler. */
fun shapeSamples(points:List<PenGeometry.P>)=points.mapIndexed { i,p -> Sample(p.x,p.y,.7f,0f,0f,i*8L) }

private val boundsCache=WeakHashMap<PageStroke,RectF>()
/** Çizginin sayfa koordinatındaki sınırı (önbellekli). Silgi ön elemesi için. */
fun PageStroke.bounds():RectF = boundsCache.getOrPut(this) {
    val r=RectF(Float.MAX_VALUE,Float.MAX_VALUE,-Float.MAX_VALUE,-Float.MAX_VALUE)
    samples.forEach { r.left=minOf(r.left,it.x); r.top=minOf(r.top,it.y); r.right=maxOf(r.right,it.x); r.bottom=maxOf(r.bottom,it.y) }
    r
}
