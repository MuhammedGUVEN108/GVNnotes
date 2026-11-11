package com.gvn.notes.pen;

import java.util.*;

/** Platform independent geometry. Coordinates use density-independent pixels. */
public final class Geometry {
    public static final class Point {
        public final float x,y,p;
        public Point(float x,float y){this(x,y,1);}
        public Point(float x,float y,float p){this.x=x;this.y=y;this.p=p;}
    }
    public static final class Stroke {
        public final ArrayList<Point> points=new ArrayList<>();
        public int color; public float width; public boolean marker;
        public Stroke(int c,float w,boolean h){color=c;width=w;marker=h;}
        public Stroke copy(){Stroke s=new Stroke(color,width,marker);s.points.addAll(points);return s;}
        private float[] blocks;private int blockCount=-1,longestCount=-1;private float longest;
        float longestSegment(){if(longestCount!=points.size()){longest=0;for(int i=1;i<points.size();i++)longest=Math.max(longest,distance(points.get(i-1),points.get(i)));longestCount=points.size();}return longest;}
        boolean nearBlock(int segment,float l,float t,float r,float b,float pad){int n=points.size();if(blockCount!=n){blocks=new float[((Math.max(0,n-1)+31)/32)*4];for(int k=0;k<blocks.length/4;k++){float x=Float.MAX_VALUE,y=x,xx=-x,yy=-x;for(int i=k*32;i<=Math.min(n-1,(k+1)*32+1);i++){Point p=points.get(i);x=Math.min(x,p.x);y=Math.min(y,p.y);xx=Math.max(xx,p.x);yy=Math.max(yy,p.y);}int j=k*4;blocks[j]=x;blocks[j+1]=y;blocks[j+2]=xx;blocks[j+3]=yy;}blockCount=n;}int j=((segment-1)/32)*4;return blocks[j]-pad<=r&&blocks[j+2]+pad>=l&&blocks[j+1]-pad<=b&&blocks[j+3]+pad>=t;}
        private final float[] box=new float[4];private int boxCount=-1;
        /** Centerline bounds {left,top,right,bottom}; recomputed only when points were added. */
        public float[] bounds(){int n=points.size();if(n!=boxCount){float l=Float.MAX_VALUE,t=Float.MAX_VALUE,r=-Float.MAX_VALUE,b=-Float.MAX_VALUE;for(int i=0;i<n;i++){Point p=points.get(i);if(p.x<l)l=p.x;if(p.x>r)r=p.x;if(p.y<t)t=p.y;if(p.y>b)b=p.y;}box[0]=l;box[1]=t;box[2]=r;box[3]=b;boxCount=n;}return box;}
    }
    public static float distance(Point a,Point b){return (float)Math.hypot(a.x-b.x,a.y-b.y);}
    public static float segmentDistance(Point p,Point a,Point b){
        float dx=b.x-a.x,dy=b.y-a.y,den=dx*dx+dy*dy;
        float t=den==0?0:Math.max(0,Math.min(1,((p.x-a.x)*dx+(p.y-a.y)*dy)/den));
        return (float)Math.hypot(p.x-a.x-t*dx,p.y-a.y-t*dy);
    }
    public static List<Stroke> copy(List<Stroke> strokes){List<Stroke> r=new ArrayList<>();for(Stroke s:strokes)r.add(s.copy());return r;}
    public static List<Stroke> erase(List<Stroke> source,Point center,float radius,boolean whole){return erase(source,center,center,radius,whole);}
    /**
     * Erases with a circle swept from a to b (a capsule). Returns source itself when nothing is hit, so callers can
     * skip redrawing. Partial erase cuts each segment at the exact capsule boundary and adds no other points.
     */
    static final class Erased extends ArrayList<Stroke> {final ArrayList<Stroke> removed=new ArrayList<>();Erased(List<Stroke> prefix){super(prefix);}}
    public static List<Stroke> erase(List<Stroke> source,Point a,Point b,float radius,boolean whole){
        List<Stroke> out=null;float l=Math.min(a.x,b.x)-radius,t=Math.min(a.y,b.y)-radius,r=Math.max(a.x,b.x)+radius,bt=Math.max(a.y,b.y)+radius;
        for(int k=0;k<source.size();k++){Stroke s=source.get(k);float reach=radius+s.width/2,w=s.width/2;float[] box=s.bounds();
            boolean hit=false;
            if(!s.points.isEmpty()&&box[0]-w<=r&&box[2]+w>=l&&box[1]-w<=bt&&box[3]+w>=t){
                if(s.points.size()==1)hit=capsuleDistance(s.points.get(0),a,b)<=reach;
                else for(int i=1;i<s.points.size()&&!hit;i++){if(!s.nearBlock(i,l,t,r,bt,w)){i=Math.min(s.points.size()-1,((i-1)/32+1)*32);continue;}hit=segmentNear(s.points.get(i-1),s.points.get(i),a,b,reach);}
            }
            if(!hit){if(out!=null)out.add(s);continue;}
            if(out==null)out=new Erased(source.subList(0,k));
            ((Erased)out).removed.add(s);
            if(whole||s.points.size()==1)continue;
            Stroke piece=new Stroke(s.color,s.width,s.marker);
            for(int i=1;i<s.points.size();i++){Point p0=s.points.get(i-1),p1=s.points.get(i);
                float[] cut=s.nearBlock(i,l,t,r,bt,w)&&segmentNear(p0,p1,a,b,reach)?cutInterval(p0,p1,a,b,reach):null;
                if(cut==null){if(piece.points.isEmpty())piece.points.add(p0);piece.points.add(p1);continue;}
                if(cut[0]>0){if(piece.points.isEmpty())piece.points.add(p0);piece.points.add(lerp(p0,p1,cut[0]));}
                if(!piece.points.isEmpty()){out.add(piece);piece=new Stroke(s.color,s.width,s.marker);}
                if(cut[1]<1){piece.points.add(lerp(p0,p1,cut[1]));piece.points.add(p1);}
            }
            if(!piece.points.isEmpty())out.add(piece);
        }
        return out==null?source:out;
    }
    public static Point lerp(Point a,Point b,float t){return new Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.p+(b.p-a.p)*t);}
    static float capsuleDistance(Point p,Point a,Point b){return segmentDistance(p,a,b);}
    /** True when segment p0-p1 comes within reach of segment a-b. */
    static boolean segmentNear(Point p0,Point p1,Point a,Point b,float reach){
        if(Math.min(p0.x,p1.x)-reach>Math.max(a.x,b.x)||Math.max(p0.x,p1.x)+reach<Math.min(a.x,b.x)||Math.min(p0.y,p1.y)-reach>Math.max(a.y,b.y)||Math.max(p0.y,p1.y)+reach<Math.min(a.y,b.y))return false;
        if(segmentsCross(p0,p1,a,b))return true;
        return segmentDistance(a,p0,p1)<=reach||segmentDistance(b,p0,p1)<=reach||segmentDistance(p0,a,b)<=reach||segmentDistance(p1,a,b)<=reach;
    }
    static boolean segmentsCross(Point p,Point q,Point a,Point b){float d1=cross(a,b,p),d2=cross(a,b,q),d3=cross(p,q,a),d4=cross(p,q,b);return (d1>0)!=(d2>0)&&d1!=0&&d2!=0&&(d3>0)!=(d4>0)&&d3!=0&&d4!=0;}
    static float cross(Point o,Point a,Point b){return (a.x-o.x)*(b.y-o.y)-(a.y-o.y)*(b.x-o.x);}
    /**
     * The part [t0,t1] of p0-p1 inside the capsule, or null. Distance to a convex capsule is convex along a segment,
     * so the inside part is one interval; both ends are returned on the outside of the boundary.
     */
    static float[] cutInterval(Point p0,Point p1,Point a,Point b,float reach){
        float lo=0,hi=1;for(int i=0;i<40;i++){float m1=lo+(hi-lo)/3,m2=hi-(hi-lo)/3;if(capsuleDistance(lerp(p0,p1,m1),a,b)<=capsuleDistance(lerp(p0,p1,m2),a,b))hi=m2;else lo=m1;}
        float tm=(lo+hi)/2;if(capsuleDistance(lerp(p0,p1,tm),a,b)>reach)return null;
        float t0=0;if(capsuleDistance(p0,a,b)>reach){float out=0,in=tm;for(int i=0;i<30;i++){float m=(out+in)/2;if(capsuleDistance(lerp(p0,p1,m),a,b)>reach)out=m;else in=m;}t0=out;}
        float t1=1;if(capsuleDistance(p1,a,b)>reach){float in=tm,out=1;for(int i=0;i<30;i++){float m=(in+out)/2;if(capsuleDistance(lerp(p0,p1,m),a,b)>reach)out=m;else in=m;}t1=out;}
        return new float[]{t0,t1};
    }
    public static List<Stroke> shape(String kind,Point a,Point b,int color,float width){
        List<Stroke> out=new ArrayList<>();float l=Math.min(a.x,b.x),t=Math.min(a.y,b.y),w=Math.abs(b.x-a.x),h=Math.abs(b.y-a.y);
        class Builder {
            Point p(double x,double y){return new Point(l+(float)x*w,t+(float)y*h);}
            void line(Point... pp){Stroke s=new Stroke(color,width,false);Collections.addAll(s.points,pp);out.add(s);}
            void poly(double... xy){for(int i=0;i<xy.length/2;i++){int j=(i+1)%(xy.length/2);line(p(xy[i*2],xy[i*2+1]),p(xy[j*2],xy[j*2+1]));}}
            void ellipse(double cx,double cy,double rx,double ry){Stroke s=new Stroke(color,width,false);for(int i=0;i<=96;i++){double z=i*Math.PI/48;s.points.add(p(cx+rx*Math.cos(z),cy+ry*Math.sin(z)));}out.add(s);}
            void cube(double dx,double dy){double[][] f={{0,dy},{1-dx,dy},{1-dx,1},{0,1}},back={{dx,0},{1,0},{1,1-dy},{dx,1-dy}};for(int i=0;i<4;i++){int j=(i+1)%4;line(p(f[i][0],f[i][1]),p(f[j][0],f[j][1]));line(p(back[i][0],back[i][1]),p(back[j][0],back[j][1]));line(p(f[i][0],f[i][1]),p(back[i][0],back[i][1]));}}
        }Builder q=new Builder();
        switch(kind){
            case "line":q.line(a,b);break;
            case "arrow":{q.line(a,b);double angle=Math.atan2(b.y-a.y,b.x-a.x);float len=Math.min(18,distance(a,b)*.25f);q.line(new Point(b.x-len*(float)Math.cos(angle-.5),b.y-len*(float)Math.sin(angle-.5)),b,new Point(b.x-len*(float)Math.cos(angle+.5),b.y-len*(float)Math.sin(angle+.5)));break;}
            case "rectangle":q.poly(0,0,1,0,1,1,0,1);break;
            case "triangle":q.poly(.5,0,1,1,0,1);break;
            case "ellipse":q.ellipse(.5,.5,.5,.5);break;
            case "circle":{float d=Math.max(w,h);return shape("ellipse",a,new Point(a.x+(b.x<a.x?-d:d),a.y+(b.y<a.y?-d:d)),color,width);}
            case "cube":q.cube(.27,.25);break;
            case "cuboid":q.cube(.15,.18);break;
            case "cylinder":q.ellipse(.5,.14,.5,.14);q.ellipse(.5,.86,.5,.14);q.line(q.p(0,.14),q.p(0,.86));q.line(q.p(1,.14),q.p(1,.86));break;
            case "prism":q.poly(0,.25,0,1,.67,1);q.poly(.33,0,.33,.75,1,.75);q.line(q.p(0,.25),q.p(.33,0));q.line(q.p(0,1),q.p(.33,.75));q.line(q.p(.67,1),q.p(1,.75));break;
            case "pyramid":q.poly(.5,.52,1,.75,.5,1,0,.75);for(double[] v:new double[][]{{.5,.52},{1,.75},{.5,1},{0,.75}})q.line(q.p(.5,0),q.p(v[0],v[1]));break;
            case "cone":q.ellipse(.5,.86,.5,.14);q.line(q.p(.5,0),q.p(0,.86));q.line(q.p(.5,0),q.p(1,.86));break;
            case "sphere":q.ellipse(.5,.5,.5,.5);q.ellipse(.5,.5,.5,.15);break;
            case "axes":q.line(q.p(0,.5),q.p(1,.5));q.line(q.p(.5,0),q.p(.5,1));for(Point end:new Point[]{q.p(0,.5),q.p(1,.5),q.p(.5,0),q.p(.5,1)}){double ang=Math.atan2(end.y-(t+h/2),end.x-(l+w/2));float len=Math.min(10,Math.min(w,h)*.15f);q.line(new Point(end.x-len*(float)Math.cos(ang-.5),end.y-len*(float)Math.sin(ang-.5)),end,new Point(end.x-len*(float)Math.cos(ang+.5),end.y-len*(float)Math.sin(ang+.5)));}break;
            default:throw new IllegalArgumentException("Bilinmeyen şekil: "+kind);
        }return out;
    }
    public static String recognize(List<Point> pts){
        if(pts.size()<5)return null;Point first=pts.get(0),last=pts.get(pts.size()-1);float length=0,l=first.x,r=l,t=first.y,b=t;
        for(int i=0;i<pts.size();i++){Point p=pts.get(i);l=Math.min(l,p.x);r=Math.max(r,p.x);t=Math.min(t,p.y);b=Math.max(b,p.y);if(i>0)length+=distance(pts.get(i-1),p);}
        float diag=(float)Math.hypot(r-l,b-t);if(diag<12)return null;
        if(distance(first,last)>.8f*length){float max=0;for(Point p:pts)max=Math.max(max,segmentDistance(p,first,last));if(max<diag*.07)return "line";}
        if(distance(first,last)>diag*.4||Math.min(r-l,b-t)<8)return null;
        double radialError=0;for(Point p:pts){double dx=(p.x-(l+r)/2)/((r-l)/2),dy=(p.y-(t+b)/2)/((b-t)/2);radialError+=Math.abs(Math.hypot(dx,dy)-1);}
        if(radialError/pts.size()<.18)return "circle";
        float edgeError=0;for(Point p:pts)edgeError+=Math.min(Math.min(Math.abs(p.x-l),Math.abs(p.x-r)),Math.min(Math.abs(p.y-t),Math.abs(p.y-b)));
        if(edgeError/pts.size()<Math.min(r-l,b-t)*.07)return "rectangle";
        return null;
    }
}
