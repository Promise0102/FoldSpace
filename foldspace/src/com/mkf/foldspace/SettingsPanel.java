package com.mkf.foldspace;

import android.content.Context;
import android.content.pm.PackageManager;
import android.content.res.ColorStateList;
import android.graphics.Typeface;
import android.os.*;
import android.provider.Settings;
import android.view.*;
import android.widget.*;
import rikka.shizuku.Shizuku;

/** An in-place secondary page. It never replaces or resizes the upper app surface. */
public final class SettingsPanel extends LinearLayout {
    public interface Actions {void setAuto(boolean enabled);void restart();void overlayPermission();void shizuku();}
    private final CompoundButton auto;
    private final TextView autoState,angleState,overlayState,shizukuState;
    private final Handler main=new Handler(Looper.getMainLooper());
    private boolean refreshing;
    private final Runnable check=new Runnable(){public void run(){refresh();main.postDelayed(this,1000);}};

    public SettingsPanel(Context c,Actions actions,Runnable back,TextView rotateButton){
        super(c);setOrientation(VERTICAL);setPadding(dp(16),dp(12),dp(16),dp(12));setBackground(SpaceStyle.glass(c,28,0xF02A3C60));setElevation(dp(4));
        setFocusableInTouchMode(true);setClickable(true);
        LinearLayout header=row();header.setGravity(Gravity.CENTER_VERTICAL);
        TextView returnButton=SpaceStyle.button(c,"‹");returnButton.setTextSize(28);returnButton.setContentDescription("返回上一页");returnButton.setOnClickListener(v->back.run());header.addView(returnButton,new LayoutParams(dp(48),dp(48)));
        TextView title=SpaceStyle.text(c,"设置",20,SpaceStyle.WHITE);title.setTypeface(Typeface.DEFAULT,Typeface.BOLD);
        LayoutParams titleParams=new LayoutParams(0,-2,1);titleParams.leftMargin=dp(12);header.addView(title,titleParams);
        header.addView(rotateButton,new LayoutParams(dp(64),dp(48)));
        angleState=SpaceStyle.text(c,"等待角度",11,SpaceStyle.MUTED);angleState.setGravity(Gravity.END);header.addView(angleState);
        addView(header,new LayoutParams(-1,dp(48)));

        ScrollView scroll=new ScrollView(c);scroll.setFillViewport(false);scroll.setVerticalScrollBarEnabled(false);scroll.setClipToPadding(false);
        LinearLayout content=column();content.setPadding(0,dp(12),0,dp(4));scroll.addView(content,new ScrollView.LayoutParams(-1,-2));
        LayoutParams scrollParams=new LayoutParams(-1,0,1);scrollParams.topMargin=dp(2);addView(scroll,scrollParams);

        LinearLayout autoCard=row();autoCard.setGravity(Gravity.CENTER_VERTICAL);autoCard.setPadding(dp(16),dp(14),dp(12),dp(14));autoCard.setBackground(SpaceStyle.glass(c,22,0x344E6898));
        LinearLayout autoLabels=column();TextView autoTitle=SpaceStyle.text(c,"自动悬停",16,SpaceStyle.WHITE);autoTitle.setTypeface(Typeface.DEFAULT,Typeface.BOLD);autoLabels.addView(autoTitle);
        autoState=SpaceStyle.text(c,"半展开时自动进入",11,SpaceStyle.MUTED);LayoutParams subtitle=new LayoutParams(-1,-2);subtitle.topMargin=dp(6);autoLabels.addView(autoState,subtitle);autoCard.addView(autoLabels,new LayoutParams(0,-2,1));
        auto=new GlassToggle(c);auto.setContentDescription("自动悬停开关");auto.setMinHeight(dp(48));autoCard.addView(auto,new LayoutParams(dp(56),dp(48)));
        auto.setOnCheckedChangeListener((button,enabled)->{if(!refreshing){actions.setAuto(enabled);main.post(this::refresh);}});addCard(content,autoCard,0);

        LinearLayout rule=column();rule.setPadding(dp(16),dp(12),dp(16),dp(12));rule.setBackground(SpaceStyle.glass(c,22,0x204A6391));
        LinearLayout range=row();range.setGravity(Gravity.CENTER_VERTICAL);TextView degrees=SpaceStyle.text(c,"110°–140°",24,SpaceStyle.WHITE);degrees.setTypeface(Typeface.DEFAULT,Typeface.BOLD);range.addView(degrees,new LayoutParams(0,-2,1));
        TextView dwell=SpaceStyle.text(c,"连续 3 秒",11,SpaceStyle.ACCENT);dwell.setGravity(Gravity.CENTER);dwell.setPadding(dp(12),dp(7),dp(12),dp(7));dwell.setBackground(SpaceStyle.glass(c,15,0x2A7295CF));range.addView(dwell);rule.addView(range);
        TextView details=SpaceStyle.text(c,"范围内进入 · 范围外退出",11,SpaceStyle.MUTED);LayoutParams detailParams=new LayoutParams(-1,-2);detailParams.topMargin=dp(8);rule.addView(details,detailParams);addCard(content,rule,dp(10));

        LinearLayout permissions=column();permissions.setPadding(dp(16),dp(2),dp(16),dp(2));permissions.setBackground(SpaceStyle.glass(c,22,0x24465E88));
        overlayState=permissionRow(permissions,"悬浮窗权限",actions::overlayPermission);
        View divider=new View(c);divider.setBackgroundColor(0x18D8E4FF);permissions.addView(divider,new LayoutParams(-1,dp(1)));
        shizukuState=permissionRow(permissions,"Shizuku",actions::shizuku);addCard(content,permissions,dp(10));
        TextView restart=SpaceStyle.button(c,"重新启动监测");restart.setOnClickListener(v->actions.restart());LayoutParams restartParams=new LayoutParams(-1,dp(48));restartParams.topMargin=dp(12);content.addView(restart,restartParams);
        TextView footnote=SpaceStyle.text(c,"点击应用只打开所选分区；点击“双开”才启动组合。视频在上，游戏在下。",10,SpaceStyle.MUTED);footnote.setPadding(dp(4),dp(12),dp(4),0);footnote.setLineSpacing(dp(3),1);content.addView(footnote,new LayoutParams(-1,-2));refresh();
    }
    private TextView permissionRow(LinearLayout parent,String name,Runnable action){
        LinearLayout row=new GlassLayout(getContext());row.setPadding(dp(10),0,dp(10),0);row.setGravity(Gravity.CENTER_VERTICAL);row.setMinimumHeight(dp(52));row.setFocusable(true);row.setClickable(true);
        TextView label=SpaceStyle.text(getContext(),name,13,SpaceStyle.WHITE);row.addView(label,new LayoutParams(0,-2,1));
        TextView state=SpaceStyle.text(getContext(),"",11,SpaceStyle.MUTED);row.addView(state);TextView chevron=SpaceStyle.text(getContext(),"  ›",20,SpaceStyle.MUTED);row.addView(chevron);row.setOnClickListener(v->action.run());row.setContentDescription(name);parent.addView(row,new LayoutParams(-1,-2));return state;
    }
    public void fold(float angle,boolean available){angleState.setText(!available?"传感器不可用":Float.isNaN(angle)?"等待角度":Math.round(angle)+"°");}
    public void refresh(){
        refreshing=true;boolean enabled=AppCatalog.prefs(getContext()).getBoolean("auto",false);auto.setChecked(enabled);autoState.setText(enabled?"已开启 · 半展开时自动进入":"已关闭 · 可手动打开应用");refreshing=false;
        overlayState.setText(Settings.canDrawOverlays(getContext())?"已允许":"去开启");
        String state="未启动";try{if(Shizuku.pingBinder())state=Shizuku.checkSelfPermission()==PackageManager.PERMISSION_GRANTED?"已连接":"待授权";}catch(RuntimeException ignored){}shizukuState.setText(state);
    }
    @Override protected void onAttachedToWindow(){super.onAttachedToWindow();main.post(check);}
    @Override protected void onDetachedFromWindow(){main.removeCallbacks(check);super.onDetachedFromWindow();}
    private void addCard(LinearLayout parent,View card,int margin){LayoutParams p=new LayoutParams(-1,-2);p.topMargin=margin;parent.addView(card,p);}
    private LinearLayout row(){return new LinearLayout(getContext());}
    private LinearLayout column(){LinearLayout view=row();view.setOrientation(VERTICAL);return view;}
    private int dp(float value){return SpaceStyle.dp(getContext(),value);}
}
