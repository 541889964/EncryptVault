package com.handwrite.ime;
import android.content.Context; import org.json.JSONArray;
import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
public class ClipboardHistory {
  public static final int MAX = 100_000;   // ★ 需求：剪贴板 10 万字
  private final List<String> items = new ArrayList<>();
  private int total = 0; private final File file;
  public ClipboardHistory(Context c){ file=new File(c.getFilesDir(),"clip.json"); load(); }
  public synchronized void add(String s){
    if(s==null) return;
    if(s.length()>MAX) s=s.substring(0,MAX);
    if(s.isEmpty()) return;
    for(int i=0;i<items.size();i++) if(items.get(i).equals(s)){ items.remove(i); total-=s.length(); break; }
    items.add(0,s); total+=s.length();
    while(total>MAX && items.size()>1) total-=items.remove(items.size()-1).length();
    save();
  }
  public synchronized List<String> all(){ return new ArrayList<>(items); }
  public synchronized void remove(int i){ if(i>=0&&i<items.size()){ total-=items.remove(i).length(); save(); } }
  public synchronized void clear(){ items.clear(); total=0; save(); }
  public synchronized int total(){ return total; }
  private void load(){
    if(!file.exists()) return;
    try(FileInputStream in=new FileInputStream(file)){
      byte[] b=new byte[(int)file.length()]; int r=in.read(b); if(r<=0) return;
      JSONArray a=new JSONArray(new String(b,0,r,StandardCharsets.UTF_8));
      for(int i=0;i<a.length()&&total<MAX;i++){ String s=a.optString(i,""); if(s.isEmpty())continue; items.add(s); total+=s.length(); }
      while(total>MAX&&items.size()>1) total-=items.remove(items.size()-1).length();
    }catch(Throwable ignored){}
  }
  private void save(){
    try(FileOutputStream o=new FileOutputStream(file)){
      JSONArray a=new JSONArray(); for(String s:items) a.put(s);
      o.write(a.toString().getBytes(StandardCharsets.UTF_8));
    }catch(Throwable ignored){}
  }
}
