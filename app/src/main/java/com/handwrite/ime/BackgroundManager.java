package com.handwrite.ime;
import android.content.*; import android.content.res.AssetManager;
import android.graphics.*; import android.graphics.drawable.*; import android.net.Uri; import android.view.View;
import androidx.core.content.ContextCompat;
import java.io.*; import java.util.*;
public class BackgroundManager {
  private static final String PREF="ime_bg", KEY_TYPE="t", KEY_VAL="v";
  public static final int NONE=0, BUILTIN=1, ASSET=2, USER=3;
  private static final int[] BUILTIN = {
    R.drawable.bg_1,R.drawable.bg_2,R.drawable.bg_3,
    R.drawable.bg_4,R.drawable.bg_5,R.drawable.bg_6 };
  public static final String[] BUILTIN_NAMES = {
    "紫蓝","青绿","暖橙","深海","蜜桃","午夜" };
  private final Context ctx; private final SharedPreferences sp;
  public BackgroundManager(Context c){ ctx=c; sp=c.getSharedPreferences(PREF,Context.MODE_PRIVATE); }
  public int type(){ return sp.getInt(KEY_TYPE,NONE); }
  public String val(){ return sp.getString(KEY_VAL,null); }
  public void set(int t,String v){ sp.edit().putInt(KEY_TYPE,t).putString(KEY_VAL,v).apply(); }
  public void clear(){ set(NONE,null); }
  public List<String> listAssets(){
    List<String> out=new ArrayList<>();
    try{ AssetManager am=ctx.getAssets(); String[] fs=am.list("backgrounds");
      if(fs==null) return out;
      for(String f:fs){ String l=f.toLowerCase();
        if(l.endsWith(".png")||l.endsWith(".jpg")||l.endsWith(".jpeg")||l.endsWith(".webp")) out.add(f); }
    }catch(Throwable ignored){}
    return out;
  }
  public List<File> listUser(){
    List<File> out=new ArrayList<>(); File d=userDir();
    File[] fs=d.listFiles(); if(fs==null) return out;
    for(File f:fs) if(f.isFile()) out.add(f); return out;
  }
  private File userDir(){ File d=new File(ctx.getFilesDir(),"bg"); if(!d.exists()) d.mkdirs(); return d; }
  public File importUri(Uri uri,String name){
    try{ InputStream in=ctx.getContentResolver().openInputStream(uri); if(in==null) return null;
      File dst=new File(userDir(),System.currentTimeMillis()+"_"+name);
      OutputStream o=new FileOutputStream(dst); byte[] b=new byte[8192]; int r;
      while((r=in.read(b))>0) o.write(b,0,r); o.close(); in.close(); return dst;
    }catch(Throwable t){ return null; }
  }
  public void del(File f){ if(f!=null&&f.exists()) f.delete(); }
  public void applyTo(View root){
    if(root==null) return;
    int t=type(); Drawable d=null;
    try{ switch(t){
      case BUILTIN: int i=Integer.parseInt(val()); if(i>=0&&i<BUILTIN.length) d=ContextCompat.getDrawable(ctx,BUILTIN[i]); break;
      case ASSET: { String n=val(); if(n!=null){ InputStream in=ctx.getAssets().open("backgrounds/"+n);
        Bitmap bm=BitmapFactory.decodeStream(in); in.close();
        if(bm!=null) d=new BitmapDrawable(ctx.getResources(),bm); } break; }
      case USER: { String p=val(); if(p!=null){ Bitmap bm=BitmapFactory.decodeFile(p);
        if(bm!=null) d=new BitmapDrawable(ctx.getResources(),bm); } break; }
    }}catch(Throwable ignored){}
    if(d!=null) root.setBackground(d); else root.setBackgroundColor(0xFFF2F4F8);
  }
}
