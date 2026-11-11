package com.gvn.notes.pen;
public final class SizeScale {
    public static float maximum(String kind){return kind.equals("pen")?30:kind.equals("shape")?15:100;}
    public static float width(String kind,int value){return Math.max(0,Math.min(100,value))*maximum(kind)/100f;}
    public static int progress(String kind,float width){return Math.max(0,Math.min(100,Math.round(width*100f/maximum(kind))));}
}
