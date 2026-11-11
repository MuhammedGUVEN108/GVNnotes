package com.gvn.notes.pen;
import android.content.*;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.graphics.drawable.*;
import android.view.*;
import android.widget.*;

final class UiKit {
    static final int[] SIZES={1,4,8,16,32,64,100};
    static int dp(Context c,float n){return Math.round(n*c.getResources().getDisplayMetrics().density);}
    static TextView text(Context c,Prefs p,String value,int size,boolean bold){TextView t=new TextView(c);t.setText(value);t.setTextSize(size);t.setTextColor(p.text());t.setLineSpacing(dp(c,3),1);if(bold)t.setTypeface(null,Typeface.BOLD);return t;}
    static Button button(Context c,Prefs p,String label,boolean primary,Runnable action){Button b=new Button(c);b.setText(label);b.setAllCaps(false);b.setTextSize(15);b.setMinHeight(dp(c,52));b.setPadding(dp(c,16),dp(c,10),dp(c,16),dp(c,10));b.setTextColor(primary?0xff0e1722:p.text());GradientDrawable shape=new GradientDrawable();shape.setColor(primary?0xff4fc4dd:p.surface());shape.setCornerRadius(dp(c,9));b.setBackground(new RippleDrawable(ColorStateList.valueOf(p.light?0x22000000:0x33ffffff),shape,null));b.setOnClickListener(v->action.run());return b;}
    static void gap(LinearLayout parent,int n){View v=new View(parent.getContext());parent.addView(v,new LinearLayout.LayoutParams(1,dp(parent.getContext(),n)));}
    static void paragraph(LinearLayout parent,Prefs p,String value){TextView t=text(parent.getContext(),p,value,15,false);t.setTextColor(p.muted());parent.addView(t);gap(parent,14);}
}
