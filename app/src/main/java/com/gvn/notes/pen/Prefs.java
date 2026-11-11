package com.gvn.notes.pen;
import android.content.*;
import android.graphics.Color;
import org.json.*;

public final class Prefs {
    public int color=0xffef4444,accent=0xff4fc4dd;
    public float pen=4,marker=22,shape=4,eraser=16,opacity=1,scale=1;
    public boolean predict=false,light=false,pressure=true,smooth=false,whole=true,smart=true,onlyPen=true,button=true,hoverErase=true,autoNavigate=android.os.Build.VERSION.SDK_INT>=34,fixedSizes=false,showCursor=true;
    public int hold=75,smartDelay=300;
    public int[] quick={0xff00d9f5,0xffffff00,0xffff00ff,0xff000000};
    public String tool="pen";public int x=18,y=85;public boolean folded=false;
    public JSONObject json() {
        JSONObject o=new JSONObject();try{
            o.put("DefaultsVersion",4);o.put("format","GVNpen-profile-1");o.put("Color",hex(color));o.put("AccentColor",hex(accent));o.put("PenSize",pen);o.put("HighlighterSize",marker);o.put("ShapeSize",shape);o.put("EraserSize",eraser);o.put("Opacity",opacity);o.put("ToolbarScale",scale);o.put("Theme",light?"Light":"Dark");o.put("PressureSensitive",pressure);o.put("SmoothStrokes",smooth);o.put("PenPrediction",predict);o.put("EraserMode",whole?"Stroke":"Point");o.put("SmartShapesEnabled",smart);o.put("SmartShapeDelayMs",smartDelay);o.put("HoldDelayMs",hold);o.put("OnlySPen",onlyPen);o.put("SPenButton",button);o.put("HoverErase",hoverErase);o.put("ExperimentalAutoNavigate",autoNavigate);o.put("SizeControlMode",fixedSizes?"Presets":"Slider");o.put("ShowPenCursor",showCursor);o.put("LastTool",tool);o.put("ToolbarLeft",x);o.put("ToolbarTop",y);o.put("ToolbarFolded",folded);JSONArray a=new JSONArray();for(int c:quick)a.put(hex(c));o.put("QuickColors",a);
        }catch(JSONException e){throw new IllegalStateException(e);}return o;
    }
    static float bound(double v,float min,float max,float def){return Double.isNaN(v)||Double.isInfinite(v)?def:Math.max(min,Math.min(max,(float)v));}
    static int col(String v,int def){try{return Color.parseColor(v);}catch(Exception e){return def;}}
    public static String hex(int c){return String.format(java.util.Locale.ROOT,"#%08X",c);}
    public static Prefs from(JSONObject o){Prefs p=new Prefs();
        p.color=col(o.optString("Color"),p.color);p.accent=col(o.optString("AccentColor"),p.accent);
        p.pen=bound(o.optDouble("PenSize",4),0,100,4);p.marker=bound(o.optDouble("HighlighterSize",22),1,100,22);p.shape=bound(o.optDouble("ShapeSize",p.pen),0,100,p.pen);p.eraser=bound(o.optDouble("EraserSize",16),1,100,16);p.opacity=bound(o.optDouble("Opacity",1),.1f,1,1);p.scale=bound(o.optDouble("ToolbarScale",1),.75f,2,1);
        p.predict=o.optBoolean("PenPrediction",false);p.light=o.optString("Theme").equals("Light");p.pressure=o.optBoolean("PressureSensitive",true);p.smooth=o.optBoolean("SmoothStrokes",false);p.whole=o.optString("EraserMode","Stroke").equals("Stroke");p.smart=o.optBoolean("SmartShapesEnabled",true);p.smartDelay=(int)bound(o.optDouble("SmartShapeDelayMs",300),200,800,300);p.hold=(int)bound(o.optDouble("HoldDelayMs",75),0,1200,75);p.onlyPen=o.optBoolean("OnlySPen",true);p.button=o.optBoolean("SPenButton",true);p.hoverErase=o.optBoolean("HoverErase",true);p.autoNavigate=o.optBoolean("ExperimentalAutoNavigate",android.os.Build.VERSION.SDK_INT>=34);p.fixedSizes="Presets".equals(o.optString("SizeControlMode","Slider"));p.showCursor=o.optBoolean("ShowPenCursor",true);
        String tool=o.optString("LastTool","pen");if(java.util.Arrays.asList("pen","highlighter","eraser","line","arrow","rectangle","ellipse","triangle","cube","cuboid","cylinder","prism","pyramid","cone","sphere","axes").contains(tool))p.tool=tool;
        p.x=(int)bound(o.optDouble("ToolbarLeft",18),0,10000,18);p.y=(int)bound(o.optDouble("ToolbarTop",85),0,10000,85);p.folded=o.optBoolean("ToolbarFolded",false);JSONArray a=o.optJSONArray("QuickColors");if(a!=null)for(int i=0;i<4;i++)p.quick[i]=col(a.optString(i),p.quick[i]);return p;
    }
    public static Prefs load(Context c){try{JSONObject stored=new JSONObject(c.getSharedPreferences("gvnpen",0).getString("settings","{}"));Prefs p=from(stored);int revision=stored.optInt("DefaultsVersion",0);if(revision<3)p.whole=true;if(revision<4&&p.hold==100)p.hold=75;if(revision<4&&(stored.optInt("SmartShapeDelayMs",500)==500||stored.optInt("SmartShapeDelayMs",500)==1000))p.smartDelay=300;if(revision<4)p.save(c);return p;}catch(Exception e){return new Prefs();}}
    public void save(Context c){c.getSharedPreferences("gvnpen",0).edit().putString("settings",json().toString()).apply();}
    public Prefs copy(){return from(json());}
    public int bg(){return light?0xffeff5f9:0xff18222e;}public int surface(){return light?0xffdce7f0:0xff2b3b4c;}public int text(){return light?0xff18222e:0xffffffff;}public int muted(){return light?0xff3e566a:0xffa9bccd;}
}
