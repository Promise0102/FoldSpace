package com.mkf.foldspace;

public final class SessionRegistryTest {
    private static int checks;
    private static void check(boolean ok){checks++;if(!ok)throw new AssertionError("Session isolation check "+checks);}
    public static void main(String[] args){
        SessionRegistry<String> registry=new SessionRegistry<>();
        check(registry.isEmpty());registry.put(0,10,"video");registry.put(1,11,"game");
        check(registry.get(10).equals("video"));check(registry.get(11).equals("game"));check(registry.values().size()==2);
        check(registry.remove(10).equals("video"));registry.put(0,12,"newVideo");
        check(registry.remove(10)==null);check(registry.at(0).equals("newVideo"));check(registry.at(1).equals("game"));
        check(registry.remove(999)==null);check(registry.values().size()==2);
        try{registry.put(0,13,"occupied");throw new AssertionError("Occupied slot accepted");}catch(IllegalArgumentException expected){checks++;}
        try{registry.put(2,13,"third");throw new AssertionError("Third slot accepted");}catch(IllegalArgumentException expected){checks++;}
        registry.remove(12);
        try{registry.put(0,11,"duplicate");throw new AssertionError("Duplicate display accepted");}catch(IllegalArgumentException expected){checks++;}
        for(int id=100;id<600;id++){
            registry.put(0,id,"v"+id);check(registry.get(id).equals("v"+id));
            check(registry.remove(id-1)==null);check(registry.get(11).equals("game"));
            check(registry.remove(id).equals("v"+id));check(registry.at(0)==null);
        }
        // The service shutdown iterates a snapshot while releases remove entries.
        for(String value:registry.values())check(value.equals("game"));registry.remove(11);check(registry.isEmpty());
        System.out.println("PASS: "+checks+" independent-display and stale-session assertions");
    }
}
