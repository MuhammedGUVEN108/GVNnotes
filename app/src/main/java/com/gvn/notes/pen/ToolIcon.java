package com.gvn.notes.pen;
import android.content.Context;
import android.graphics.*;
import android.view.View;
import android.graphics.drawable.GradientDrawable;

/** Authored vector icons in the existing GVNpen tool vocabulary. */
public class ToolIcon extends View {
    String name;Prefs prefs;boolean selected=false,dimmed=false;int swatch=0;final Paint p=new Paint(3);
    // onDraw allocates nothing: line glyphs are built once into one path, shape previews are cached per text color.
    private final Path glyph=new Path(),work=new Path();private java.util.List<Geometry.Stroke> preview;private int previewColor;
    private GradientDrawable bg;private int bgColor,bgStroke;private boolean bgSelected;
    ToolIcon(Context c,String n,String label,Prefs f){super(c);name=n;prefs=f;setContentDescription(label);setTooltipText(label);setFocusable(true);setClickable(true);build();refresh();}
    void refresh(){int color=selected?(prefs.light?0xffb4deea:0xff1e5669):prefs.surface(),stroke=selected?prefs.accent:0;if(bg!=null&&color==bgColor&&stroke==bgStroke&&selected==bgSelected)return;bgColor=color;bgStroke=stroke;bgSelected=selected;
        bg=new GradientDrawable();bg.setColor(color);bg.setCornerRadius(7*getResources().getDisplayMetrics().density);if(selected)bg.setStroke((int)(2*getResources().getDisplayMetrics().density),stroke);setBackground(bg);invalidate();}
    /** Dimmed icons (nothing to undo/redo) draw their glyph at reduced opacity; the background is unchanged. */
    void setDimmed(boolean value){if(dimmed!=value){dimmed=value;invalidate();}}
    static void line(Path z,float...xy){z.moveTo(xy[0],xy[1]);for(int i=2;i<xy.length;i+=2)z.lineTo(xy[i],xy[i+1]);}
    private void build(){Path z=glyph;
        switch(name){
            case "eye":z.moveTo(2,12);z.quadTo(12,0,22,12);z.quadTo(12,24,2,12);break;
            case "cursor":line(z,5,2,5,20,10,15,14,22,18,20,14,13,21,12,5,2);break;
            case "pen":line(z,4,20,5,14,17,2,22,7,10,19,4,20);line(z,14,5,19,10);break;
            case "highlighter":line(z,3,17,7,12,12,17,8,21,3,21,3,17);line(z,7,12,16,2,22,8,12,17);line(z,3,23,20,23);break;
            case "eraser":line(z,2,14,12,3,22,12,13,22,9,22,2,14);line(z,7,9,17,18);line(z,13,22,23,22);break;
            case "line":line(z,3,20,21,4);break;
            case "arrow":line(z,3,20,21,4,11,5);line(z,21,4,20,14);break;
            case "triangle":line(z,12,3,22,21,2,21,12,3);break;
            case "undo":case "redo":line(z,9,5,3,11,9,16);z.moveTo(3,11);z.cubicTo(23,5,25,23,14,21);break;
            case "clear":line(z,3,6,21,6);line(z,7,6,8,22,17,22,18,6);line(z,9,6,9,2,15,2,15,6);line(z,11,10,11,18);line(z,15,10,15,18);break;
            case "settings":for(int i=0;i<8;i++){double a=i*Math.PI/4;line(z,12+8*(float)Math.cos(a),12+8*(float)Math.sin(a),12+11*(float)Math.cos(a),12+11*(float)Math.sin(a));}break;
            case "close":line(z,5,5,19,19);line(z,19,5,5,19);break;
        }
    }
    @Override protected void onDraw(Canvas c){super.onDraw(c);c.save();float scale=Math.min(getResources().getDisplayMetrics().density,Math.min(getWidth()/26f,getHeight()/26f));c.translate((getWidth()-24*scale)/2,(getHeight()-24*scale)/2);c.scale(scale,scale);int ink=prefs.text();p.setColor(dimmed?ink&0xffffff|0x66000000:ink);p.setStyle(Paint.Style.STROKE);p.setStrokeWidth(1.7f);p.setStrokeCap(Paint.Cap.ROUND);p.setStrokeJoin(Paint.Join.ROUND);
        switch(name){
            case "grip":for(int x=9;x<=15;x+=6)for(int y=5;y<=19;y+=7)c.drawCircle(x,y,.8f,p);break;
            case "eye":c.drawPath(glyph,p);c.drawCircle(12,12,3,p);break;
            case "cursor":case "pen":case "highlighter":case "eraser":case "line":case "arrow":case "triangle":case "undo":case "clear":case "close":c.drawPath(glyph,p);break;
            case "rectangle":c.drawRect(3,4,21,20,p);break;
            case "ellipse":c.drawOval(2,5,22,19,p);break;
            case "thickness":p.setStyle(Paint.Style.FILL);for(int i=0;i<3;i++)c.drawCircle(4+i*8,12,1.3f+i*1.1f,p);break;
            case "redo":c.scale(-1,1,12,12);c.drawPath(glyph,p);break;
            case "settings":c.drawPath(glyph,p);c.drawCircle(12,12,8,p);c.drawCircle(12,12,3,p);break;
            case "color":p.setStyle(Paint.Style.FILL);p.setColor(swatch);c.drawRoundRect(1,1,23,23,4,4,p);p.setStyle(Paint.Style.STROKE);p.setColor(ink);c.drawRoundRect(1,1,23,23,4,4,p);break;
            default:if(preview==null||previewColor!=ink){previewColor=ink;try{preview=Geometry.shape(name,new Geometry.Point(2,2),new Geometry.Point(22,22),ink,1.4f);}catch(Exception ignored){preview=java.util.Collections.emptyList();}}
                for(int i=0;i<preview.size();i++){Geometry.Stroke s=preview.get(i);PenRenderer.drawStroke(c,s,0,s.points.size(),p,false,work,null);}break;
        }c.restore();
    }
}
