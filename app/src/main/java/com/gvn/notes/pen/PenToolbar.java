package com.gvn.notes.pen;

import android.content.Context;
import android.graphics.drawable.GradientDrawable;
import android.os.Handler;
import android.os.Looper;
import android.view.*;
import android.widget.*;
import java.util.*;

/**
 * GVNpen araç şeridi (OverlayService.buildBar/showPopup) notun içinde: aynı sıra, simgeler, renkler, sürükleme,
 * 150 ms katlanma, kalem yaklaşınca 350 ms'de açılan ve 400 ms'de kapanan menüler, 120 ms soluklaşma/büyüme.
 * Sistem penceresi yerine not ekranının üstündeki bu şeffaf katmanda çalışır; boş alana dokunuşlar alttaki sayfaya geçer.
 */
public final class PenToolbar extends FrameLayout {
    /** Şeridin bağlı olduğu yazma yüzeyi. */
    public interface Host {
        void selectTool(String tool);
        void setDrawing(boolean drawing);
        boolean drawing();
        boolean temporary();
        void undo(); void redo(); boolean canUndo(); boolean canRedo();
        void clear();
        void openSettings();
        void close();
        /** Renk/kalınlık değişti (ayarları kaydet, çizimi tazele). */
        void prefsChanged();
    }
    final Host host;Prefs prefs;
    LinearLayout bar;ScrollView barScroll;View popup;final HashMap<String,ToolIcon> icons=new HashMap<>();
    boolean folded=false,beforeFold=true;private final Handler handler=new Handler(Looper.getMainLooper());
    float renderScale=1;
    // Kalem yaklaşmasıyla açılan menü, kalem 400 ms boyunca ne menüde ne düğmesinde değilse kendiliğinden kapanır.
    boolean popupByHover,popupHovered,popupTouched;View popupAnchor;
    final Runnable hoverClose=()->{if(popup!=null&&popupByHover&&!popupHovered&&!popupTouched&&(popupAnchor==null||!popupAnchor.isHovered()&&!popupAnchor.isPressed()))closePopup();};
    // Katlama/açma: şerit animasyon sırasında yüksekliğini barClip olarak bildirir.
    int barClip=-1;android.animation.ValueAnimator barAnim;
    int dragX,dragY;boolean dragPending;final Choreographer.FrameCallback dragFrame=t->{dragPending=false;applyDrag();};
    int barX,barY,barW,barH;

    public PenToolbar(Context c,Host host,Prefs prefs){super(c);this.host=host;this.prefs=prefs;folded=prefs.folded;setClipChildren(false);}
    int dp(float n){return Math.round(n*getResources().getDisplayMetrics().density);}
    public void setPrefs(Prefs p){prefs=p;folded=p.folded;if(getWidth()>0)buildBar();}

    @Override protected void onSizeChanged(int w,int h,int ow,int oh){super.onSizeChanged(w,h,ow,oh);if(w>dp(80)&&h>dp(120))post(this::buildBar);}
    /** Boş alana dokunuş alttaki sayfaya geçer; açık menü varsa dışarıya dokunuş menüyü kapatır (GVNpen WATCH_OUTSIDE). */
    @Override public boolean dispatchTouchEvent(MotionEvent e){
        if(e.getActionMasked()==MotionEvent.ACTION_DOWN&&popup!=null&&!inside(popup,e)&&!inside(barScroll,e))closePopup();
        return super.dispatchTouchEvent(e);
    }
    private static boolean inside(View v,MotionEvent e){if(v==null||v.getVisibility()!=VISIBLE)return false;float x=e.getX()-v.getX(),y=e.getY()-v.getY();return x>=0&&y>=0&&x<v.getWidth()&&y<v.getHeight();}

    public void buildBar(){closePopup();if(barAnim!=null){barAnim.removeAllListeners();barAnim.removeAllUpdateListeners();barAnim.cancel();barAnim=null;}barClip=-1;if(dragPending){Choreographer.getInstance().removeFrameCallback(dragFrame);dragPending=false;}
        if(barScroll!=null)removeView(barScroll);
        renderScale=Math.min(prefs.scale,Math.max(.45f,getHeight()/(float)dp(820)));icons.clear();
        bar=new LinearLayout(getContext()){@Override protected void onMeasure(int w,int h){super.onMeasure(w,h);if(barClip>=0)setMeasuredDimension(getMeasuredWidth(),barClip);}};bar.setOrientation(LinearLayout.VERTICAL);bar.setPadding(dp(5*renderScale),dp(4*renderScale),dp(5*renderScale),dp(6*renderScale));GradientDrawable bg=new GradientDrawable();bg.setColor(prefs.bg());bg.setCornerRadius(dp(12));bar.setBackground(bg);barScroll=new ScrollView(getContext());barScroll.setFillViewport(false);barScroll.setVerticalScrollBarEnabled(false);barScroll.addView(bar);
        ToolIcon grip=add("grip","Araç çubuğunu sürükle",()->{});grip.setOnTouchListener(new View.OnTouchListener(){float sx,sy;int x,y;public boolean onTouch(View v,MotionEvent e){switch(e.getActionMasked()){case 0:sx=e.getRawX();sy=e.getRawY();x=barX;y=barY;closePopup();return true;case 2:dragX=x+Math.round(e.getRawX()-sx);dragY=y+Math.round(e.getRawY()-sy);if(!dragPending){dragPending=true;Choreographer.getInstance().postFrameCallback(dragFrame);}return true;case 3:flushDrag();return true;case 1:flushDrag();prefs.x=barX;prefs.y=barY;host.prefsChanged();v.performClick();return true;}return true;}});
        // Not uygulamasında göz: şeridi katlar ve okuma (gezinme) moduna geçer; yazılar görünür kalır.
        add("eye",folded?"Araç çubuğunu aç":"Araç çubuğunu katla",()->{closePopup();if(!folded){beforeFold=host.drawing();host.setDrawing(false);folded=true;}else{folded=false;host.setDrawing(beforeFold);}prefs.folded=folded;host.prefsChanged();foldBar();refreshSelection();});
        {add("cursor","Gezinme · sayfayı kalemle kaydır",()->{host.setDrawing(false);refreshSelection();});separator();
            for(String[] item:new String[][]{{"pen","Kalem"},{"highlighter","Fosforlu"},{"eraser","Silgi"},{"line","2B şekiller"},{"cube","3B cisimler"},{"thickness","Mürekkep kalınlığı"}}){String key=item[0];ToolIcon b=add(key,item[1],()->{if(key.equals("thickness"))showWidths(bAnchor(key),false);else if(key.equals("line")||key.equals("cube")){select(key);showShapes(bAnchor(key),key.equals("cube"));}else select(key);});b.setOnLongClickListener(v->{if(key.equals("line")||key.equals("cube"))showShapes(v,key.equals("cube"));else showWidths(v,key.equals("eraser"));return true;});b.setOnHoverListener((v,e)->{int a=e.getActionMasked();if(a==MotionEvent.ACTION_HOVER_ENTER){if(v==popupAnchor)handler.removeCallbacks(hoverClose);handler.postDelayed(()->{if(v.isHovered()&&(popup==null||popupByHover&&popupAnchor!=v)){if(key.equals("line")||key.equals("cube"))showShapes(v,key.equals("cube"));else showWidths(v,key.equals("eraser"));if(popup!=null){popupByHover=true;popupAnchor=v;}}},350);}else if(a==MotionEvent.ACTION_HOVER_EXIT&&v==popupAnchor)scheduleHoverClose();return false;});}
            separator();add("undo","Geri al",()->{if(!host.canUndo())return;host.undo();refreshHistory();});add("redo","Yinele",()->{if(!host.canRedo())return;host.redo();refreshHistory();});add("clear","Sayfayı temizle · geri alınabilir",()->{host.clear();refreshHistory();});separator();
            add("settings","Ayarlar",host::openSettings);add("close","Notu kapat",host::close);
            LinearLayout colors=new LinearLayout(getContext());colors.setOrientation(LinearLayout.VERTICAL);for(int row=0;row<2;row++){LinearLayout rr=new LinearLayout(getContext());for(int col=0;col<2;col++){final int index=row*2+col;ToolIcon b=new ToolIcon(getContext(),"color","Hızlı renk "+Prefs.hex(prefs.quick[index]),prefs);b.swatch=prefs.quick[index];b.setOnClickListener(v->applyColor(prefs.quick[index]));rr.addView(b,new LinearLayout.LayoutParams(0,dp(30*renderScale),1));}colors.addView(rr);}bar.addView(colors);ToolIcon color=add("color","Renk paletini aç",()->showColors(bAnchor("color")));color.swatch=prefs.color;
            ImageView logo=new ImageView(getContext());logo.setImageResource(com.gvn.notes.R.drawable.brand);logo.setContentDescription("GVNpen logosu");LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(dp(24*renderScale),dp(24*renderScale));lp.gravity=Gravity.CENTER;lp.topMargin=dp(7);bar.addView(logo,lp);TextView brand=new TextView(getContext());brand.setText("GVNpen");brand.setTextColor(prefs.text());brand.setTextSize(10*renderScale);brand.setSingleLine(true);// En büyük boyut en küçükten büyük olmalı (eşitse Android hata verir; kısa not alanında ölçek düşebilir).
            brand.setAutoSizeTextTypeUniformWithConfiguration(6,Math.max(7,(int)(10*renderScale)),1,android.util.TypedValue.COMPLEX_UNIT_SP);brand.setGravity(Gravity.CENTER);bar.addView(brand);
        }
        if(folded)foldedContent(true);
        bar.measure(View.MeasureSpec.makeMeasureSpec(dp(58*renderScale),View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        barW=dp(58*renderScale);barH=Math.min(getHeight()-dp(12),bar.getMeasuredHeight());barX=prefs.x;barY=prefs.y;barScroll.setElevation(dp(6));
        addView(barScroll,new LayoutParams(barW,barH));clampBar();place();refreshSelection();refreshHistory();
    }
    void foldedContent(boolean hide){for(int i=2;i<bar.getChildCount();i++)bar.getChildAt(i).setVisibility(hide?View.GONE:View.VISIBLE);}
    /** Şeridi yerinde katlar/açar: yükseklik 150 ms'de değişir. */
    void foldBar(){
        if(bar==null||barScroll==null)return;int from=barClip>=0?barClip:barScroll.getHeight()>0?Math.min(barH,barScroll.getHeight()):barH;
        if(barAnim!=null){barAnim.removeAllListeners();barAnim.removeAllUpdateListeners();barAnim.cancel();barAnim=null;}
        ToolIcon eye=icons.get("eye");if(eye!=null){String label=folded?"Araç çubuğunu aç":"Araç çubuğunu katla";eye.setContentDescription(label);eye.setTooltipText(label);}
        final boolean fold=folded;barScroll.scrollTo(0,0);foldedContent(fold);barClip=-1;bar.measure(View.MeasureSpec.makeMeasureSpec(barW,View.MeasureSpec.EXACTLY),View.MeasureSpec.makeMeasureSpec(0,View.MeasureSpec.UNSPECIFIED));
        final int to=Math.min(getHeight()-dp(12),bar.getMeasuredHeight());if(fold)foldedContent(false);
        if(!fold)placeBar(to);
        if(from<=0||from==to){barClip=-1;if(fold){foldedContent(true);placeBar(to);}bar.requestLayout();return;}
        barClip=from;bar.requestLayout();android.animation.ValueAnimator anim=android.animation.ValueAnimator.ofInt(from,to);anim.setDuration(150);anim.setInterpolator(new android.view.animation.DecelerateInterpolator());
        anim.addUpdateListener(a->{barClip=(Integer)a.getAnimatedValue();bar.requestLayout();});
        anim.addListener(new android.animation.AnimatorListenerAdapter(){@Override public void onAnimationEnd(android.animation.Animator a){if(barAnim!=a)return;barAnim=null;barClip=-1;if(fold){foldedContent(true);placeBar(to);}bar.requestLayout();}});
        barAnim=anim;anim.start();
    }
    void placeBar(int height){barH=height;barX=prefs.x;barY=prefs.y;clampBar();place();}
    void place(){if(barScroll==null)return;LayoutParams lp=(LayoutParams)barScroll.getLayoutParams();if(lp.height!=barH||lp.width!=barW){lp.width=barW;lp.height=barH;barScroll.setLayoutParams(lp);}barScroll.setTranslationX(barX);barScroll.setTranslationY(barY);}
    void applyDrag(){if(barScroll==null)return;barX=dragX;barY=dragY;clampBar();place();}
    void flushDrag(){if(dragPending){Choreographer.getInstance().removeFrameCallback(dragFrame);dragPending=false;applyDrag();}}
    void clampBar(){int m=dp(6);barX=Math.max(m,Math.min(barX,getWidth()-barW-m));barY=Math.max(m,Math.min(barY,getHeight()-barH-m));}
    public void refreshHistory(){ToolIcon undo=icons.get("undo"),redo=icons.get("redo");if(undo!=null)undo.setDimmed(!host.canUndo());if(redo!=null)redo.setDimmed(!host.canRedo());}
    void scheduleHoverClose(){if(popup!=null&&popupByHover){handler.removeCallbacks(hoverClose);handler.postDelayed(hoverClose,400);}}
    void popupHover(View root,int action){if(root!=popup)return;if(action==MotionEvent.ACTION_HOVER_EXIT){popupHovered=false;scheduleHoverClose();}else if(action==MotionEvent.ACTION_HOVER_ENTER||action==MotionEvent.ACTION_HOVER_MOVE){popupHovered=true;handler.removeCallbacks(hoverClose);}}
    void popupTouch(View root,int action){if(root!=popup)return;if(action==MotionEvent.ACTION_DOWN){popupTouched=true;handler.removeCallbacks(hoverClose);}else if(action==MotionEvent.ACTION_UP||action==MotionEvent.ACTION_CANCEL){popupTouched=false;scheduleHoverClose();}}
    View bAnchor(String name){return icons.get(name);}
    ToolIcon add(String name,String label,Runnable action){ToolIcon b=new ToolIcon(getContext(),name,label,prefs);b.setOnClickListener(v->{closePopup();action.run();});LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp((name.equals("grip")?24:36)*renderScale));lp.topMargin=dp(2);lp.bottomMargin=dp(2);bar.addView(b,lp);icons.put(name,b);return b;}
    void separator(){View v=new View(getContext());v.setBackgroundColor(prefs.light?0xffb4c7d7:0xff394b5c);LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(1));lp.setMargins(dp(6),dp(5),dp(6),dp(5));bar.addView(v,lp);}
    void applyColor(int color){prefs.color=color;host.prefsChanged();ToolIcon swatch=icons.get("color");if(swatch!=null){swatch.swatch=color;swatch.invalidate();}closePopup();}
    void select(String tool){prefs.tool=tool;host.selectTool(tool);refreshSelection();}
    public void refreshSelection(){boolean drawing=host.drawing();for(Map.Entry<String,ToolIcon> e:icons.entrySet()){String key=e.getKey(),tool=host.temporary()?"eraser":prefs.tool;e.getValue().selected=key.equals("cursor")?!drawing:drawing&&(key.equals(tool)||key.equals("line")&&Arrays.asList("arrow","rectangle","ellipse","triangle").contains(tool)||key.equals("cube")&&Arrays.asList("cuboid","cylinder","prism","pyramid","cone","sphere","axes").contains(tool));e.getValue().refresh();}}
    private String sizeKind(View anchor,boolean eraser){if(eraser)return "eraser";String key=anchor instanceof ToolIcon?((ToolIcon)anchor).name:"thickness";if(key.equals("pen")||key.equals("highlighter"))return key;return prefs.tool.equals("pen")?"pen":prefs.tool.equals("highlighter")?"highlighter":prefs.tool.equals("eraser")?"eraser":"shape";}
    private int currentSize(String kind){return SizeScale.progress(kind,kind.equals("eraser")?prefs.eraser:kind.equals("highlighter")?prefs.marker:kind.equals("shape")?prefs.shape:prefs.pen);}
    private void changeSize(String kind,int value){float width=SizeScale.width(kind,value);if(kind.equals("eraser"))prefs.eraser=Math.max(1,width);else if(kind.equals("highlighter"))prefs.marker=Math.max(1,width);else if(kind.equals("shape"))prefs.shape=width;else prefs.pen=width;}
    private void showWidths(View anchor,boolean eraser){showPopup(anchor,sizeControls(sizeKind(anchor,eraser)));}
    private LinearLayout sizeControls(final String kind){LinearLayout panel=new LinearLayout(getContext());panel.setOrientation(LinearLayout.VERTICAL);panel.setPadding(dp(6),dp(6),dp(6),dp(6));
        TextView title=new TextView(getContext());title.setText(kind.equals("eraser")?"Silgi boyutu":kind.equals("shape")?"Şekil çizgi kalınlığı":kind.equals("highlighter")?"Fosforlu kalınlığı":"Kalem kalınlığı");title.setTextColor(prefs.text());title.setTextSize(14);if(!kind.equals("shape"))panel.addView(title);
        TextView value=new TextView(getContext());value.setText(currentSize(kind)+" / 100");value.setTextColor(prefs.light?0xff11758b:0xff4fc4dd);value.setTextSize(14);value.setPadding(0,dp(8),0,dp(4));LinearLayout controls=new LinearLayout(getContext());controls.setGravity(Gravity.CENTER_VERTICAL);panel.addView(controls);
        if(!prefs.fixedSizes){SeekBar slider=new SeekBar(getContext());slider.setMax(100);slider.setProgress(currentSize(kind));slider.setKeyProgressIncrement(1);slider.setContentDescription(title.getText());slider.setProgressTintList(android.content.res.ColorStateList.valueOf(0xff4fc4dd));slider.setThumbTintList(android.content.res.ColorStateList.valueOf(0xff4fc4dd));controls.addView(slider,new LinearLayout.LayoutParams(dp(178),dp(48)));controls.addView(value,new LinearLayout.LayoutParams(dp(62),dp(48)));value.setGravity(Gravity.CENTER);slider.setOnSeekBarChangeListener(new SeekBar.OnSeekBarChangeListener(){public void onProgressChanged(SeekBar b,int n,boolean user){if(user){changeSize(kind,n);value.setText(n+" / 100");}}public void onStartTrackingTouch(SeekBar b){}public void onStopTrackingTouch(SeekBar b){host.prefsChanged();}});}
        else{GridLayout grid=new GridLayout(getContext());grid.setColumnCount(4);for(int n:UiKit.SIZES){Button button=popupButton(String.valueOf(n),()->{changeSize(kind,n);host.prefsChanged();closePopup();});GridLayout.LayoutParams lp=new GridLayout.LayoutParams();lp.width=dp(56);lp.height=dp(52);grid.addView(button,lp);}panel.addView(grid);}
        return panel;
    }
    private void showShapes(View anchor,boolean solid){String[] keys=solid?new String[]{"cube","cuboid","cylinder","prism","pyramid","cone","sphere","axes"}:new String[]{"line","arrow","rectangle","ellipse","triangle"};String[] labels=solid?new String[]{"Küp","Dikdörtgen prizma","Silindir","Üçgen prizma","Kare piramit","Koni","Küre","Koordinat eksenleri"}:new String[]{"Çizgi","Ok","Dikdörtgen","Elips","Üçgen"};LinearLayout outer=new LinearLayout(getContext());outer.setOrientation(LinearLayout.VERTICAL);for(int i=0;i<keys.length;i+=solid?4:5){LinearLayout row=popupRow();for(int j=i;j<Math.min(i+(solid?4:5),keys.length);j++){String key=keys[j];ToolIcon b=new ToolIcon(getContext(),key,labels[j],prefs);b.setOnClickListener(v->{closePopup();select(key);});LinearLayout.LayoutParams cell=new LinearLayout.LayoutParams(0,dp(52),1);cell.setMargins(dp(2),0,dp(2),0);row.addView(b,cell);}outer.addView(row,new LinearLayout.LayoutParams(dp(solid?252:264),dp(56)));}outer.addView(sizeControls("shape"));showPopup(anchor,outer);}
    private void showColors(View anchor){LinearLayout outer=new LinearLayout(getContext());outer.setOrientation(LinearLayout.VERTICAL);int[] colors={0xff00d9f5,0xff3b82f6,0xffa78bfa,0xffef4444,0xfff59e0b,0xfffacc15,0xff22c55e,0xffff00ff,0xffffffff,0xff000000};for(int i=0;i<2;i++){LinearLayout row=popupRow();for(int j=0;j<5;j++){int color=colors[i*5+j];ToolIcon b=new ToolIcon(getContext(),"color",Prefs.hex(color),prefs);b.swatch=color;b.setOnClickListener(v->applyColor(color));row.addView(b,new LinearLayout.LayoutParams(dp(48),dp(48)));}outer.addView(row);}showPopup(anchor,outer);}
    LinearLayout popupRow(){LinearLayout row=new LinearLayout(getContext());row.setPadding(dp(2),dp(2),dp(2),dp(2));return row;}
    Button popupButton(String text,Runnable action){Button b=new Button(getContext());b.setText(text);b.setTextColor(prefs.text());b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(prefs.surface()));b.setMinWidth(dp(48));b.setMinimumWidth(dp(48));b.setPadding(0,0,0,0);b.setOnClickListener(v->action.run());return b;}
    void showPopup(View anchor,View content){closePopup();if(anchor==null)return;
        LinearLayout root=new LinearLayout(getContext()){@Override protected boolean dispatchHoverEvent(MotionEvent e){popupHover(this,e.getActionMasked());return super.dispatchHoverEvent(e);}@Override public boolean dispatchTouchEvent(MotionEvent e){popupTouch(this,e.getActionMasked());return super.dispatchTouchEvent(e);}};root.setOrientation(LinearLayout.VERTICAL);GradientDrawable bg=new GradientDrawable();bg.setColor(prefs.bg());bg.setCornerRadius(dp(10));root.setBackground(bg);root.setPadding(dp(5),dp(5),dp(5),dp(5));root.addView(content);Button done=popupButton("Kapat",this::closePopup);root.addView(done,new LinearLayout.LayoutParams(-1,dp(48)));root.setElevation(dp(8));
        root.measure(View.MeasureSpec.makeMeasureSpec(getWidth()-dp(20),View.MeasureSpec.AT_MOST),View.MeasureSpec.makeMeasureSpec(getHeight()-dp(20),View.MeasureSpec.AT_MOST));
        int[] pos=new int[2],self=new int[2];anchor.getLocationOnScreen(pos);getLocationOnScreen(self);int anchorY=pos[1]-self[1];int w=root.getMeasuredWidth(),h=root.getMeasuredHeight();
        int right=barX+barW+dp(6);boolean onRight=right+w<getWidth();int x=onRight?right:Math.max(0,barX-w-dp(6));int y=Math.max(dp(10),Math.min(anchorY,getHeight()-h-dp(10)));
        popup=root;addView(root,new LayoutParams(w,h));root.setTranslationX(x);root.setTranslationY(y);
        // 120 ms soluklaşma + şeride bakan kenardan hafif büyüme, düğme hizasında.
        root.setPivotX(onRight?0:w);root.setPivotY(Math.max(0,Math.min(h,anchorY+anchor.getHeight()/2-y)));root.setAlpha(0);root.setScaleX(.94f);root.setScaleY(.94f);
        root.animate().alpha(1).scaleX(1).scaleY(1).setDuration(120).setInterpolator(new android.view.animation.DecelerateInterpolator());}
    /** Menü hemen ayrılır (popup==null), 120 ms'de solarak kaldırılır. */
    public void closePopup(){handler.removeCallbacks(hoverClose);popupByHover=false;popupHovered=false;popupTouched=false;popupAnchor=null;if(popup==null)return;final View dying=popup;popup=null;dying.setEnabled(false);
        Runnable remove=()->{if(dying.getParent()==this)removeView(dying);};
        dying.animate().cancel();dying.animate().alpha(0).scaleX(.94f).scaleY(.94f).setDuration(120).setInterpolator(new android.view.animation.AccelerateInterpolator()).withEndAction(remove);handler.postDelayed(remove,400);}
    @Override protected void onDetachedFromWindow(){handler.removeCallbacksAndMessages(null);if(barAnim!=null){barAnim.removeAllListeners();barAnim.cancel();}Choreographer.getInstance().removeFrameCallback(dragFrame);super.onDetachedFromWindow();}
}
