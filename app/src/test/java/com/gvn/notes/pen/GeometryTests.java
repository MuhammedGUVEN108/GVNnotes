package com.gvn.notes.pen;
import java.util.*;
public final class GeometryTests {
    static int checks=0;
    static void check(boolean b,String label){checks++;if(!b)throw new AssertionError(label);}
    static Geometry.Point p(float x,float y){return new Geometry.Point(x,y);}
    public static void main(String[] args){
        Geometry.Stroke indexed=new Geometry.Stroke(0,2,false);for(int i=0;i<130;i++)indexed.points.add(p(i,(i%33)*7));
        for(int i=1;i<indexed.points.size();i++){Geometry.Point q=indexed.points.get(i);check(indexed.nearBlock(i,q.x,q.y,q.x,q.y,0),"block includes segment endpoint "+i);Geometry.Point prev=indexed.points.get(i-1);check(indexed.nearBlock(i,prev.x,prev.y,prev.x,prev.y,0),"block includes predecessor "+i);if(i+1<indexed.points.size()){Geometry.Point next=indexed.points.get(i+1);check(indexed.nearBlock(i,next.x,next.y,next.x,next.y,0),"block includes smoothing control extent "+i);}}
        indexed.points.add(p(999,999));check(indexed.nearBlock(130,999,999,999,999,0),"block cache rebuilt after append");
        List<Geometry.Stroke> indexedDoc=Arrays.asList(indexed);check(Geometry.erase(indexedDoc,p(-100,-100),1,false)==indexedDoc,"indexed miss preserves list identity");
        check(Geometry.segmentDistance(p(5,4),p(0,0),p(10,0))==4,"segment perpendicular");
        check(Geometry.segmentDistance(p(15,0),p(0,0),p(10,0))==5,"segment endpoint");
        Geometry.Stroke s=new Geometry.Stroke(0xff000000,2,false);s.points.add(p(0,0));s.points.add(p(100,0));List<Geometry.Stroke> doc=Arrays.asList(s);
        check(Geometry.erase(doc,p(50,12),5,false).get(0)==s,"no erase outside visible radius plus stroke");
        check(Geometry.erase(doc,p(50,0),5,true).isEmpty(),"whole stroke erase crosses segment");
        List<Geometry.Stroke> split=Geometry.erase(doc,p(50,0),5,false);check(split.size()==2,"point erase splits sparse segment");
        for(Geometry.Stroke piece:split)for(Geometry.Point q:piece.points)check(Geometry.distance(q,p(50,0))>6,"cut remains outside eraser");
        check(s.points.size()==2,"original geometry preserved");
        List<Geometry.Stroke> copied=Geometry.copy(doc);copied.get(0).points.clear();check(s.points.size()==2,"undo copy independent");
        String[] shapes={"line","arrow","rectangle","ellipse","triangle","cube","cuboid","cylinder","prism","pyramid","cone","sphere","axes"};
        for(String shape:shapes){List<Geometry.Stroke> ss=Geometry.shape(shape,p(100,100),p(10,20),0xff112233,4);check(!ss.isEmpty(),shape+" exists");for(Geometry.Stroke st:ss){check(st.points.size()>=2,shape+" independent edge");for(Geometry.Point q:st.points)check(Float.isFinite(q.x)&&Float.isFinite(q.y),shape+" finite");}}
        check(Geometry.shape("cube",p(0,0),p(100,100),0,2).size()==12,"cube 12 independent edges");
        check(Geometry.shape("prism",p(0,0),p(100,100),0,2).size()==9,"prism 9 independent edges");
        Geometry.Stroke circle=Geometry.shape("circle",p(0,0),p(100,80),0,2).get(0);for(Geometry.Point q:circle.points)check(Math.abs(Geometry.distance(q,p(50,50))-50)<.01,"circle constant radius");
        check("circle".equals(Geometry.recognize(circle.points)),"recognize circle");
        check("circle".equals(Geometry.recognize(circle.points.subList(0,circle.points.size()-5))),"tolerate open circle");
        ArrayList<Geometry.Point> line=new ArrayList<>();for(int i=0;i<20;i++)line.add(p(i*5,i*2));check("line".equals(Geometry.recognize(line)),"recognize line");
        check(Geometry.recognize(Arrays.asList(p(0,0),p(1,1)))==null,"short scribble not shape");
        check(SmartShape.fit(circle.points,0,4)!=null,"held circle fitted");
        for(int rotate:new int[]{0,25,60}){
            ArrayList<Geometry.Point> roughSquare=new ArrayList<>();double angle=Math.toRadians(rotate);
            for(int side=0;side<4;side++)for(int i=0;i<32;i++){
                double u=i/32.0,bow=8*Math.sin(Math.PI*u);double x=0,y=0;
                if(side==0){x=100*u;y=bow;}if(side==1){x=100-bow;y=100*u;}if(side==2){x=100-100*u;y=100-bow;}if(side==3){x=bow;y=100-100*u;}
                roughSquare.add(p((float)(x*Math.cos(angle)-y*Math.sin(angle)),(float)(x*Math.sin(angle)+y*Math.cos(angle))));
            }roughSquare.add(roughSquare.get(0));List<Geometry.Stroke> fit=SmartShape.fit(roughSquare,0,4);check(fit!=null&&fit.size()==4,"bowed hand drawn square "+rotate);
        }
        for(float ratio:new float[]{.5f,1,2,3}){List<Geometry.Point> oval=Geometry.shape("ellipse",p(0,0),p(100*ratio,100),0,4).get(0).points;List<Geometry.Stroke> fit=SmartShape.fit(oval,0,4);check(fit!=null&&fit.size()==1,"oval must not become rectangle "+ratio);}
        for(int rotate:new int[]{0,25,60}){
            ArrayList<Geometry.Point> rounded=new ArrayList<>();double rotation=Math.toRadians(rotate);
            for(int i=0;i<=128;i++){double a=i*2*Math.PI/128,co=Math.cos(a),si=Math.sin(a);double x=50*Math.copySign(Math.sqrt(Math.abs(co)),co),y=50*Math.copySign(Math.sqrt(Math.abs(si)),si);rounded.add(p((float)(x*Math.cos(rotation)-y*Math.sin(rotation)),(float)(x*Math.sin(rotation)+y*Math.cos(rotation))));}
            List<Geometry.Stroke> fit=SmartShape.fit(rounded,0,4);check(fit!=null&&fit.size()==4,"rounded square "+rotate);
            fit=SmartShape.fit(rounded.subList(0,rounded.size()-6),0,4);check(fit!=null&&fit.size()==4,"open rounded square "+rotate);
        }
        for(int vertices:new int[]{3,4}){ArrayList<Geometry.Point> raw=new ArrayList<>();Geometry.Point[] corners=vertices==3?new Geometry.Point[]{p(30,5),p(120,100),p(0,80),p(30,5)}:new Geometry.Point[]{p(0,30),p(100,0),p(130,100),p(30,130),p(0,30)};for(int i=1;i<corners.length;i++)for(int j=0;j<20;j++){float t=j/20f;raw.add(p(corners[i-1].x+(corners[i].x-corners[i-1].x)*t,corners[i-1].y+(corners[i].y-corners[i-1].y)*t));}raw.add(corners[0]);List<Geometry.Stroke> fit=SmartShape.fit(raw,0,4);check(fit!=null&&fit.size()==vertices,"held rotated polygon "+vertices);}
        System.out.println(checks+" geometry checks passed");
    }
}
