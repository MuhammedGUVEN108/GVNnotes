package com.gvn.notes.pen;
import java.util.*;

/** Fits held freehand outlines without changing manually selected ellipse tools. */
public final class SmartShape {
    public static List<Geometry.Stroke> fit(List<Geometry.Point> raw,int color,float width){
        if(raw.size()<5)return null;List<Geometry.Point> pts=resample(raw,96);Geometry.Point first=pts.get(0),last=pts.get(pts.size()-1);
        float l=first.x,r=l,t=first.y,b=t,path=0;for(int i=0;i<pts.size();i++){Geometry.Point p=pts.get(i);l=Math.min(l,p.x);r=Math.max(r,p.x);t=Math.min(t,p.y);b=Math.max(b,p.y);if(i>0)path+=Geometry.distance(pts.get(i-1),p);}
        float diag=(float)Math.hypot(r-l,b-t);if(diag<18)return null;
        if("line".equals(Geometry.recognize(pts)))return Geometry.shape("line",first,last,color,width);
        float gap=Geometry.distance(first,last);if(gap>diag*.48||gap>path*.26||path+gap>diag*4.4||r-l<10||b-t<10)return null;
        double radial=0;for(Geometry.Point p:pts)radial+=Math.abs(Math.hypot((p.x-(l+r)/2)/((r-l)/2),(p.y-(t+b)/2)/((b-t)/2))-1);
        ArrayList<Geometry.Point> coarse=new ArrayList<>();for(int i=0;i<pts.size();i+=3)coarse.add(pts.get(i));
        Geometry.Point[] tri=null;double bestArea=0;
        for(int i=0;i<coarse.size()-2;i++)for(int j=i+1;j<coarse.size()-1;j++)for(int k=j+1;k<coarse.size();k++){
            Geometry.Point a=coarse.get(i),q=coarse.get(j),c=coarse.get(k);double area=Math.abs((q.x-a.x)*(c.y-a.y)-(q.y-a.y)*(c.x-a.x));if(area>bestArea){bestArea=area;tri=new Geometry.Point[]{a,q,c};}}
        if(tri!=null&&bestArea>diag*diag*.12&&fits(pts,tri,diag,.026f,.10f,path))return polygon(tri,color,width);
        // Minimum-area oriented rectangle over sampled edge directions.
        Geometry.Point[] rectangle=null;double area=Double.MAX_VALUE;
        for(int i=0;i<coarse.size();i++){
            Geometry.Point a=coarse.get(i),z=coarse.get((i+1)%coarse.size());double angle=Math.atan2(z.y-a.y,z.x-a.x),cos=Math.cos(angle),sin=Math.sin(angle),minX=Double.MAX_VALUE,maxX=-minX,minY=minX,maxY=-minX;
            for(Geometry.Point p:pts){double x=p.x*cos+p.y*sin,y=-p.x*sin+p.y*cos;minX=Math.min(minX,x);maxX=Math.max(maxX,x);minY=Math.min(minY,y);maxY=Math.max(maxY,y);}double ar=(maxX-minX)*(maxY-minY);
            if(ar<area){area=ar;rectangle=new Geometry.Point[4];int j=0;for(double[] v:new double[][]{{minX,minY},{maxX,minY},{maxX,maxY},{minX,maxY}})rectangle[j++]=new Geometry.Point((float)(v[0]*cos-v[1]*sin),(float)(v[0]*sin+v[1]*cos));}
        }
        if(rectangle!=null&&area>diag*diag*.10&&hasRectangleCorners(pts,rectangle)&&fits(pts,rectangle,diag,.050f,.12f,path))return polygon(rectangle,color,width);
        if(radial/pts.size()<.14){float cx=(l+r)/2,cy=(t+b)/2,rad=(r-l+b-t)/4;Geometry.Stroke circle=new Geometry.Stroke(color,width,false);for(int i=0;i<=96;i++){double a=i*Math.PI/48;circle.points.add(new Geometry.Point(cx+rad*(float)Math.cos(a),cy+rad*(float)Math.sin(a)));}return Collections.singletonList(circle);}
        return null;
    }
    /** Corner evidence allows bowed sides without turning circles/ovals into boxes.
     * Normalize each axis separately so wide ovals cannot pass as rectangles. */
    static boolean hasRectangleCorners(List<Geometry.Point> points,Geometry.Point[] box){
        Geometry.Point origin=box[0];float ux=box[1].x-origin.x,uy=box[1].y-origin.y,vx=box[3].x-origin.x,vy=box[3].y-origin.y;
        float u2=ux*ux+uy*uy,v2=vx*vx+vy*vy;if(u2<1||v2<1)return false;int supported=0;
        for(int corner=0;corner<4;corner++){
            float targetU=corner==1||corner==2?1:0,targetV=corner>=2?1:0;double nearest=Double.MAX_VALUE;
            for(Geometry.Point p:points){float dx=p.x-origin.x,dy=p.y-origin.y;double u=(dx*ux+dy*uy)/u2,v=(dx*vx+dy*vy)/v2;nearest=Math.min(nearest,Math.hypot(u-targetU,v-targetV));}
            if(nearest<.16)supported++;
        }return supported>=3;
    }
    static boolean fits(List<Geometry.Point> points,Geometry.Point[] polygon,float diagonal,float meanLimit,float maxLimit,float path){double sum=0,max=0,perimeter=0;for(int i=0;i<polygon.length;i++)perimeter+=Geometry.distance(polygon[i],polygon[(i+1)%polygon.length]);if(path<perimeter*.70||path>perimeter*1.35)return false;for(Geometry.Point p:points){float d=Float.MAX_VALUE;for(int i=0;i<polygon.length;i++)d=Math.min(d,Geometry.segmentDistance(p,polygon[i],polygon[(i+1)%polygon.length]));sum+=d;max=Math.max(max,d);}return sum/points.size()<diagonal*meanLimit&&max<diagonal*maxLimit;}
    static List<Geometry.Stroke> polygon(Geometry.Point[] pts,int color,float width){List<Geometry.Stroke> result=new ArrayList<>();for(int i=0;i<pts.length;i++){Geometry.Stroke s=new Geometry.Stroke(color,width,false);s.points.add(pts[i]);s.points.add(pts[(i+1)%pts.length]);result.add(s);}return result;}
    static List<Geometry.Point> resample(List<Geometry.Point> raw,int count){float length=0;for(int i=1;i<raw.size();i++)length+=Geometry.distance(raw.get(i-1),raw.get(i));if(length<1)return raw;List<Geometry.Point> points=new ArrayList<>();int segment=1;float passed=0;for(int i=0;i<count;i++){float target=length*i/(count-1);while(segment<raw.size()-1&&passed+Geometry.distance(raw.get(segment-1),raw.get(segment))<target){passed+=Geometry.distance(raw.get(segment-1),raw.get(segment));segment++;}Geometry.Point a=raw.get(segment-1),b=raw.get(segment);float len=Geometry.distance(a,b),t=len==0?0:Math.max(0,Math.min(1,(target-passed)/len));points.add(new Geometry.Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t));}return points;}
}
