package com.gvn.notes.pen;
import android.graphics.*;
import java.util.List;

/** GVNpen InkView çizgi çizimi (aynen kopya): basınçlı segmentler, yuvarlak uçlar, isteğe bağlı yumuşatma, fosforlu tek yol. */
public final class PenRenderer {
    private PenRenderer(){}
    static boolean touches(RectF r,float l,float t,float rr,float b,float pad){return l-pad<=r.right&&rr+pad>=r.left&&t-pad<=r.bottom&&b+pad>=r.top;}
    public static void drawStrokes(Canvas c,List<Geometry.Stroke> strokes,Paint p,boolean smooth){Path path=new Path();for(Geometry.Stroke s:strokes)drawStroke(c,s,0,s.points.size(),p,smooth,path,null);}
    /**
     * Draws points [from,to) of one stroke; a pen stroke split at k into [0,k) and [k-1,n) joins seamlessly.
     * With cull (dp), pen segments that cannot reach it are skipped; the drawn pixels inside cull are unchanged.
     */
    public static void drawStroke(Canvas c,Geometry.Stroke s,int from,int to,Paint p,boolean smooth,Path path,RectF cull){if(to-from<=0)return;p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);p.setStyle(Paint.Style.STROKE);p.setColor(s.color);if(s.marker)p.setAlpha(Math.round(Color.alpha(s.color)*.32f));
        if(s.marker){path.reset();Geometry.Point a=s.points.get(from);path.moveTo(a.x,a.y);for(int i=from+1;i<to;i++){Geometry.Point b=s.points.get(i);path.lineTo(b.x,b.y);}p.setStrokeWidth(s.width);if(to-from==1)c.drawPoint(a.x,a.y,p);else c.drawPath(path,p);}
        else{float pad=s.width+1;Geometry.Point a=s.points.get(from);if(cull==null||touches(cull,a.x,a.y,a.x,a.y,pad)){p.setStrokeWidth(s.width*a.p);c.drawPoint(a.x,a.y,p);}
            for(int i=from+1;i<to;i++){if(cull!=null&&(i-1)%32==0&&!s.nearBlock(i,cull.left,cull.top,cull.right,cull.bottom,pad)){int end=Math.min(to-1,((i-1)/32+1)*32);Geometry.Point q=s.points.get(end);if(smooth&&end<to-1){Geometry.Point z=s.points.get(end+1);a=new Geometry.Point((q.x+z.x)/2,(q.y+z.y)/2,q.p);}else a=q;i=end;continue;}Geometry.Point b=s.points.get(i);
                if(smooth&&i<to-1){Geometry.Point z=s.points.get(i+1);float mx=(b.x+z.x)/2,my=(b.y+z.y)/2;
                    if(cull==null||touches(cull,Math.min(a.x,Math.min(b.x,mx)),Math.min(a.y,Math.min(b.y,my)),Math.max(a.x,Math.max(b.x,mx)),Math.max(a.y,Math.max(b.y,my)),pad)){p.setStrokeWidth(s.width*(a.p+b.p)/2);path.reset();path.moveTo(a.x,a.y);path.quadTo(b.x,b.y,mx,my);c.drawPath(path,p);}
                    a=new Geometry.Point(mx,my,b.p);}
                else{if(cull==null||touches(cull,Math.min(a.x,b.x),Math.min(a.y,b.y),Math.max(a.x,b.x),Math.max(a.y,b.y),pad)){p.setStrokeWidth(s.width*(a.p+b.p)/2);c.drawLine(a.x,a.y,b.x,b.y,p);}a=b;}}}
    }
}
