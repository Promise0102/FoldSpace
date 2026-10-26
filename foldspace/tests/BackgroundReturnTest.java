package com.mkf.foldspace;

import java.util.*;

public final class BackgroundReturnTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Background return check "+checks);}
    private static final class Bridge implements BackgroundReturn.Bridge {
        final Object root=new Object();final List<String> events=new ArrayList<>();String fail="";boolean occupied,confirm=true,noRoot;
        private void event(String name)throws Exception{events.add(name);if(fail.equals(name))throw new Exception(name);}
        public Object createRoot()throws Exception{event("create");return noRoot?null:root;}
        public void lowerRoot(Object value)throws Exception{check(value==root);event("lower");}
        public void moveIntoRoot(Object value)throws Exception{check(value==root);event("move");}
        public void detachAtBottom()throws Exception{event("detach");}
        public boolean commitAndConfirm()throws Exception{event("commit");return confirm;}
        public boolean empty(Object value)throws Exception{check(value==root);event("empty");return !occupied;}
        public void delete(Object value)throws Exception{check(value==root);check(!occupied);event("delete");}
    }
    public static void main(String[] args)throws Exception{
        Bridge normal=new Bridge();check(BackgroundReturn.restore(normal));check(normal.events.equals(Arrays.asList("create","lower","move","detach","commit","empty","delete")));
        Bridge occupied=new Bridge();occupied.occupied=true;check(BackgroundReturn.restore(occupied));check(!occupied.events.contains("delete"));
        for(String step:new String[]{"lower","move","detach","commit"}){
            Bridge failed=new Bridge();failed.fail=step;failed.occupied=step.equals("commit");
            try{BackgroundReturn.restore(failed);throw new AssertionError("Failure not propagated");}catch(Exception expected){checks++;}
            check(failed.events.contains("empty"));check(failed.events.contains("delete")!=failed.occupied);
        }
        Bridge missing=new Bridge();missing.noRoot=true;try{BackgroundReturn.restore(missing);throw new AssertionError("Missing root accepted");}catch(IllegalStateException expected){checks++;}check(missing.events.equals(Arrays.asList("create")));
        Bridge notConfirmed=new Bridge();notConfirmed.confirm=false;check(!BackgroundReturn.restore(notConfirmed));check(notConfirmed.events.contains("delete"));
        System.out.println("PASS: "+checks+" background-return ordering and non-destructive cleanup assertions");
    }
}
