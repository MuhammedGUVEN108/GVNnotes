package com.gvn.notes.core

import kotlin.math.*

/**
 * Kalem geometrisi: alan silgisi (süpürülen kapsül) ve akıllı şekil tanıma.
 * Kullanıcının izniyle (11 Kasım 2026) GVNpen'in test edilmiş Geometry/SmartShape kodundan Kotlin'e
 * taşındı; algoritmalar aynıdır, yalnız veri tipi GVNnotes'un sayfa koordinatındaki örneklerine uyarlandı.
 */
object PenGeometry {
    data class P(val x:Float,val y:Float,val p:Float=1f,val t:Long=0)

    fun distance(a:P,b:P)=hypot(a.x-b.x,a.y-b.y)
    fun segmentDistance(p:P,a:P,b:P):Float {
        val dx=b.x-a.x; val dy=b.y-a.y; val den=dx*dx+dy*dy
        val t=if(den==0f) 0f else (((p.x-a.x)*dx+(p.y-a.y)*dy)/den).coerceIn(0f,1f)
        return hypot(p.x-a.x-t*dx,p.y-a.y-t*dy)
    }
    fun lerp(a:P,b:P,t:Float)=P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.p+(b.p-a.p)*t,a.t+((b.t-a.t)*t).toLong())
    private fun cross(o:P,a:P,b:P)=(a.x-o.x)*(b.y-o.y)-(a.y-o.y)*(b.x-o.x)
    private fun segmentsCross(p:P,q:P,a:P,b:P):Boolean {
        val d1=cross(a,b,p); val d2=cross(a,b,q); val d3=cross(p,q,a); val d4=cross(p,q,b)
        return (d1>0)!=(d2>0) && d1!=0f && d2!=0f && (d3>0)!=(d4>0) && d3!=0f && d4!=0f
    }
    /** p0-p1 doğru parçası, a-b kapsülüne [reach] mesafesinden yakın mı. */
    fun segmentNear(p0:P,p1:P,a:P,b:P,reach:Float):Boolean {
        if(min(p0.x,p1.x)-reach>max(a.x,b.x) || max(p0.x,p1.x)+reach<min(a.x,b.x) ||
            min(p0.y,p1.y)-reach>max(a.y,b.y) || max(p0.y,p1.y)+reach<min(a.y,b.y)) return false
        if(segmentsCross(p0,p1,a,b)) return true
        return segmentDistance(a,p0,p1)<=reach || segmentDistance(b,p0,p1)<=reach ||
            segmentDistance(p0,a,b)<=reach || segmentDistance(p1,a,b)<=reach
    }
    /**
     * p0-p1'in kapsül içindeki [t0,t1] bölümü ya da null. Dışbükey kapsüle uzaklık doğru boyunca dışbükeydir;
     * içerideki kısım tek aralıktır, iki uç da sınırın dışında döner.
     */
    fun cutInterval(p0:P,p1:P,a:P,b:P,reach:Float):FloatArray? {
        var lo=0f; var hi=1f
        repeat(40) { val m1=lo+(hi-lo)/3; val m2=hi-(hi-lo)/3
            if(segmentDistance(lerp(p0,p1,m1),a,b)<=segmentDistance(lerp(p0,p1,m2),a,b)) hi=m2 else lo=m1 }
        val tm=(lo+hi)/2
        if(segmentDistance(lerp(p0,p1,tm),a,b)>reach) return null
        var t0=0f
        if(segmentDistance(p0,a,b)>reach) { var out=0f; var inside=tm
            repeat(30) { val m=(out+inside)/2; if(segmentDistance(lerp(p0,p1,m),a,b)>reach) out=m else inside=m }; t0=out }
        var t1=1f
        if(segmentDistance(p1,a,b)>reach) { var inside=tm; var out=1f
            repeat(30) { val m=(inside+out)/2; if(segmentDistance(lerp(p0,p1,m),a,b)>reach) out=m else inside=m }; t1=out }
        return floatArrayOf(t0,t1)
    }
    /**
     * a'dan b'ye süpürülen [radius] yarıçaplı silgiyle tek çizgiyi keser. Dokunmuyorsa null (çizgi aynen kalır);
     * dokunuyorsa kalan parçalar (boş liste = tamamı silindi). Yalnız kesim noktaları eklenir.
     */
    fun eraseStroke(points:List<P>,width:Float,a:P,b:P,radius:Float,whole:Boolean):List<List<P>>? {
        if(points.isEmpty()) return null
        val reach=radius+width/2
        val hit=if(points.size==1) segmentDistance(points[0],a,b)<=reach
            else (1 until points.size).any { segmentNear(points[it-1],points[it],a,b,reach) }
        if(!hit) return null
        if(whole || points.size==1) return emptyList()
        val out=ArrayList<List<P>>(); var piece=ArrayList<P>()
        for(i in 1 until points.size) {
            val p0=points[i-1]; val p1=points[i]
            val cut=if(segmentNear(p0,p1,a,b,reach)) cutInterval(p0,p1,a,b,reach) else null
            if(cut==null) { if(piece.isEmpty()) piece.add(p0); piece.add(p1); continue }
            if(cut[0]>0f) { if(piece.isEmpty()) piece.add(p0); piece.add(lerp(p0,p1,cut[0])) }
            if(piece.isNotEmpty()) { out.add(piece); piece=ArrayList() }
            if(cut[1]<1f) { piece.add(lerp(p0,p1,cut[1])); piece.add(p1) }
        }
        if(piece.isNotEmpty()) out.add(piece)
        return out
    }

    // --- Akıllı şekil (GVNpen SmartShape) ---
    private fun resample(raw:List<P>,count:Int):List<P> {
        var length=0f; for(i in 1 until raw.size) length+=distance(raw[i-1],raw[i])
        if(length<1) return raw
        val points=ArrayList<P>(); var segment=1; var passed=0f
        for(i in 0 until count) {
            val target=length*i/(count-1)
            while(segment<raw.size-1 && passed+distance(raw[segment-1],raw[segment])<target) { passed+=distance(raw[segment-1],raw[segment]); segment++ }
            val a=raw[segment-1]; val b=raw[segment]; val len=distance(a,b)
            val t=if(len==0f) 0f else ((target-passed)/len).coerceIn(0f,1f)
            points.add(P(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t))
        }
        return points
    }
    /** "line", "circle", "rectangle" ya da null (GVNpen Geometry.recognize). */
    fun recognize(pts:List<P>):String? {
        if(pts.size<5) return null
        val first=pts[0]; val last=pts[pts.size-1]
        var length=0f; var l=first.x; var r=l; var t=first.y; var b=t
        pts.forEachIndexed { i,p -> l=min(l,p.x); r=max(r,p.x); t=min(t,p.y); b=max(b,p.y); if(i>0) length+=distance(pts[i-1],p) }
        val diag=hypot(r-l,b-t); if(diag<12) return null
        if(distance(first,last)>.8f*length) { var mx=0f; for(p in pts) mx=max(mx,segmentDistance(p,first,last)); if(mx<diag*.07) return "line" }
        if(distance(first,last)>diag*.4 || min(r-l,b-t)<8) return null
        var radialError=0.0
        for(p in pts) { val dx=(p.x-(l+r)/2)/((r-l)/2).toDouble(); val dy=(p.y-(t+b)/2)/((b-t)/2).toDouble(); radialError+=abs(hypot(dx,dy)-1) }
        if(radialError/pts.size<.18) return "circle"
        var edgeError=0f; for(p in pts) edgeError+=min(min(abs(p.x-l),abs(p.x-r)),min(abs(p.y-t),abs(p.y-b)))
        if(edgeError/pts.size<min(r-l,b-t)*.07) return "rectangle"
        return null
    }
    /**
     * Bekletilerek bitirilen serbest çizimi düzgün şekle çevirir: çizgi, üçgen, (döndürülmüş) dikdörtgen veya daire.
     * Sonuç kenar listeleridir (her kenar ayrı çizgi; daire tek çizgi). Uymuyorsa null.
     */
    fun fitShape(raw:List<P>):List<List<P>>? {
        if(raw.size<5) return null
        val pts=resample(raw,96); val first=pts[0]; val last=pts[pts.size-1]
        var l=first.x; var r=l; var t=first.y; var b=t; var path=0f
        pts.forEachIndexed { i,p -> l=min(l,p.x); r=max(r,p.x); t=min(t,p.y); b=max(b,p.y); if(i>0) path+=distance(pts[i-1],p) }
        val diag=hypot(r-l,b-t); if(diag<18) return null
        if(recognize(pts)=="line") return listOf(listOf(first,last))
        val gap=distance(first,last)
        if(gap>diag*.48 || gap>path*.26 || path+gap>diag*4.4 || r-l<10 || b-t<10) return null
        var radial=0.0
        for(p in pts) radial+=abs(hypot((p.x-(l+r)/2)/((r-l)/2).toDouble(),(p.y-(t+b)/2)/((b-t)/2).toDouble())-1)
        val coarse=pts.filterIndexed { i,_ -> i%3==0 }
        var tri:Array<P>?=null; var bestArea=0.0
        for(i in 0 until coarse.size-2) for(j in i+1 until coarse.size-1) for(k in j+1 until coarse.size) {
            val a=coarse[i]; val q=coarse[j]; val c=coarse[k]
            val area=abs(((q.x-a.x)*(c.y-a.y)-(q.y-a.y)*(c.x-a.x)).toDouble())
            if(area>bestArea) { bestArea=area; tri=arrayOf(a,q,c) }
        }
        tri?.let { if(bestArea>diag*diag*.12 && fits(pts,it,diag,.026f,.10f,path)) return polygon(it) }
        // Örneklenen kenar yönleri üzerinden en küçük alanlı yönlendirilmiş dikdörtgen.
        var rectangle:Array<P>?=null; var area=Double.MAX_VALUE
        for(i in coarse.indices) {
            val a=coarse[i]; val z=coarse[(i+1)%coarse.size]
            val angle=atan2((z.y-a.y).toDouble(),(z.x-a.x).toDouble()); val cs=cos(angle); val sn=sin(angle)
            var minX=Double.MAX_VALUE; var maxX=-minX; var minY=minX; var maxY=-minX
            for(p in pts) { val x=p.x*cs+p.y*sn; val y=-p.x*sn+p.y*cs; minX=min(minX,x); maxX=max(maxX,x); minY=min(minY,y); maxY=max(maxY,y) }
            val ar=(maxX-minX)*(maxY-minY)
            if(ar<area) { area=ar; rectangle=arrayOf(doubleArrayOf(minX,minY),doubleArrayOf(maxX,minY),doubleArrayOf(maxX,maxY),doubleArrayOf(minX,maxY))
                .map { v -> P((v[0]*cs-v[1]*sn).toFloat(),(v[0]*sn+v[1]*cs).toFloat()) }.toTypedArray() }
        }
        rectangle?.let { if(area>diag*diag*.10 && hasRectangleCorners(pts,it) && fits(pts,it,diag,.050f,.12f,path)) return polygon(it) }
        if(radial/pts.size<.14) {
            val cx=(l+r)/2; val cy=(t+b)/2; val rad=(r-l+b-t)/4
            return listOf(List(97) { i -> val a=i*PI/48; P(cx+rad*cos(a).toFloat(),cy+rad*sin(a).toFloat()) })
        }
        return null
    }
    /** Köşe kanıtı: kenarları bükük karelere izin verir, daire/ovaller kutuya dönüşmez. */
    private fun hasRectangleCorners(points:List<P>,box:Array<P>):Boolean {
        val o=box[0]; val ux=box[1].x-o.x; val uy=box[1].y-o.y; val vx=box[3].x-o.x; val vy=box[3].y-o.y
        val u2=ux*ux+uy*uy; val v2=vx*vx+vy*vy; if(u2<1 || v2<1) return false
        var supported=0
        for(corner in 0 until 4) {
            val tu=if(corner==1||corner==2) 1f else 0f; val tv=if(corner>=2) 1f else 0f; var nearest=Double.MAX_VALUE
            for(p in points) { val dx=p.x-o.x; val dy=p.y-o.y; val u=((dx*ux+dy*uy)/u2).toDouble(); val v=((dx*vx+dy*vy)/v2).toDouble(); nearest=min(nearest,hypot(u-tu,v-tv)) }
            if(nearest<.16) supported++
        }
        return supported>=3
    }
    private fun fits(points:List<P>,polygon:Array<P>,diagonal:Float,meanLimit:Float,maxLimit:Float,path:Float):Boolean {
        var sum=0.0; var mx=0.0; var perimeter=0.0
        for(i in polygon.indices) perimeter+=distance(polygon[i],polygon[(i+1)%polygon.size])
        if(path<perimeter*.70 || path>perimeter*1.35) return false
        for(p in points) { var d=Float.MAX_VALUE; for(i in polygon.indices) d=min(d,segmentDistance(p,polygon[i],polygon[(i+1)%polygon.size])); sum+=d; mx=max(mx,d.toDouble()) }
        return sum/points.size<diagonal*meanLimit && mx<diagonal*maxLimit
    }
    private fun polygon(pts:Array<P>)=pts.indices.map { i -> listOf(pts[i],pts[(i+1)%pts.size]) }

    /**
     * Kalem çizginin sonunda [holdMs] boyunca [radius] içinde kaldıysa true (akıllı şekil tetikleyicisi).
     * Örneklerin zaman damgası (P.t, ms) kullanılır.
     */
    fun heldAtEnd(points:List<P>,radius:Float,holdMs:Long):Boolean {
        if(points.size<5) return false
        val end=points.last(); var i=points.lastIndex
        while(i>0 && distance(points[i-1],end)<=radius) i--
        return end.t-points[i].t>=holdMs
    }
}
