package com.gvn.notes.core

import com.gvn.notes.core.PenGeometry.P
import org.junit.Assert.*
import org.junit.Test
import kotlin.math.*
import kotlin.random.Random

/** GVNpen EraseTests/GeometryTests'in Kotlin karşılıkları: aynı kontroller, aynı eşikler. */
class PenGeometryTest {
    private fun page(r:Random,strokes:Int,points:Int,w:Float,h:Float,step:Float):List<Pair<List<P>,Float>> = List(strokes) {
        val width=1+r.nextFloat()*8; var x=r.nextFloat()*w; var y=r.nextFloat()*h; var angle=r.nextFloat()*6.28f
        List(points) { angle+=(r.nextFloat()-.5f)*.8f
            x=(x+cos(angle)*step*(.3f+r.nextFloat())).coerceIn(0f,w); y=(y+sin(angle)*step*(.3f+r.nextFloat())).coerceIn(0f,h)
            P(x,y,.3f+r.nextFloat()*1.2f) } to width
    }
    private fun minDistance(s:List<P>,a:P,b:P):Float {
        var best=PenGeometry.segmentDistance(s[0],a,b)
        for(i in 1 until s.size) { val n=max(1,(PenGeometry.distance(s[i-1],s[i])/.02f).toInt())
            for(k in 0..n) best=min(best,PenGeometry.segmentDistance(PenGeometry.lerp(s[i-1],s[i],k.toFloat()/n),a,b)) }
        return best
    }

    @Test fun sweptEraserCutsExactlyWithoutOverErasing() {
        val r=Random(7)
        repeat(400) { trial ->
            val page=page(r,6,40,200f,200f,4f)
            val a=P(r.nextFloat()*200,r.nextFloat()*200)
            val b=if(trial%4==0) a else P(a.x+(r.nextFloat()-.5f)*60,a.y+(r.nextFloat()-.5f)*60)
            val radius=2+r.nextFloat()*18; val whole=r.nextBoolean()
            for((s,width) in page) {
                val reach=radius+width/2; val d=minDistance(s,a,b)
                val result=PenGeometry.eraseStroke(s,width,a,b,radius,whole)
                if(d<reach-.01f) assertNotNull("silginin değdiği çizgi kesilmeli/silinmeli",result)
                else if(d>reach+.01f) assertNull("silgi dışındaki çizgi aynen kalmalı",result)
                if(result==null || whole) { if(whole && result!=null) assertTrue(result.isEmpty()); continue }
                val cuts=(1 until s.size).count { PenGeometry.segmentNear(s[it-1],s[it],a,b,reach) }
                assertTrue("yalnız kesim noktaları eklenir",result.sumOf { it.size }<=s.size+2*cuts)
                for(piece in result) for(q in piece) {
                    assertTrue("parça noktaları silginin dışında",PenGeometry.segmentDistance(q,a,b)>reach)
                    assertTrue("basınç ara değerlenir",q.p>=.3f-1e-4f && q.p<=1.5f+1e-4f)
                }
                // Silgi dışında açıkça kalan her nokta bir parça tarafından hâlâ kapsanır.
                for(i in 1 until s.size) for(k in 0..10) {
                    val q=PenGeometry.lerp(s[i-1],s[i],k/10f)
                    if(PenGeometry.segmentDistance(q,a,b)<=reach+.05f) continue
                    val covered=result.any { o -> if(o.size==1) PenGeometry.distance(o[0],q)<.02f else (1 until o.size).any { j -> PenGeometry.segmentDistance(q,o[j-1],o[j])<.02f } }
                    assertTrue("silgi dışında fazla silme yok",covered)
                }
            }
        }
    }
    @Test fun sweepAcrossLongEdgeCutsOnceAtExactPositions() {
        val cut=PenGeometry.eraseStroke(listOf(P(0f,0f),P(500f,0f)),2f,P(250f,-30f),P(250f,30f),10f,false)!!
        assertEquals(2,cut.size); assertEquals(2,cut[0].size); assertEquals(2,cut[1].size)
        assertEquals(239f,cut[0][1].x,.01f); assertEquals(261f,cut[1][0].x,.01f)
    }
    private fun circle(cx:Float,cy:Float,r:Float)=List(97) { i -> val a=i*PI/48; P(cx+r*cos(a).toFloat(),cy+r*sin(a).toFloat()) }
    @Test fun recognizesLineCircleAndRejectsScribble() {
        val c=circle(50f,50f,50f)
        assertEquals("circle",PenGeometry.recognize(c))
        assertEquals("circle",PenGeometry.recognize(c.subList(0,c.size-5)))
        assertEquals("line",PenGeometry.recognize(List(20) { P(it*5f,it*2f) }))
        assertNull(PenGeometry.recognize(listOf(P(0f,0f),P(1f,1f))))
        assertNotNull(PenGeometry.fitShape(c))
    }
    @Test fun fitsBowedAndRoundedSquaresButKeepsOvals() {
        for(rotate in intArrayOf(0,25,60)) {
            val angle=Math.toRadians(rotate.toDouble()); val pts=ArrayList<P>()
            for(side in 0 until 4) for(i in 0 until 32) {
                val u=i/32.0; val bow=8*sin(PI*u); var x=0.0; var y=0.0
                when(side) { 0->{x=100*u;y=bow}; 1->{x=100-bow;y=100*u}; 2->{x=100-100*u;y=100-bow}; else->{x=bow;y=100-100*u} }
                pts.add(P((x*cos(angle)-y*sin(angle)).toFloat(),(x*sin(angle)+y*cos(angle)).toFloat()))
            }
            pts.add(pts[0])
            assertEquals("bükük kare $rotate",4,PenGeometry.fitShape(pts)?.size)
        }
        for(ratio in floatArrayOf(.5f,1f,2f,3f)) {
            val oval=List(97) { i -> val a=i*PI/48; P(50*ratio+50*ratio*cos(a).toFloat(),50+50*sin(a).toFloat()) }
            assertEquals("oval dikdörtgen olmamalı $ratio",1,PenGeometry.fitShape(oval)?.size)
        }
        for(rotate in intArrayOf(0,25,60)) {
            val rot=Math.toRadians(rotate.toDouble())
            val rounded=List(129) { i -> val a=i*2*PI/128; val co=cos(a); val si=sin(a)
                val x=50*sign(co)*sqrt(abs(co)); val y=50*sign(si)*sqrt(abs(si))
                P((x*cos(rot)-y*sin(rot)).toFloat(),(x*sin(rot)+y*cos(rot)).toFloat()) }
            assertEquals("yuvarlak kare $rotate",4,PenGeometry.fitShape(rounded)?.size)
            assertEquals("açık yuvarlak kare $rotate",4,PenGeometry.fitShape(rounded.subList(0,rounded.size-6))?.size)
        }
        for(vertices in intArrayOf(3,4)) {
            val corners=if(vertices==3) listOf(P(30f,5f),P(120f,100f),P(0f,80f),P(30f,5f)) else listOf(P(0f,30f),P(100f,0f),P(130f,100f),P(30f,130f),P(0f,30f))
            val raw=ArrayList<P>(); for(i in 1 until corners.size) for(j in 0 until 20) raw.add(PenGeometry.lerp(corners[i-1],corners[i],j/20f)); raw.add(corners[0])
            assertEquals("döndürülmüş çokgen $vertices",vertices,PenGeometry.fitShape(raw)?.size)
        }
    }
    @Test fun holdDetection() {
        val moving=List(20) { P(it*5f,0f,1f,it*10L) }
        assertFalse(PenGeometry.heldAtEnd(moving,3f,600))
        val held=moving+List(10) { P(95f+(it%2)*.5f,0f,1f,200L+it*80L) }
        assertTrue(PenGeometry.heldAtEnd(held,3f,600))
    }
}
