package com.handwrite.ime;
import android.content.Context; import android.graphics.*; import android.util.AttributeSet;
import android.view.*; import java.util.*;
public class HandWriteCanvas extends View {
  public interface L { void onDone(List<S> s); void onEmpty(); }
  public static class P { public final float x,y; public final long t; public P(float x,float y,long t){this.x=x;this.y=y;this.t=t;} }
  public static class S { public final List<P> pts = new ArrayList<>(); }
  private final List<S> strokes = new ArrayList<>();
  private final List<Path> paths = new ArrayList<>();
  private S cur; private Path curPath; private float lx,ly;
  private final Paint paint; private L listener; private long t0 = System.currentTimeMillis();
  public HandWriteCanvas(Context c){ this(c,null); }
  public HandWriteCanvas(Context c,AttributeSet a){
    super(c,a);
    paint = new Paint(Paint.ANTI_ALIAS_FLAG);
    paint.setColor(0xFF1B1B1F); paint.setStrokeWidth(dp(4.2f));
    paint.setStyle(Paint.Style.STROKE);
    paint.setStrokeCap(Paint.Cap.ROUND); paint.setStrokeJoin(Paint.Join.ROUND);
    setLayerType(LAYER_TYPE_HARDWARE,null);
  }
  private float dp(float v){ return v*getResources().getDisplayMetrics().density; }
  public void setListener(L l){ listener=l; }
  public void clear(){ strokes.clear(); paths.clear(); cur=null; curPath=null; invalidate(); if(listener!=null) listener.onEmpty(); }
  public void undo(){ if(strokes.isEmpty())return; strokes.remove(strokes.size()-1); paths.remove(paths.size()-1); invalidate();
    if(listener!=null){ if(strokes.isEmpty()) listener.onEmpty(); else listener.onDone(strokes); } }
  public List<S> getStrokes(){ return strokes; }
  @Override public boolean onTouchEvent(MotionEvent e){
    float x=e.getX(),y=e.getY(); long t=System.currentTimeMillis()-t0;
    switch(e.getActionMasked()){
      case MotionEvent.ACTION_DOWN:
        cur=new S(); cur.pts.add(new P(x,y,t)); strokes.add(cur);
        curPath=new Path(); curPath.moveTo(x,y); paths.add(curPath); lx=x; ly=y; invalidate(); return true;
      case MotionEvent.ACTION_MOVE:
        if(cur!=null){
          float mx=(lx+x)/2f, my=(ly+y)/2f;
          curPath.quadTo(lx,ly,mx,my); cur.pts.add(new P(x,y,t)); lx=x; ly=y; invalidate();
        } return true;
      case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
        if(curPath!=null&&cur!=null){ curPath.lineTo(x,y); cur.pts.add(new P(x,y,t)); }
        cur=null; curPath=null; invalidate();
        if(listener!=null&&!strokes.isEmpty()) listener.onDone(strokes);
        return true;
    }
    return super.onTouchEvent(e);
  }
  @Override protected void onDraw(Canvas c){ super.onDraw(c); for(Path p:paths) c.drawPath(p,paint); }
}
