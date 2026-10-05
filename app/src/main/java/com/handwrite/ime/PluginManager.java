package com.handwrite.ime;
import android.content.Context; import android.net.Uri;
import org.json.*; import java.io.*; import java.nio.charset.StandardCharsets; import java.util.*;
public class PluginManager {
  public static class K { public String label, action; public float weight=1f; public boolean accent=false; }
  public static class P {
    public String name="默认"; public float canvasRatio=0.60f;
    public String barBg="#F5FFFFFF", canvasBg="#FAFBFC";
    public String keyColor="#1B1B1F", accentColor="#4A7DFF";
    public List<K> keys=new ArrayList<>();
  }
  private final Context ctx;
  public PluginManager(Context c){ ctx=c; }
  public File dir(){ File d=new File(ctx.getFilesDir(),"plugins"); if(!d.exists()) d.mkdirs(); return d; }
  public List<File> list(){ List<File> out=new ArrayList<>(); File[] fs=dir().listFiles(); if(fs==null) return out;
    for(File f:fs) if(f.isFile()&&f.getName().endsWith(".json")) out.add(f); return out; }
  public File importUri(Uri uri,String name){
    try{ InputStream in=ctx.getContentResolver().openInputStream(uri); if(in==null) return null;
      if(!name.endsWith(".json")) name+=".json";
      File dst=new File(dir(),name);
      OutputStream o=new FileOutputStream(dst); byte[] b=new byte[8192]; int r;
      while((r=in.read(b))>0) o.write(b,0,r); o.close(); in.close(); return dst;
    }catch(Throwable t){ return null; }
  }
  public void del(File f){ if(f!=null&&f.exists()) f.delete(); }
  public P def(){
    P p=new P();
    p.keys.add(mk("🎤","voice",1.4f,true));
    p.keys.add(mk("空格","space",3.6f,false));
    p.keys.add(mk("←","backspace",1.4f,false));
    p.keys.add(mk("✎","edit",1.4f,false));
    p.keys.add(mk("📋","clipboard",1.4f,false));
    p.keys.add(mk("↵","enter",1.6f,true));
    return p;
  }
  public P load(File f){
    P p=def(); if(f==null||!f.exists()) return p;
    try(FileInputStream in=new FileInputStream(f)){
      byte[] b=new byte[(int)f.length()]; int r=in.read(b); if(r<=0) return p;
      JSONObject o=new JSONObject(new String(b,0,r,StandardCharsets.UTF_8));
      p.name=o.optString("name",f.getName());
      p.canvasRatio=(float)o.optDouble("canvasRatio",p.canvasRatio);
      p.barBg=o.optString("barBg",p.barBg);
      p.canvasBg=o.optString("canvasBg",p.canvasBg);
      p.keyColor=o.optString("keyColor",p.keyColor);
      p.accentColor=o.optString("accentColor",p.accentColor);
      JSONArray a=o.optJSONArray("bottomKeys");
      if(a!=null&&a.length()>0){ p.keys.clear();
        for(int i=0;i<a.length();i++){ JSONObject k=a.getJSONObject(i); K d=new K();
          d.label=k.optString("label","?"); d.action=k.optString("action","space");
          d.weight=(float)k.optDouble("weight",1); d.accent=k.optBoolean("accent",false);
          p.keys.add(d); } }
    }catch(Throwable ignored){}
    return p;
  }
  private static K mk(String l,String a,float w,boolean ac){ K k=new K(); k.label=l; k.action=a; k.weight=w; k.accent=ac; return k; }
}
