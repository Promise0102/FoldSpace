package com.mkf.foldspace;

import java.util.*;

/** Worker-thread only. A stale display release cannot remove a replacement in the same slot. */
final class SessionRegistry<T> {
    private static final class Entry<T>{final int id;final T value;Entry(int id,T value){this.id=id;this.value=value;}}
    private final Map<Integer,Entry<T>> slots=new HashMap<>();
    T at(int slot){check(slot);Entry<T> e=slots.get(slot);return e==null?null:e.value;}
    T get(int id){for(Entry<T> e:slots.values())if(e.id==id)return e.value;return null;}
    void put(int slot,int id,T value){check(slot);if(value==null || id<=0 || slots.containsKey(slot) || get(id)!=null)throw new IllegalArgumentException("Occupied display slot");slots.put(slot,new Entry<>(id,value));}
    T remove(int id){for(Iterator<Map.Entry<Integer,Entry<T>>> it=slots.entrySet().iterator();it.hasNext();){Entry<T> e=it.next().getValue();if(e.id==id){it.remove();return e.value;}}return null;}
    List<T> values(){List<T> result=new ArrayList<>();for(Entry<T> e:slots.values())result.add(e.value);return result;}
    boolean isEmpty(){return slots.isEmpty();}
    static void check(int slot){if(slot!=0 && slot!=1)throw new IllegalArgumentException("Only upper and lower slots are supported");}
}
