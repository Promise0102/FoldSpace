package com.mkf.foldspace;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.*;
import android.text.*;
import android.view.*;
import android.view.inputmethod.InputMethodManager;
import android.widget.*;
import java.util.*;

/** Original native interface, shared by the activity and the posture overlay. */
public final class SpacePanel extends FrameLayout {
    public interface Actions { void launch(AppCatalog.App app); void settings(); void close(); }
    private final Actions actions;
    private final LinearLayout top, bottom, cards, chips;
    private final TextView status, count, hint;
    private final GridLayout grid;
    private final HorizontalScrollView carousel;
    private final LinearLayout header;
    private final HorizontalScrollView chipScroll;
    private final FrameLayout playerControls;
    private AppHostView host;
    private final EditText search;
    private final ArrayList<AppCatalog.App> apps = new ArrayList<>();
    private String category = "视频";
    private int lastWidth, lastHeight, insetTop, insetBottom;
    private boolean disposed,catalogLoaded;
    private final int white = Color.rgb(244,247,255), muted = Color.rgb(181,195,219);

    public SpacePanel(Context context, Actions actions, boolean overlay) {
        super(context); this.actions = actions;
        setBackground(new Atmosphere()); setFocusableInTouchMode(true);
        top = column(); bottom = column();
        addView(top, new FrameLayout.LayoutParams(-1, -2)); addView(bottom, new FrameLayout.LayoutParams(-1,-2));
        playerControls=new FrameLayout(context);playerControls.setVisibility(GONE);addView(playerControls,new FrameLayout.LayoutParams(-1,-2));
        header = row(); header.setGravity(Gravity.CENTER_VERTICAL);
        LinearLayout titles = column();
        TextView brand = text("折境空间",21,white); brand.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        titles.addView(brand); status = text("正在读取铰链角度…",11,muted); titles.addView(status);
        header.addView(titles,new LinearLayout.LayoutParams(0,-2,1));
        TextView settings = button("设置",13); settings.setContentDescription("自动悬停设置"); settings.setOnClickListener(v -> actions.settings());
        header.addView(settings, new LinearLayout.LayoutParams(dp(64),dp(38)));
        TextView close = button("×",25); close.setContentDescription(overlay ? "收起，等待下次半展开" : "关闭界面"); close.setOnClickListener(v -> actions.close());
        LinearLayout.LayoutParams closeParams = new LinearLayout.LayoutParams(dp(38),dp(38)); closeParams.leftMargin=dp(8); header.addView(close,closeParams);
        top.addView(header,new LinearLayout.LayoutParams(-1,dp(58)));
        carousel = new HorizontalScrollView(context); carousel.setHorizontalScrollBarEnabled(false); carousel.setClipToPadding(false);
        cards = row(); carousel.addView(cards,new HorizontalScrollView.LayoutParams(-2,-1));
        LinearLayout.LayoutParams heroParams = new LinearLayout.LayoutParams(-1,0,1); heroParams.topMargin=dp(8); top.addView(carousel,heroParams);
        hint = text("常用应用  ·  左右滑动",10,muted); hint.setGravity(Gravity.CENTER);
        top.addView(hint,new LinearLayout.LayoutParams(-1,dp(26)));
        chipScroll = new HorizontalScrollView(context); chipScroll.setHorizontalScrollBarEnabled(false);
        chips = row(); chips.setGravity(Gravity.CENTER_VERTICAL); chipScroll.addView(chips);
        top.addView(chipScroll,new LinearLayout.LayoutParams(-1,dp(43))); refreshChips();

        LinearLayout directoryHeader = row(); directoryHeader.setGravity(Gravity.CENTER_VERTICAL);
        TextView title=text("视频应用",19,white); title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        directoryHeader.addView(title,new LinearLayout.LayoutParams(0,-2,1));
        count=text("读取中",11,muted); directoryHeader.addView(count);
        bottom.addView(directoryHeader,new LinearLayout.LayoutParams(-1,dp(36)));
        search=new EditText(context); search.setSingleLine(true); search.setTextSize(13); search.setTextColor(white); search.setHintTextColor(muted);
        search.setHint("搜索视频应用"); search.setPadding(dp(16),0,dp(16),0); search.setBackground(glass(17,0x224B618C));
        search.setImeOptions(android.view.inputmethod.EditorInfo.IME_ACTION_DONE);
        search.setOnEditorActionListener((v,action,event) -> { ((InputMethodManager)context.getSystemService(Context.INPUT_METHOD_SERVICE)).hideSoftInputFromWindow(search.getWindowToken(),0); search.clearFocus(); return true; });
        bottom.addView(search,new LinearLayout.LayoutParams(-1,dp(40)));
        ScrollView scroll=new ScrollView(context); scroll.setVerticalScrollBarEnabled(false); scroll.setClipToPadding(false); scroll.setPadding(0,dp(10),0,0);
        grid=new GridLayout(context); grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS); scroll.addView(grid,new ScrollView.LayoutParams(-1,-2));
        LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,0,1); scrollParams.topMargin=dp(3); bottom.addView(scroll,scrollParams);
        search.addTextChangedListener(new TextWatcher() {
            public void beforeTextChanged(CharSequence s,int start,int count,int after) {}
            public void onTextChanged(CharSequence s,int start,int before,int count) { refreshGrid(); }
            public void afterTextChanged(Editable e) {}
        });
        AppCatalog.load(context, result -> { if (disposed) return;catalogLoaded=true; apps.clear();for(AppCatalog.App app:result)if(app.category.equals("视频"))apps.add(app); refreshCards(); refreshGrid(); });
        setOnApplyWindowInsetsListener((v,insets) -> {
            Insets bars=insets.getInsets(WindowInsets.Type.displayCutout());
            insetTop=bars.top; insetBottom=bars.bottom; arrange(getWidth(),getHeight()); return insets;
        });
    }
    public void dispose() { disposed=true;top.animate().cancel();if(host!=null){host.dispose();host=null;} }
    public void status(float angle, boolean hover, boolean available) {
        String angleText = Float.isNaN(angle) ? "等待角度" : Math.round(angle)+"°";
        String enabled = AppCatalog.prefs(getContext()).getBoolean("auto",false) ? "自动悬停已开启" : "自动悬停未开启 · 点设置";
        status.setText(available ? angleText+"  ·  "+(hover?"半展开":"折叠角度监听")+"  ·  "+enabled : "未找到可用铰链传感器 · 可手动使用目录");
    }
    public void refreshRecents() { refreshCards(); if(category.equals("最近")) refreshGrid(); }
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh) { super.onSizeChanged(w,h,oldw,oldh); arrange(w,h); }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(()->ImmersiveMode.apply(this));}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused)post(()->ImmersiveMode.apply(this));}
    private void arrange(int w,int h) {
        if(w<=0 || h<=0) return;
        int margin=dp(w/getResources().getDisplayMetrics().density>700?32:22);
        int split=h/2;
        FrameLayout.LayoutParams tp=(FrameLayout.LayoutParams)top.getLayoutParams();
        tp.width=host==null?w-2*margin:w; tp.height=host==null?Math.max(dp(190),split-insetTop-dp(8)):split; tp.leftMargin=host==null?margin:0; tp.topMargin=host==null?insetTop+dp(4):0; top.setLayoutParams(tp);
        FrameLayout.LayoutParams bp=(FrameLayout.LayoutParams)bottom.getLayoutParams();
        bp.width=w-2*margin; bp.height=Math.max(dp(100),h-split-insetBottom-dp(18)); bp.leftMargin=margin; bp.topMargin=split+dp(10); bottom.setLayoutParams(bp);
        FrameLayout.LayoutParams pp=new FrameLayout.LayoutParams(bp.width,bp.height);pp.leftMargin=bp.leftMargin;pp.topMargin=bp.topMargin;playerControls.setLayoutParams(pp);
        if(lastWidth!=w || lastHeight!=h) { lastWidth=w; lastHeight=h; post(() -> { refreshCards(); refreshGrid(); }); }
    }
    private void refreshChips() {
        chips.removeAllViews();
        for(String name:new String[]{"视频","最近"}) {
            TextView chip=button(name.equals("视频")?"全部视频":"最近使用",13); boolean selected=name.equals(category);
            chip.setTextColor(selected?white:muted); chip.setBackground(glass(22,selected?0x667E95BF:0x244F638D));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(92),dp(36)); p.rightMargin=dp(8); chips.addView(chip,p);
            chip.setOnClickListener(v -> {category=name; refreshChips(); refreshGrid();});
        }
    }
    private void refreshCards() {
        cards.removeAllViews(); int width=Math.max(dp(240),getWidth()-dp(68));
        for(AppCatalog.App app:AppCatalog.featured(getContext(),apps)) {
            HeroCard card=new HeroCard(getContext(),app); card.setContentDescription("打开"+app.label); card.setClickable(true);
            card.setOnClickListener(v -> open(app)); LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,-1); p.rightMargin=dp(12); cards.addView(card,p);
        }
        if(apps.isEmpty()) { TextView empty=text(catalogLoaded?"未找到已安装的视频应用":"正在读取视频应用",18,white); empty.setGravity(Gravity.CENTER); cards.addView(empty,new LinearLayout.LayoutParams(width,-1)); }
    }
    private void refreshGrid() {
        if(grid==null || search==null) return;
        grid.removeAllViews(); int columns=Math.max(3,Math.min(7,(getWidth()-dp(44))/dp(90))); grid.setColumnCount(columns);
        String query=search.getText().toString().trim().toLowerCase(Locale.ROOT);
        ArrayList<AppCatalog.App> matches=new ArrayList<>();
        for(AppCatalog.App app:apps) {
            if(category.equals("最近") && AppCatalog.lastUsed(getContext(),app)==0) continue;
            if(!category.equals("全部") && !category.equals("最近") && !category.equals(app.category)) continue;
            if(!query.isEmpty() && !app.label.toLowerCase(Locale.ROOT).contains(query) && !app.packageName.toLowerCase(Locale.ROOT).contains(query)) continue;
            matches.add(app);
        }
        if(category.equals("最近")) Collections.sort(matches,(a,b)->Long.compare(AppCatalog.lastUsed(getContext(),b),AppCatalog.lastUsed(getContext(),a)));
        count.setText(matches.size()+" 个视频应用");
        if(matches.isEmpty()) {
            TextView empty=text(category.equals("最近")?"从这里打开的应用会出现在最近使用中":"没有匹配的应用",13,muted); empty.setGravity(Gravity.CENTER); empty.setPadding(0,dp(30),0,dp(30));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(); p.columnSpec=GridLayout.spec(0,columns,1f); p.width=0; grid.addView(empty,p); return;
        }
        for(AppCatalog.App app:matches) {
            LinearLayout tile=column(); tile.setGravity(Gravity.CENTER); tile.setPadding(dp(4),dp(9),dp(4),dp(7)); tile.setBackground(ripple());
            ImageView icon=new ImageView(getContext()); icon.setImageBitmap(app.icon); tile.addView(icon,new LinearLayout.LayoutParams(dp(43),dp(43)));
            TextView label=text(app.label,11,white); label.setMaxLines(1); label.setEllipsize(TextUtils.TruncateAt.END); label.setGravity(Gravity.CENTER);
            LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2); lp.topMargin=dp(7); tile.addView(label,lp);
            tile.setContentDescription("打开"+app.label); tile.setOnClickListener(v -> open(app));
            GridLayout.LayoutParams p=new GridLayout.LayoutParams(); p.width=0; p.height=dp(88); p.columnSpec=GridLayout.spec(GridLayout.UNDEFINED,1f); grid.addView(tile,p);
        }
    }
    private void open(AppCatalog.App app) {
        if(!app.category.equals("视频"))return;openHost(app);
    }
    public void openAutomatically(AppCatalog.App app){openHost(app);}
    private void openHost(AppCatalog.App app){
        if(disposed)return;if(host!=null){top.removeView(host);host.dispose();}
        host=new AppHostView(getContext(),app,()->actions.launch(app));
        header.setVisibility(GONE);carousel.setVisibility(GONE);hint.setVisibility(GONE);chipScroll.setVisibility(GONE);
        top.addView(host,0,new LinearLayout.LayoutParams(-1,0,1));
        playerControls.removeAllViews();
        if(app.category.equals("视频")){bottom.setVisibility(GONE);playerControls.setVisibility(VISIBLE);playerControls.addView(host.controls(this::directory),new FrameLayout.LayoutParams(-1,-1));}
        else{bottom.setVisibility(VISIBLE);playerControls.setVisibility(GONE);}
        arrange(getWidth(),getHeight());
        animateTop();
    }
    private void directory(){
        if(host!=null){top.removeView(host);host.dispose();host=null;}
        header.setVisibility(VISIBLE);carousel.setVisibility(VISIBLE);hint.setVisibility(VISIBLE);chipScroll.setVisibility(VISIBLE);
        playerControls.removeAllViews();playerControls.setVisibility(GONE);bottom.setVisibility(VISIBLE);refreshRecents();
        arrange(getWidth(),getHeight());
        animateTop();
    }
    private void animateTop(){top.animate().cancel();top.setAlpha(.12f);top.setTranslationY(dp(6));top.animate().alpha(1f).translationY(0).setDuration(220).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();}
    private LinearLayout column(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.VERTICAL);return l;}
    private LinearLayout row(){LinearLayout l=new LinearLayout(getContext());l.setOrientation(LinearLayout.HORIZONTAL);return l;}
    private TextView text(String s,float size,int color){TextView t=new TextView(getContext());t.setText(s);t.setTextSize(size);t.setTextColor(color);t.setIncludeFontPadding(false);return t;}
    private TextView button(String s,float size){TextView t=text(s,size,white);t.setGravity(Gravity.CENTER);t.setBackground(glass(20,0x294F638D));t.setClickable(true);return t;}
    private Drawable glass(int radius,int color){GradientDrawable d=new GradientDrawable();d.setColor(color);d.setCornerRadius(dp(radius));d.setStroke(dp(1),0x22D8E4FF);return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF),d,null);}
    private Drawable ripple(){GradientDrawable mask=new GradientDrawable();mask.setColor(Color.WHITE);mask.setCornerRadius(dp(18));return new RippleDrawable(android.content.res.ColorStateList.valueOf(0x22FFFFFF),null,mask);}
    private int dp(float value){return Math.round(value*getResources().getDisplayMetrics().density);}

    private final class HeroCard extends View {
        private final AppCatalog.App app; private final Paint paint=new Paint(3); private final RectF rect=new RectF();
        HeroCard(Context c,AppCatalog.App a){super(c);app=a;setLayerType(View.LAYER_TYPE_SOFTWARE,null);}
        @Override protected void onDraw(Canvas canvas){
            float w=getWidth(),h=getHeight(),r=dp(26); rect.set(0,0,w,h); canvas.save(); Path clip=new Path();clip.addRoundRect(rect,r,r,Path.Direction.CW);canvas.clipPath(clip);
            paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF7283A9,0xFF46577B,0xFF253A63},null,Shader.TileMode.CLAMP));canvas.drawRect(rect,paint);paint.setShader(null);
            float cx=w*.89f,cy=h*.62f,disc=Math.min(w*.38f,h*.73f);
            paint.setColor(0xFF263D82);canvas.drawCircle(cx,cy,disc,paint);paint.setColor(0x2256A7FF);canvas.drawCircle(cx,cy,disc*.84f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x445E8DF1);canvas.drawCircle(cx,cy,disc*.94f,paint);canvas.drawCircle(cx,cy,disc*.74f,paint);paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(72); float giant=disc*.98f;canvas.drawBitmap(app.icon,null,new RectF(cx-giant/2,cy-giant/2,cx+giant/2,cy+giant/2),paint);paint.setAlpha(255);
            int padding=dp(23),iconSize=dp(h<dp(170)?27:36);paint.setColor(Color.WHITE);canvas.drawBitmap(app.icon,null,new RectF(padding,padding,padding+iconSize,padding+iconSize),paint);
            float titleSize=Math.min(dp(32),h*.16f);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(titleSize);paint.setColor(white);
            String title=TextUtils.ellipsize(app.label,new android.text.TextPaint(paint),w*.56f,TextUtils.TruncateAt.END).toString();
            float baseline=Math.max(padding+iconSize+titleSize+dp(15),h*.58f);baseline=Math.min(baseline,h-dp(47));canvas.drawText(title,padding,baseline,paint);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(dp(12));paint.setColor(0xFFDEE6F7);canvas.drawText(app.category+"  /  点击打开  ↗",padding,baseline+dp(25),paint);
            canvas.restore();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x35DFE8FF);rect.inset(.5f,.5f);canvas.drawRoundRect(rect,r,r,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
    private final class Atmosphere extends Drawable {
        private final Paint paint=new Paint(3);
        public void draw(Canvas c){Rect b=getBounds();float w=b.width(),h=b.height();paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF465473,0xFF253554,0xFF121E39},null,Shader.TileMode.CLAMP));c.drawRect(b,paint);
            paint.setShader(new RadialGradient(w*.46f,h*.15f,Math.max(1,w*.8f),new int[]{0x6597ABDB,0x0097ABDB},null,Shader.TileMode.CLAMP));c.drawRect(b,paint);
            paint.setShader(new RadialGradient(w*.8f,h*.75f,Math.max(1,w*.65f),new int[]{0x442342A5,0x002342A5},null,Shader.TileMode.CLAMP));c.drawRect(b,paint);paint.setShader(null);}
        public void setAlpha(int a){} public void setColorFilter(ColorFilter f){} public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
