package com.mkf.foldspace;

public final class WorkspacePolicyTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Workspace policy check "+checks);}
    public static void main(String[] args){
        check(WorkspacePolicy.category("com.ss.android.ugc.aweme","抖音",false,false).equals("视频"));
        check(WorkspacePolicy.category("tv.danmaku.bili","哔哩哔哩",false,false).equals("视频"));
        check(WorkspacePolicy.category("com.tencent.tmgp.sgame","王者荣耀",false,false).equals("游戏"));
        check(WorkspacePolicy.category("com.mihoyo.GenshinImpact","原神",false,false).equals("游戏"));
        check(WorkspacePolicy.category("unknown.game","New Game",false,true).equals("游戏"));
        check(WorkspacePolicy.category("unknown.video","New Video",true,false).equals("视频"));
        check(WorkspacePolicy.category("game.video","视频游戏",true,true).equals("游戏"));
        check(WorkspacePolicy.category("com.android.settings","设置",false,false).equals("其他"));
        check(WorkspacePolicy.category("com.tencent.mm","微信",false,false).equals("其他"));
        check(WorkspacePolicy.category("com.netease.cloudmusic","网易云音乐",false,false).equals("其他"));
        check(WorkspacePolicy.slot("视频")==0);check(WorkspacePolicy.slot("游戏")==1);check(WorkspacePolicy.slot("其他")==0);
        System.out.println("PASS: "+checks+" workspace category and slot assertions");
    }
}
