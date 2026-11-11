package com.gvn.notes.pen;
import java.util.*;

/** Swept eraser: exact cuts, no over/under erasing, no point inflation; benchmark against the 1.0.5 eraser. */
public final class EraseTests {
    static int checks=0;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    static Geometry.Point p(float x,float y,float pr){return new Geometry.Point(x,y,pr);}
    static List<Geometry.Stroke> page(Random r,int strokes,int points,float w,float h,float step){
        List<Geometry.Stroke> page=new ArrayList<>();
        for(int i=0;i<strokes;i++){Geometry.Stroke s=new Geometry.Stroke(0xff000000,1+r.nextFloat()*8,false);float x=r.nextFloat()*w,y=r.nextFloat()*h,angle=r.nextFloat()*6.28f;
            for(int j=0;j<points;j++){angle+=(r.nextFloat()-.5f)*.8f;x=Math.max(0,Math.min(w,x+(float)Math.cos(angle)*step*(.3f+r.nextFloat())));y=Math.max(0,Math.min(h,y+(float)Math.sin(angle)*step*(.3f+r.nextFloat())));s.points.add(p(x,y,.3f+r.nextFloat()*1.2f));}
            page.add(s);}
        return page;
    }
    /** Smallest distance from a stroke's centerline to the eraser capsule, sampled every 0.02 dp. */
    static float minDistance(Geometry.Stroke s,Geometry.Point a,Geometry.Point b){
        float best=Geometry.segmentDistance(s.points.get(0),a,b);
        for(int i=1;i<s.points.size();i++){Geometry.Point p0=s.points.get(i-1),p1=s.points.get(i);int n=Math.max(1,(int)(Geometry.distance(p0,p1)/.02f));for(int k=0;k<=n;k++)best=Math.min(best,Geometry.segmentDistance(Geometry.lerp(p0,p1,(float)k/n),a,b));}
        return best;
    }
    static int points(List<Geometry.Stroke> page){int n=0;for(Geometry.Stroke s:page)n+=s.points.size();return n;}
    static boolean containsSame(List<Geometry.Stroke> list,Geometry.Stroke s){for(Geometry.Stroke x:list)if(x==s)return true;return false;}
    /** The 1.0.5 eraser, kept verbatim as the reference for the benchmark. */
    static List<Geometry.Stroke> legacyErase(List<Geometry.Stroke> source,Geometry.Point center,float radius,boolean whole){
        List<Geometry.Stroke> out=new ArrayList<>();
        for(Geometry.Stroke s:source){
            float reach=radius+s.width/2;boolean hit=false;
            for(int i=0;i<s.points.size();i++)if(i==0?Geometry.distance(center,s.points.get(0))<=reach:Geometry.segmentDistance(center,s.points.get(i-1),s.points.get(i))<=reach){hit=true;break;}
            if(!hit){out.add(s);continue;} if(whole)continue;
            Geometry.Stroke piece=new Geometry.Stroke(s.color,s.width,s.marker);
            if(s.points.size()==1)continue;
            for(int i=1;i<s.points.size();i++){
                Geometry.Point a=s.points.get(i-1),b=s.points.get(i);
                int steps=Math.max(1,(int)Math.ceil(Geometry.distance(a,b)/Math.max(.5f,Math.min(2,reach/3))));
                for(int j=0;j<=steps;j++){
                    float t=(float)j/steps;Geometry.Point q=new Geometry.Point(a.x+(b.x-a.x)*t,a.y+(b.y-a.y)*t,a.p+(b.p-a.p)*t);
                    if(Geometry.distance(center,q)<=reach){if(!piece.points.isEmpty()){out.add(piece);piece=new Geometry.Stroke(s.color,s.width,s.marker);}}
                    else if(piece.points.isEmpty()||Geometry.distance(piece.points.get(piece.points.size()-1),q)>.01)piece.points.add(q);
                }
            }
            if(!piece.points.isEmpty())out.add(piece);
        }return out;
    }
    public static void main(String[] args){
        Random r=new Random(7);
        // Randomized correctness against a dense-sampling oracle.
        for(int trial=0;trial<400;trial++){
            List<Geometry.Stroke> page=page(r,6,40,200,200,4);Geometry.Point a=p(r.nextFloat()*200,r.nextFloat()*200,1),b=trial%4==0?a:p(a.x+(r.nextFloat()-.5f)*60,a.y+(r.nextFloat()-.5f)*60,1);
            float radius=2+r.nextFloat()*18;boolean whole=r.nextBoolean();
            List<Geometry.Stroke> out=Geometry.erase(page,a,b,radius,whole);
            boolean anyHit=false;
            for(Geometry.Stroke s:page){float reach=radius+s.width/2,d=minDistance(s,a,b);
                if(d<reach-.01f){anyHit=true;check(!containsSame(out,s),"stroke touched by the eraser is removed or cut");}
                else if(d>reach+.01f)check(containsSame(out,s),"stroke outside the eraser stays the same object");
            }
            if(!anyHit&&out!=page){boolean borderline=false;for(Geometry.Stroke s:page)borderline|=Math.abs(minDistance(s,a,b)-(radius+s.width/2))<=.01f;check(borderline,"no hit returns the source list");}
            if(whole){for(Geometry.Stroke s:out)check(containsSame(page,s),"whole erase only removes strokes");continue;}
            int cutSegments=0;
            for(Geometry.Stroke s:page)if(!containsSame(out,s)){float reach=radius+s.width/2;
                for(int i=1;i<s.points.size();i++)if(Geometry.segmentNear(s.points.get(i-1),s.points.get(i),a,b,reach))cutSegments++;}
            check(points(out)<=points(page)+2*cutSegments,"partial erase adds only cut points");
            for(Geometry.Stroke piece:out){if(containsSame(page,piece))continue;float reach=radius+piece.width/2;
                for(Geometry.Point q:piece.points){check(Geometry.segmentDistance(q,a,b)>reach,"fragment points stay outside the eraser");check(q.p>=.3f-1e-4f&&q.p<=1.5f+1e-4f,"cut pressure interpolated");}}
            // Everything clearly outside the eraser is still covered by some output segment.
            for(Geometry.Stroke s:page){if(containsSame(out,s))continue;float reach=radius+s.width/2;
                for(int i=1;i<s.points.size();i++){Geometry.Point p0=s.points.get(i-1),p1=s.points.get(i);
                    for(int k=0;k<=10;k++){Geometry.Point q=Geometry.lerp(p0,p1,k/10f);if(Geometry.segmentDistance(q,a,b)<=reach+.05f)continue;
                        boolean covered=false;for(Geometry.Stroke o:out){if(o.color!=s.color||o.width!=s.width)continue;if(o.points.size()==1)covered|=Geometry.distance(o.points.get(0),q)<.02f;for(int j=1;j<o.points.size()&&!covered;j++)covered=Geometry.segmentDistance(q,o.points.get(j-1),o.points.get(j))<.02f;if(covered)break;}
                        check(covered,"no over-erasing outside the eraser");}}}
        }
        // Long sparse segment (shape edge): one cut, two endpoints kept, cut points just outside.
        Geometry.Stroke edge=new Geometry.Stroke(0xff000000,2,false);edge.points.add(p(0,0,1));edge.points.add(p(500,0,1));
        List<Geometry.Stroke> cut=Geometry.erase(Arrays.asList(edge),p(250,-30,1),p(250,30,1),10,false);
        check(cut.size()==2&&cut.get(0).points.size()==2&&cut.get(1).points.size()==2,"sweep across an edge cuts once");
        check(Math.abs(cut.get(0).points.get(1).x-239)<.01f&&Math.abs(cut.get(1).points.get(0).x-261)<.01f,"exact cut positions");
        // Benchmark: same eraser path, 1.0.5 stepping vs one swept call per sample.
        List<Geometry.Stroke> dense=page(new Random(3),300,300,800,1200,3);float radius=15;
        List<Geometry.Point> path=new ArrayList<>();for(int i=0;i<=400;i++)path.add(p(100+i*1.5f,200+(float)Math.sin(i*.05)*300+i,1));
        for(boolean whole:new boolean[]{false,true}){
            List<Geometry.Stroke> legacy=dense;long t0=System.nanoTime();
            for(int i=0;i<path.size();i++){Geometry.Point q=path.get(i);if(i>0){Geometry.Point last=path.get(i-1);int n=(int)Math.ceil(Geometry.distance(last,q)/Math.max(1,radius/2));for(int k=1;k<n;k++){float t=(float)k/n;legacy=legacyErase(legacy,p(last.x+(q.x-last.x)*t,last.y+(q.y-last.y)*t,1),radius,whole);}}legacy=legacyErase(legacy,q,radius,whole);}
            long t1=System.nanoTime();List<Geometry.Stroke> swept=dense;
            for(int i=0;i<path.size();i++)swept=Geometry.erase(swept,i>0?path.get(i-1):path.get(i),path.get(i),radius,whole);
            long t2=System.nanoTime();
            System.out.println((whole?"whole":"partial")+" erase, "+path.size()+" samples on "+points(dense)+" points: 1.0.5 "+(t1-t0)/1000000+" ms -> "+points(legacy)+" points, swept "+(t2-t1)/1000000+" ms -> "+points(swept)+" points");
            check(points(swept)<=points(legacy),"swept erase never inflates more than 1.0.5");
            // The capsule covers the gaps between 1.0.5's stamps, so it may remove a few more borderline strokes.
            if(whole)check(swept.size()<=legacy.size()&&legacy.size()-swept.size()<=legacy.size()/50+2,"whole erase removes what 1.0.5 removes");
        }
        System.out.println(checks+" erase checks passed");
    }
}
