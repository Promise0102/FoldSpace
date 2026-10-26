package com.mkf.foldspace;

import java.util.Locale;

final class WorkspacePolicy {
    static final String VIDEO="视频",GAME="游戏";
    static String category(String pkg,String label,boolean video,boolean game){
        if(game)return GAME;
        if(video)return VIDEO;
        String text=(pkg+" "+label).toLowerCase(Locale.ROOT);
        if(text.matches(".*(aweme|bilibili|kuaishou|qiyi|youku|hunantv|视频|抖音|哔哩|快手|优酷|爱奇艺|芒果).*"))return VIDEO;
        if(text.matches(".*(com\\.tencent\\.(tmgp|ig|lol|sgame)|com\\.mihoyo\\.|com\\.hoyoverse\\.|com\\.netease\\.(hyxd|mc|party|onmyoji)|com\\.mojang\\.|com\\.supercell\\.|com\\.hypergryph\\.|com\\.kurogame\\.|王者荣耀|和平精英|原神|崩坏|星穹铁道|绝区零|明日方舟|鸣潮|第五人格|蛋仔派对|我的世界|金铲铲|英雄联盟|部落冲突|开心消消乐).*"))return GAME;
        return "其他";
    }
    static int slot(String category){return GAME.equals(category)?1:0;}
    private WorkspacePolicy(){}
}
