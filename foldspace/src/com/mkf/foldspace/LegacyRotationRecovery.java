package com.mkf.foldspace;

import android.util.AtomicFile;
import android.util.Log;
import org.json.JSONObject;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.util.concurrent.TimeUnit;

/** One-time recovery for a 0.5.0 journal. New versions never acquire a global rotation lock. */
final class LegacyRotationRecovery {
    static void restore(){
        AtomicFile journal=new AtomicFile(new File("/data/local/tmp/foldspace-rotation.json"));
        if(!journal.getBaseFile().exists() && !new File(journal.getBaseFile()+".bak").exists())return;
        try{
            JSONObject saved=new JSONObject(new String(journal.readFully(),StandardCharsets.UTF_8));
            String before=saved.getString("rotation"),fixed=saved.getString("fixed");
            if(!before.matches("free|lock [0-3]") || !fixed.matches("default|disabled|enabled|enabled_if_no_auto_rotation"))throw new IOException("Invalid legacy rotation journal");
            // A free current state is already the user's newer preference; don't turn it off.
            String current=command("user-rotation","-d","0").trim();
            if(!current.matches("free|lock [0-3]"))throw new IOException("Unsupported rotation response");
            String currentFixed=command("fixed-to-user-rotation","-d","0").trim();
            if(currentFixed.equals("enabled")){
                if(!current.equals("free")){
                    if(before.equals("free"))command("user-rotation","-d","0","free");
                    else command("user-rotation","-d","0","lock",before.substring(5));
                }
                command("fixed-to-user-rotation","-d","0",fixed);
            }
            journal.delete();Log.i("FoldSpaceRotation","Legacy system-rotation journal recovered");
        }catch(Exception error){Log.w("FoldSpaceRotation","Legacy rotation recovery will retry on next service start",error);}
    }
    private static String command(String... args)throws Exception{
        String[] command=new String[args.length+1];command[0]="/system/bin/wm";System.arraycopy(args,0,command,1,args.length);
        Process process=new ProcessBuilder(command).redirectErrorStream(true).start();
        if(!process.waitFor(1500,TimeUnit.MILLISECONDS)){process.destroy();throw new IOException("Legacy rotation recovery timed out");}
        ByteArrayOutputStream out=new ByteArrayOutputStream();byte[] bytes=new byte[512];int count;
        try(InputStream in=process.getInputStream()){while((count=in.read(bytes))!=-1){out.write(bytes,0,count);if(out.size()>4096)throw new IOException("Unexpected rotation response");}}
        if(process.exitValue()!=0)throw new IOException("System refused legacy rotation recovery");
        return out.toString("UTF-8");
    }
    private LegacyRotationRecovery(){}
}
