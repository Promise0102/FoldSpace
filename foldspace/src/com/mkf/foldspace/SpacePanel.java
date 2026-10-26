package com.mkf.foldspace;

import android.content.Context;
import android.graphics.*;
import android.graphics.drawable.Drawable;
import android.text.TextUtils;
import android.view.*;
import android.widget.*;
import java.util.*;

/** Shared immersive surface with an uncluttered hero and four-app pages below the hinge. */
public final class SpacePanel extends FrameLayout {
    public interface Actions extends SettingsPanel.Actions {void launch(AppCatalog.App app);void close();}
    private final Actions actions;
    private final LinearLayout top,bottom,cards,gridPages;
    private final HorizontalScrollView carousel;
    private final GridPager gridPager;
    private final FrameLayout playerControls,settingsLayer,lowerApp,pairToolbar;
    private final TextView pageMarker;
    private final ArrayList<AppCatalog.App> apps=new ArrayList<>();
    private AppHostView host,gameHost;
    private HoverRotation rotation;
    private String category="视频";
    private boolean switchingUpper,pairMode,sidebarExpanded,disposalFinished,disposalFinishing;
    private int pendingReleases;
    private final ArrayList<Runnable> disposalCallbacks=new ArrayList<>();
    private int switchDirection;
    private final ArrayList<AppCatalog.App> allApps=new ArrayList<>();
    private TextView videoTab,gameTab,catalogBack;
    private SettingsPanel settingsPanel;
    private boolean disposed,catalogLoaded,sensorAvailable;
    private float angle=Float.NaN;
    private int lastWidth,lastHeight,insetTop,insetBottom;
    private final int white=SpaceStyle.WHITE,muted=SpaceStyle.MUTED;

    public SpacePanel(Context context,Actions actions,boolean overlay){
        super(context);this.actions=actions;setBackground(new Atmosphere());setFocusableInTouchMode(true);
        top=column();bottom=column();addView(top,new FrameLayout.LayoutParams(-1,-2));lowerApp=new FrameLayout(context);addView(lowerApp,new FrameLayout.LayoutParams(-1,-2));addView(bottom,new FrameLayout.LayoutParams(-1,-2));
        carousel=new HeroPager(context);carousel.setHorizontalScrollBarEnabled(false);carousel.setClipToPadding(false);
        cards=row();carousel.addView(cards,new HorizontalScrollView.LayoutParams(-2,-1));top.addView(carousel,new LinearLayout.LayoutParams(-1,0,1));
        TextView hint=text("常用应用 · 左右滑动",10,muted);hint.setGravity(Gravity.CENTER);top.addView(hint,new LinearLayout.LayoutParams(-1,dp(24)));

        gridPager=new GridPager(context);gridPager.setHorizontalScrollBarEnabled(false);gridPager.setFillViewport(true);gridPager.setClipToPadding(false);
        gridPages=row();gridPager.addView(gridPages,new HorizontalScrollView.LayoutParams(-2,-1));bottom.addView(gridPager,new LinearLayout.LayoutParams(-1,0,1));
        LinearLayout footer=row();footer.setGravity(Gravity.CENTER_VERTICAL);
        TextView close=SpaceStyle.button(context,overlay?"收起":"关闭");close.setContentDescription(overlay?"收起，等待下次半展开":"关闭界面");catalogBack=close;close.setOnClickListener(v->{if(host!=null || gameHost!=null)showRuntime();else actions.close();});footer.addView(close,new LinearLayout.LayoutParams(dp(66),dp(48)));
        videoTab=SpaceStyle.button(context,"视频");gameTab=SpaceStyle.button(context,"游戏");
        videoTab.setOnClickListener(v->showCatalog("视频"));gameTab.setOnClickListener(v->showCatalog("游戏"));
        for(TextView tab:new TextView[]{videoTab,gameTab}){LinearLayout.LayoutParams tabParams=new LinearLayout.LayoutParams(dp(58),dp(48));tabParams.leftMargin=dp(6);footer.addView(tab,tabParams);}
        TextView pair=SpaceStyle.button(context,"双开");pair.setContentDescription("启动上视频、下游戏组合");pair.setOnClickListener(v->startPair());LinearLayout.LayoutParams pairParams=new LinearLayout.LayoutParams(dp(58),dp(48));pairParams.leftMargin=dp(6);footer.addView(pair,pairParams);
        LinearLayout.LayoutParams rotateParams=new LinearLayout.LayoutParams(dp(58),dp(48));rotateParams.leftMargin=dp(6);footer.addView(rotationButton(),rotateParams);
        pageMarker=text("",11,muted);pageMarker.setGravity(Gravity.CENTER);footer.addView(pageMarker,new LinearLayout.LayoutParams(0,-1,1));
        TextView settings=SpaceStyle.button(context,"设置");settings.setContentDescription("打开下半屏设置");settings.setOnClickListener(v->showSettings());footer.addView(settings,new LinearLayout.LayoutParams(dp(76),dp(48)));
        LinearLayout.LayoutParams footerParams=new LinearLayout.LayoutParams(-1,dp(48));footerParams.topMargin=dp(12);bottom.addView(footer,footerParams);

        playerControls=new FrameLayout(context);playerControls.setVisibility(GONE);addView(playerControls,new FrameLayout.LayoutParams(-1,-2));
        pairToolbar=new FrameLayout(context);pairToolbar.setVisibility(GONE);addView(pairToolbar,new FrameLayout.LayoutParams(-1,-2));
        settingsLayer=new FrameLayout(context);settingsLayer.setVisibility(GONE);addView(settingsLayer,new FrameLayout.LayoutParams(-1,-2));
        AppCatalog.load(context,result->{if(disposed)return;catalogLoaded=true;allApps.clear();allApps.addAll(result);filterApps();refreshCards();refreshGrid();});
        setOnApplyWindowInsetsListener((v,insets)->{Insets cutout=insets.getInsets(WindowInsets.Type.displayCutout());insetTop=cutout.top;insetBottom=cutout.bottom;arrange(getWidth(),getHeight());return insets;});
    }
    public void dispose(){dispose(null);}
    public void dispose(Runnable completion){
        if(completion!=null){if(disposalFinished){post(completion);return;}disposalCallbacks.add(completion);}
        if(disposed)return;disposed=true;top.animate().cancel();pairToolbar.animate().cancel();settingsLayer.removeAllViews();settingsPanel=null;
        pendingReleases=(host!=null?1:0)+(gameHost!=null?1:0);
        if(pendingReleases==0)finishDisposal();else{
            if(host!=null)host.dispose(this::hostReleased);
            if(gameHost!=null)gameHost.dispose(this::hostReleased);
        }
    }
    private void hostReleased(){if(--pendingReleases==0)finishDisposal();}
    private void finishDisposal(){
        if(disposalFinishing)return;disposalFinishing=true;
        if(rotation!=null){HoverRotation old=rotation;rotation=null;old.close(this::completeDisposal);}else completeDisposal();
    }
    private void completeDisposal(){
        if(disposalFinished)return;disposalFinished=true;
        ArrayList<Runnable> callbacks=new ArrayList<>(disposalCallbacks);disposalCallbacks.clear();for(Runnable done:callbacks)done.run();
    }
    public void status(float value,boolean hover,boolean available){
        angle=value;sensorAvailable=available;if(settingsPanel!=null)settingsPanel.fold(value,available);
    }
    public void suspendRotation(){if(rotation!=null){rotation.close();rotation=null;}refreshRotationButtons(this);}
    private boolean rotated(){return rotation!=null && !rotation.isClosed();}
    private String rotationLabel(){return rotated()?(rotation.isPending()?"旋转中":"还原"):"横屏";}
    private String rotationDescription(){return !rotated()?"手动切换为横屏":rotation.isPending()?"正在切换横屏，点击取消":"撤回手动横屏，跟随原方向";}
    private TextView rotationButton(){
        TextView button=SpaceStyle.button(getContext(),rotationLabel());button.setTag("manual-orientation");button.setContentDescription(rotationDescription());button.setOnClickListener(v->toggleRotation());return button;
    }
    private void toggleRotation(){
        if(disposed)return;
        if(rotated())suspendRotation();else{rotation=new HoverRotation(this,()->{if(!disposed)refreshRotationButtons(this);});rotation.start();refreshRotationButtons(this);}
    }
    private void refreshRotationButtons(View view){
        if(view instanceof TextView && "manual-orientation".equals(view.getTag())){((TextView)view).setText(rotationLabel());view.setContentDescription(rotationDescription());}
        if(view instanceof ViewGroup){ViewGroup group=(ViewGroup)view;for(int i=0;i<group.getChildCount();i++)refreshRotationButtons(group.getChildAt(i));}
    }
    public void refreshRecents(){refreshCards();refreshGrid();refreshSettings();}
    public void refreshSettings(){if(settingsPanel!=null)settingsPanel.refresh();}
    public void showSettings(){
        if(disposed || settingsPanel!=null)return;
        settingsPanel=new SettingsPanel(getContext(),actions,this::hideSettings,rotationButton());settingsPanel.fold(angle,sensorAvailable);
        settingsLayer.addView(settingsPanel,new FrameLayout.LayoutParams(-1,-1));settingsLayer.setVisibility(VISIBLE);settingsLayer.bringToFront();
        settingsPanel.setAlpha(0);settingsPanel.setTranslationY(dp(8));settingsPanel.animate().alpha(1).translationY(0).setDuration(180).start();settingsPanel.requestFocus();
    }
    public boolean hideSettings(){if(settingsPanel==null)return false;settingsPanel.animate().cancel();settingsLayer.removeAllViews();settingsLayer.setVisibility(GONE);settingsPanel=null;return true;}
    @Override public boolean dispatchKeyEvent(KeyEvent event){if(settingsPanel!=null && event.getKeyCode()==KeyEvent.KEYCODE_BACK){if(event.getAction()==KeyEvent.ACTION_UP)hideSettings();return true;}return super.dispatchKeyEvent(event);}
    @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);arrange(w,h);}
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();post(()->ImmersiveMode.apply(this));}
    @Override public void onWindowFocusChanged(boolean focused){super.onWindowFocusChanged(focused);if(focused){post(()->ImmersiveMode.apply(this));refreshSettings();}}
    private void arrange(int w,int h){
        if(w<=0 || h<=0)return;int margin=dp(w/getResources().getDisplayMetrics().density>700?32:22),split=h/2;
        FrameLayout.LayoutParams upper=(FrameLayout.LayoutParams)top.getLayoutParams();upper.width=host==null?w-2*margin:w;upper.height=host==null?Math.max(dp(110),split-insetTop-dp(8)):split;upper.leftMargin=host==null?margin:0;upper.topMargin=host==null?insetTop+dp(8):0;top.setLayoutParams(upper);
        FrameLayout.LayoutParams lower=new FrameLayout.LayoutParams(w-2*margin,Math.max(dp(120),h-split-insetBottom-dp(24)));lower.leftMargin=margin;lower.topMargin=split+dp(10);bottom.setLayoutParams(lower);
        FrameLayout.LayoutParams controls=new FrameLayout.LayoutParams(lower.width,lower.height);controls.leftMargin=lower.leftMargin;controls.topMargin=lower.topMargin;playerControls.setLayoutParams(controls);
        FrameLayout.LayoutParams settings=new FrameLayout.LayoutParams(lower.width,lower.height);settings.leftMargin=lower.leftMargin;settings.topMargin=lower.topMargin;settingsLayer.setLayoutParams(settings);
        FrameLayout.LayoutParams gameGeometry=new FrameLayout.LayoutParams(w,h-split);gameGeometry.topMargin=split;lowerApp.setLayoutParams(gameGeometry);
        int sideHeight=sidebarExpanded?Math.max(dp(120),h-split-insetBottom-dp(24)):dp(88);FrameLayout.LayoutParams toolbarGeometry=new FrameLayout.LayoutParams(dp(sidebarExpanded?76:24),sideHeight);toolbarGeometry.gravity=Gravity.TOP|Gravity.RIGHT;toolbarGeometry.topMargin=sidebarExpanded?split+dp(12):split+Math.max(0,(h-split-sideHeight-insetBottom)/2);toolbarGeometry.rightMargin=0;pairToolbar.setLayoutParams(toolbarGeometry);
        if(lastWidth!=w || lastHeight!=h){lastWidth=w;lastHeight=h;post(()->{if(!disposed){refreshCards();refreshGrid();}});}
    }
    private void refreshCards(){
        if(disposed)return;cards.removeAllViews();int width=Math.max(dp(200),getWidth()-dp(68));
        for(AppCatalog.App app:AppCatalog.featured(getContext(),allApps)){
            HeroCard card=new HeroCard(getContext(),app);card.setContentDescription("打开"+app.label);card.setClickable(true);card.setFocusable(true);card.setOnClickListener(v->open(app));
            LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(width,-1);p.rightMargin=dp(12);cards.addView(card,p);
        }
        if(allApps.isEmpty()){TextView empty=text(catalogLoaded?"未找到视频或游戏应用":"正在读取应用",16,white);empty.setGravity(Gravity.CENTER);cards.addView(empty,new LinearLayout.LayoutParams(width,-1));}
    }
    private void refreshGrid(){
        if(disposed || gridPager.getWidth()<=0 || gridPager.getHeight()<=0)return;
        int width=gridPager.getWidth(),height=gridPager.getHeight(),page=Math.round(gridPager.getScrollX()/(float)width);gridPages.removeAllViews();
        int pageCount=Math.max(1,(apps.size()+3)/4);
        for(int p=0;p<pageCount;p++){
            GridLayout grid=new GridLayout(getContext());grid.setColumnCount(2);grid.setRowCount(2);grid.setAlignmentMode(GridLayout.ALIGN_BOUNDS);
            gridPages.addView(grid,new LinearLayout.LayoutParams(width,height));
            if(apps.isEmpty()){TextView empty=text(catalogLoaded?"暂无"+category+"应用":"正在读取应用…",14,muted);empty.setGravity(Gravity.CENTER);grid.addView(empty,new GridLayout.LayoutParams(GridLayout.spec(0,2,1f),GridLayout.spec(0,2,1f)));continue;}
            for(int slot=0;slot<4;slot++){
                int index=p*4+slot;View tile=index<apps.size()?appTile(apps.get(index)):new View(getContext());
                GridLayout.LayoutParams cell=new GridLayout.LayoutParams(GridLayout.spec(slot/2,1,1f),GridLayout.spec(slot%2,1,1f));cell.width=0;cell.height=0;
                cell.leftMargin=slot%2==0?0:dp(6);cell.rightMargin=slot%2==0?dp(6):0;cell.topMargin=slot<2?0:dp(6);cell.bottomMargin=slot<2?dp(6):0;grid.addView(tile,cell);
            }
        }
        final int target=Math.min(page,pageCount-1);gridPager.post(()->{if(!disposed){gridPager.scrollTo(target*gridPager.getWidth(),0);updatePageMarker();}});updatePageMarker();
    }
    private View appTile(AppCatalog.App app){
        LinearLayout tile=new GlassLayout(getContext());tile.setOrientation(LinearLayout.VERTICAL);tile.setGravity(Gravity.CENTER);tile.setPadding(dp(12),dp(10),dp(12),dp(10));tile.setClickable(true);tile.setFocusable(true);
        ImageView icon=new ImageView(getContext());icon.setImageBitmap(app.icon);tile.addView(icon,new LinearLayout.LayoutParams(dp(44),dp(44)));
        TextView label=text(app.label,14,white);label.setMaxLines(1);label.setEllipsize(TextUtils.TruncateAt.END);label.setGravity(Gravity.CENTER);LinearLayout.LayoutParams labelParams=new LinearLayout.LayoutParams(-1,-2);labelParams.topMargin=dp(10);tile.addView(label,labelParams);
        tile.setContentDescription("打开"+app.label);tile.setOnClickListener(v->open(app));return tile;
    }
    private void updatePageMarker(){if(pageMarker==null || gridPager==null)return;int count=Math.max(1,(apps.size()+3)/4),page=Math.min(count,Math.max(1,Math.round(gridPager.getScrollX()/(float)Math.max(1,gridPager.getWidth()))+1));pageMarker.setText(count>1?page+" / "+count:"");pageMarker.setContentDescription(count>1?"第"+page+"页，共"+count+"页":null);}
    private void filterApps(){
        apps.clear();for(AppCatalog.App app:allApps)if(category.equals(app.category))apps.add(app);
        videoTab.setAlpha(category.equals("视频")?1:.55f);gameTab.setAlpha(category.equals("游戏")?1:.55f);
    }
    private void showCatalog(String type){
        if(disposed)return;hideSettings();category=type;filterApps();gridPager.scrollTo(0,0);catalogBack.setText(host!=null || gameHost!=null?"返回":"收起");catalogBack.setContentDescription(host!=null || gameHost!=null?"返回正在运行的分区":"关闭界面");
        bottom.setBackground((host!=null || gameHost!=null)?SpaceStyle.glass(getContext(),24,0xFA273B60):null);
        bottom.setVisibility(VISIBLE);playerControls.setVisibility(GONE);pairToolbar.setVisibility(GONE);refreshGrid();
    }
    private AppCatalog.App preferred(String type,boolean requireSaved){
        String saved=AppCatalog.prefs(getContext()).getString("paired."+type,"");
        for(AppCatalog.App app:allApps)if(app.category.equals(type) && app.packageName.equals(saved))return app;
        if(requireSaved)return null;
        ArrayList<AppCatalog.App> candidates=new ArrayList<>();for(AppCatalog.App app:allApps)if(type.equals(app.category))candidates.add(app);
        List<AppCatalog.App> sorted=AppCatalog.featured(getContext(),candidates);return sorted.isEmpty()?null:sorted.get(0);
    }
    private void open(AppCatalog.App app){
        if(disposed)return;
        // Only an explicit pair action may retain a second app. Saved preferences never launch it.
        if("游戏".equals(app.category)){
            if(!pairMode)closeUpper();
            openGame(app);
        }else{
            if(!pairMode)closeGame();
            openUpper(app);
        }
        showRuntime();
    }
    public void openAutomatically(AppCatalog.App app){
        if(disposed)return;pairMode=false;open(app);
    }
    private void chooseGameForPair(){
        if(disposed)return;pairMode=true;showCatalog("游戏");
    }
    private void startPair(){
        if(disposed)return;pairMode=true;
        AppCatalog.App video=host==null || !"视频".equals(host.app().category)?preferred("视频",false):host.app();AppCatalog.App game=gameHost==null?preferred("游戏",false):gameHost.app();
        if(video!=null)openUpper(video);
        if(game!=null)openGame(game);
        if(video==null || game==null){showCatalog(video==null?"视频":"游戏");Toast.makeText(getContext(),"请选择"+(video==null?"上半区视频":"下半区游戏")+"应用",Toast.LENGTH_SHORT).show();}else showRuntime();
    }
    private void launched(AppCatalog.App app){
        if(disposed)return;actions.launch(app);
    }
    private void openUpper(AppCatalog.App app){
        hideSettings();if(host!=null && host.app().packageName.equals(app.packageName) && host.isReusable())return;
        if(host!=null){top.removeView(host);host.dispose();}
        host=new AppHostView(getContext(),app,WorkspacePolicy.slot(app.category),()->launched(app));host.setAppSwipeListener(this::switchUpperApp);
        for(int i=0;i<top.getChildCount();i++)top.getChildAt(i).setVisibility(GONE);
        top.addView(host,0,new LinearLayout.LayoutParams(-1,0,1));arrange(getWidth(),getHeight());animateTop();
    }
    private void openGame(AppCatalog.App app){
        hideSettings();if(gameHost!=null && gameHost.app().packageName.equals(app.packageName) && gameHost.isReusable())return;
        if(gameHost!=null){lowerApp.removeView(gameHost);gameHost.dispose();}
        gameHost=new AppHostView(getContext(),app,WorkspacePolicy.slot(app.category),()->launched(app));lowerApp.addView(gameHost,new FrameLayout.LayoutParams(-1,-1));
        lowerApp.setAlpha(.12f);lowerApp.animate().alpha(1).setDuration(220).start();
    }
    private void showRuntime(){
        hideSettings();bottom.setVisibility(GONE);playerControls.removeAllViews();
        if(gameHost!=null){playerControls.setVisibility(GONE);pairToolbar.setVisibility(VISIBLE);toolbar(false);}
        else if(host!=null && "视频".equals(host.app().category)){
            pairToolbar.setVisibility(GONE);playerControls.setVisibility(VISIBLE);
            playerControls.addView(host.controls(()->showCatalog("视频"),this::showSettings,this::chooseGameForPair,rotationButton()),new FrameLayout.LayoutParams(-1,-1));
        }else{playerControls.setVisibility(GONE);showCatalog("视频");}
        arrange(getWidth(),getHeight());
    }
    private void toolbar(boolean expanded){
        sidebarExpanded=expanded;pairToolbar.removeAllViews();pairToolbar.setContentDescription(expanded?"贴边侧边栏":"展开贴边侧边栏");
        if(!expanded){
            TextView handle=SpaceStyle.button(getContext(),"‹");handle.setTextSize(25);handle.setMinHeight(0);handle.setContentDescription("展开右侧贴边工具栏");handle.setOnClickListener(v->toolbar(true));pairToolbar.addView(handle,new FrameLayout.LayoutParams(-1,-1));
        }else{
            LinearLayout panel=column();panel.setPadding(dp(5),dp(5),dp(5),dp(5));panel.setBackground(SpaceStyle.glass(getContext(),22,0xBA273B60));
            TextView collapse=SpaceStyle.button(getContext(),"›");collapse.setTextSize(24);collapse.setMinHeight(dp(36));collapse.setContentDescription("收起侧边栏");collapse.setOnClickListener(v->toolbar(false));panel.addView(collapse,new LinearLayout.LayoutParams(-1,dp(36)));
            ScrollView scroll=new ScrollView(getContext());scroll.setVerticalScrollBarEnabled(false);LinearLayout items=column();scroll.addView(items,new ScrollView.LayoutParams(-1,-2));LinearLayout.LayoutParams scrollParams=new LinearLayout.LayoutParams(-1,0,1);scrollParams.topMargin=dp(6);panel.addView(scroll,scrollParams);
            if(host!=null)tool(items,"上返回",()->host.back());if(gameHost!=null)tool(items,"下返回",()->gameHost.back());
            tool(items,"视频",()->showCatalog("视频"));tool(items,"游戏",()->showCatalog("游戏"));if(host!=null && gameHost!=null)tool(items,"单屏",this::singleVideo);
            LinearLayout.LayoutParams rotateParams=new LinearLayout.LayoutParams(-1,dp(44));rotateParams.bottomMargin=dp(6);items.addView(rotationButton(),rotateParams);
            tool(items,"设置",this::showSettings);tool(items,"收起",actions::close);pairToolbar.addView(panel,new FrameLayout.LayoutParams(-1,-1));
        }
        arrange(getWidth(),getHeight());pairToolbar.animate().cancel();pairToolbar.setTranslationX(dp(expanded?36:12));pairToolbar.setAlpha(.25f);pairToolbar.animate().translationX(0).alpha(1).setDuration(180).setInterpolator(new android.view.animation.DecelerateInterpolator()).start();
    }
    private void tool(LinearLayout bar,String label,Runnable action){TextView button=SpaceStyle.button(getContext(),label);button.setTextSize(12);button.setMinHeight(dp(44));button.setOnClickListener(v->action.run());button.setContentDescription(label);LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,dp(44));p.bottomMargin=dp(6);bar.addView(button,p);}
    private void closeUpper(){
        if(host!=null){host.animate().cancel();top.removeView(host);host.dispose();host=null;}
        top.animate().cancel();top.setAlpha(1);top.setTranslationX(0);top.setTranslationY(0);switchingUpper=false;switchDirection=0;
        for(int i=0;i<top.getChildCount();i++)top.getChildAt(i).setVisibility(VISIBLE);
        arrange(getWidth(),getHeight());
    }
    private void closeGame(){
        lowerApp.animate().cancel();if(gameHost!=null){lowerApp.removeView(gameHost);gameHost.dispose();gameHost=null;}
    }
    private void singleVideo(){pairMode=false;closeGame();showRuntime();}
    private boolean switchUpperApp(int direction){
        if(disposed || switchingUpper || host==null)return false;
        ArrayList<AppCatalog.App> candidates=new ArrayList<>();
        for(AppCatalog.App app:allApps)if("视频".equals(app.category))candidates.add(app);
        int current=-1;for(int i=0;i<candidates.size();i++)if(candidates.get(i).packageName.equals(host.app().packageName))current=i;
        if(current<0){candidates.add(0,host.app());current=0;}
        if(candidates.size()<2){Toast.makeText(getContext(),"暂无其他视频应用可切换",Toast.LENGTH_SHORT).show();return false;}
        AppCatalog.App next=candidates.get((current+direction+candidates.size())%candidates.size());AppHostView previous=host;
        switchingUpper=true;previous.animate().cancel();previous.animate().translationX(-direction*getWidth()*.22f).alpha(0).setDuration(130).withEndAction(()->{
            if(disposed || host!=previous){switchingUpper=false;return;}
            switchDirection=direction;openUpper(next);showRuntime();
        }).start();return true;
    }
    private void animateTop(){
        int direction=switchDirection;switchDirection=0;top.animate().cancel();top.setAlpha(.12f);top.setTranslationX(direction*dp(34));top.setTranslationY(direction==0?dp(6):0);
        top.animate().alpha(1).translationX(0).translationY(0).setDuration(240).setInterpolator(new android.view.animation.DecelerateInterpolator()).withEndAction(()->switchingUpper=false).start();
    }
    private LinearLayout column(){LinearLayout view=row();view.setOrientation(LinearLayout.VERTICAL);return view;}
    private LinearLayout row(){return new LinearLayout(getContext());}
    private TextView text(String value,float size,int color){return SpaceStyle.text(getContext(),value,size,color);}
    private int dp(float value){return SpaceStyle.dp(getContext(),value);}

    private final class HeroPager extends HorizontalScrollView{
        private float startX,startY;private int startPage;
        HeroPager(Context context){super(context);setOverScrollMode(OVER_SCROLL_NEVER);setContentDescription("常用应用，每次左右滑动切换一张卡片");}
        private int stride(){if(cards==null || cards.getChildCount()==0)return Math.max(1,getWidth());View card=cards.getChildAt(0);return Math.max(1,card.getWidth()+dp(12));}
        @Override public boolean dispatchTouchEvent(MotionEvent event){
            int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_DOWN){startX=event.getRawX();startY=event.getRawY();startPage=Math.round(getScrollX()/(float)stride());}
            boolean handled=super.dispatchTouchEvent(event);
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){float dx=startX-event.getRawX(),dy=startY-event.getRawY();int page=startPage;
                if(action==MotionEvent.ACTION_UP && Math.abs(dx)>dp(24) && Math.abs(dx)>Math.abs(dy)*1.35f)page+=dx>0?1:-1;
                int target=Math.max(0,Math.min(Math.max(0,cards.getChildCount()-1),page));smoothScrollTo(target*stride(),0);
            }
            return handled;
        }
        @Override public void fling(int velocityX){}
    }
    private final class GridPager extends HorizontalScrollView{
        private float downX;private int downPage;
        GridPager(Context c){super(c);setOverScrollMode(OVER_SCROLL_NEVER);setContentDescription("视频和游戏应用四宫格，左右滑动翻页");}
        @Override protected void onSizeChanged(int w,int h,int oldw,int oldh){super.onSizeChanged(w,h,oldw,oldh);if(w!=oldw || h!=oldh)post(()->refreshGrid());}
        @Override public boolean onInterceptTouchEvent(MotionEvent event){if(event.getActionMasked()==MotionEvent.ACTION_DOWN){downX=event.getX();downPage=Math.round(getScrollX()/(float)Math.max(1,getWidth()));}return super.onInterceptTouchEvent(event);}
        @Override public boolean onTouchEvent(MotionEvent event){
            boolean handled=super.onTouchEvent(event);int action=event.getActionMasked();
            if(action==MotionEvent.ACTION_UP || action==MotionEvent.ACTION_CANCEL){int page=Math.round(getScrollX()/(float)Math.max(1,getWidth()));float drag=downX-event.getX();if(action==MotionEvent.ACTION_UP && Math.abs(drag)>getWidth()*.18f)page=downPage+(drag>0?1:-1);int target=Math.max(0,Math.min(Math.max(1,(apps.size()+3)/4)-1,page));smoothScrollTo(target*getWidth(),0);}return handled;
        }
        @Override public void fling(int velocityX){} // release snaps to one four-app page
        @Override protected void onScrollChanged(int x,int y,int oldx,int oldy){super.onScrollChanged(x,y,oldx,oldy);updatePageMarker();}
    }
    private final class HeroCard extends View{
        private final AppCatalog.App app;private final Paint paint=new Paint(3);private final RectF rect=new RectF();private final LiquidGlassDrawable surface;
        HeroCard(Context c,AppCatalog.App app){super(c);this.app=app;surface=new LiquidGlassDrawable(this,26);SpaceStyle.press(this);}
        @Override protected void onDraw(Canvas canvas){
            if(LiquidGlassDrawable.isCapturing())return;
            float w=getWidth(),h=getHeight(),r=dp(26);rect.set(0,0,w,h);canvas.save();Path clip=new Path();clip.addRoundRect(rect,r,r,Path.Direction.CW);canvas.clipPath(clip);
            surface.setBounds(0,0,getWidth(),getHeight());surface.draw(canvas);
            float cx=w*.89f,cy=h*.62f,disc=Math.min(w*.38f,h*.73f);paint.setColor(0xFF263D82);canvas.drawCircle(cx,cy,disc,paint);paint.setColor(0x2256A7FF);canvas.drawCircle(cx,cy,disc*.84f,paint);
            paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x445E8DF1);canvas.drawCircle(cx,cy,disc*.94f,paint);canvas.drawCircle(cx,cy,disc*.74f,paint);paint.setStyle(Paint.Style.FILL);
            paint.setAlpha(72);float giant=disc*.98f;canvas.drawBitmap(app.icon,null,new RectF(cx-giant/2,cy-giant/2,cx+giant/2,cy+giant/2),paint);paint.setAlpha(255);
            int padding=dp(23),iconSize=dp(h<dp(170)?27:36);paint.setColor(Color.WHITE);canvas.drawBitmap(app.icon,null,new RectF(padding,padding,padding+iconSize,padding+iconSize),paint);
            float titleSize=Math.min(dp(32),h*.16f);paint.setTypeface(Typeface.create("sans-serif",Typeface.BOLD));paint.setTextSize(titleSize);paint.setColor(white);
            String title=TextUtils.ellipsize(app.label,new android.text.TextPaint(paint),w*.56f,TextUtils.TruncateAt.END).toString();float baseline=Math.min(Math.max(padding+iconSize+titleSize+dp(15),h*.58f),h-dp(47));canvas.drawText(title,padding,baseline,paint);
            paint.setTypeface(Typeface.create("sans-serif",Typeface.NORMAL));paint.setTextSize(dp(12));paint.setColor(0xFFDEE6F7);canvas.drawText("点击打开  ↗",padding,baseline+dp(25),paint);
            canvas.restore();paint.setStyle(Paint.Style.STROKE);paint.setStrokeWidth(dp(1));paint.setColor(0x35DFE8FF);rect.inset(.5f,.5f);canvas.drawRoundRect(rect,r,r,paint);paint.setStyle(Paint.Style.FILL);
        }
    }
    private final class Atmosphere extends Drawable{
        private final Paint paint=new Paint(3);
        public void draw(Canvas canvas){Rect bounds=getBounds();float w=bounds.width(),h=bounds.height();paint.setShader(new LinearGradient(0,0,w,h,new int[]{0xFF465473,0xFF253554,0xFF121E39},null,Shader.TileMode.CLAMP));canvas.drawRect(bounds,paint);paint.setShader(new RadialGradient(w*.46f,h*.15f,Math.max(1,w*.8f),new int[]{0x6597ABDB,0x0097ABDB},null,Shader.TileMode.CLAMP));canvas.drawRect(bounds,paint);paint.setShader(new RadialGradient(w*.8f,h*.75f,Math.max(1,w*.65f),new int[]{0x442342A5,0x002342A5},null,Shader.TileMode.CLAMP));canvas.drawRect(bounds,paint);paint.setShader(null);}
        public void setAlpha(int alpha){}public void setColorFilter(ColorFilter filter){}public int getOpacity(){return PixelFormat.OPAQUE;}
    }
}
