package com.handwrite.ime;
import android.content.*; import android.graphics.Color; import android.graphics.PorterDuff;
import android.graphics.drawable.*; import android.inputmethodservice.InputMethodService;
import android.os.*; import android.text.TextUtils; import android.util.TypedValue;
import android.view.*; import android.view.animation.*;
import android.view.inputmethod.*; import android.widget.*;
import androidx.core.content.ContextCompat;
import com.google.mlkit.common.model.*; import com.google.mlkit.vision.digitalink.*;
import java.util.*;
public class HandWriteIME extends InputMethodService implements HandWriteCanvas.L {
  private LinearLayout root, bottomBar, candidateBar, panelHost, editRow, topBar;
  private FrameLayout canvasHost; private HandWriteCanvas canvas;
  private TextView status, voicePreview;
  private ClipboardHistory clip; private PluginManager pluginMgr; private BackgroundManager bgMgr;
  private PluginManager.P currentPlugin; private VoiceInputHelper voice;
  private DigitalInkRecognizer rec; private volatile boolean ready=false;
  private final Handler ui=new Handler(Looper.getMainLooper());
  private Runnable pendingRec;
  @Override public void onCreate(){
    super.onCreate();
    clip=new ClipboardHistory(this);
    pluginMgr=new PluginManager(this);
    bgMgr=new BackgroundManager(this);
    currentPlugin=pluginMgr.def();
    voice=new VoiceInputHelper(this);
    initRec();
  }
  private void initRec(){
    try{
      DigitalInkRecognitionModelIdentifier id=DigitalInkRecognitionModelIdentifier.fromLanguageTag("zh-Hani");
      if(id==null) return;
      DigitalInkRecognitionModel m=DigitalInkRecognitionModel.builder(id).build();
      RemoteModelManager.getInstance().download(m,new DownloadConditions.Builder().build())
        .addOnSuccessListener(a->{ ready=true;
          rec=DigitalInkRecognition.getClient(DigitalInkRecognizerOptions.builder(m).build());
          ui.post(()->{ if(status!=null) status.setText("手写·语音"); });
        }).addOnFailureListener(e->ui.post(()->{ if(status!=null) status.setText("模型加载失败"); }));
    }catch(Throwable ignored){}
  }
  // ============ UI ============
  @Override public View onCreateInputView(){
    root=new LinearLayout(this); root.setOrientation(LinearLayout.VERTICAL);
    root.setBackgroundColor(0xFFF2F4F8);
    topBar=new LinearLayout(this); topBar.setOrientation(LinearLayout.HORIZONTAL);
    topBar.setGravity(Gravity.CENTER_VERTICAL); topBar.setPadding(dp(8),dp(4),dp(8),dp(4));
    topBar.setBackgroundColor(0xF5FFFFFF);
    status=new TextView(this); status.setText("手写·语音"); status.setTextSize(12);
    status.setTextColor(0xFF6B6F78); status.setPadding(dp(10),0,0,0);
    topBar.addView(status,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
    topBar.addView(topBtn("🗑 清空",v->canvasClear()));
    topBar.addView(topBtn("↩ 撤销",v->{ if(canvas!=null) canvas.undo(); }));
    topBar.addView(topBtn("🎨 背景",v->cycleBg()));
    root.addView(topBar,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(38)));
    canvasHost=new FrameLayout(this);
    canvasHost.setBackground(ContextCompat.getDrawable(this,R.drawable.canvas_bg));
    canvas=new HandWriteCanvas(this); canvas.setListener(this);
    canvasHost.addView(canvas,new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT));
    voicePreview=new TextView(this); voicePreview.setTextSize(18);
    voicePreview.setTextColor(0xFF4A7DFF); voicePreview.setGravity(Gravity.CENTER);
    voicePreview.setVisibility(View.GONE); voicePreview.setPadding(dp(12),dp(12),dp(12),dp(12));
    voicePreview.setBackgroundColor(0xE6FFFFFF);
    canvasHost.addView(voicePreview,new FrameLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT));
    root.addView(canvasHost,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(200)));
    HorizontalScrollView candScroll=new HorizontalScrollView(this);
    candScroll.setHorizontalScrollBarEnabled(false); candScroll.setBackgroundColor(0xF2FFFFFF);
    candidateBar=new LinearLayout(this); candidateBar.setOrientation(LinearLayout.HORIZONTAL);
    candidateBar.setPadding(dp(4),dp(2),dp(4),dp(2)); candidateBar.setGravity(Gravity.CENTER_VERTICAL);
    candScroll.addView(candidateBar);
    root.addView(candScroll,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(42)));
    bottomBar=new LinearLayout(this); bottomBar.setOrientation(LinearLayout.HORIZONTAL);
    bottomBar.setBackgroundColor(0xF5FFFFFF); bottomBar.setPadding(dp(4),dp(4),dp(4),dp(6));
    root.addView(bottomBar,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(52)));
    panelHost=new LinearLayout(this); panelHost.setOrientation(LinearLayout.VERTICAL);
    panelHost.setVisibility(View.GONE); panelHost.setBackground(ContextCompat.getDrawable(this,R.drawable.panel_bg));
    root.addView(panelHost,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,dp(240)));
    applyPlugin(currentPlugin);
    bgMgr.applyTo(root);
    root.addOnAttachStateChangeListener(new View.OnAttachStateChangeListener(){
      public void onViewAttachedToWindow(View v){
        v.setAlpha(0f); v.setTranslationY(dp(24));
        v.animate().alpha(1f).translationY(0).setDuration(200)
          .setInterpolator(new DecelerateInterpolator()).start();
      }
      public void onViewDetachedFromWindow(View v){}
    });
    return root;
  }
  private Button topBtn(String t,View.OnClickListener l){
    Button b=new Button(this); b.setText(t); b.setTextSize(11); b.setAllCaps(false);
    b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(8),0,dp(8),0);
    b.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
    b.setTextColor(0xFF6B6F78); b.setOnClickListener(l); return b;
  }
  private View makeKey(PluginManager.K def){
    final String label=def.label, action=def.action; final boolean accent=def.accent;
    LinearLayout wrap=new LinearLayout(this); wrap.setOrientation(LinearLayout.VERTICAL);
    wrap.setGravity(Gravity.CENTER);
    TextView tv=new TextView(this); tv.setText(label); tv.setTextSize(accent?18:15);
    tv.setGravity(Gravity.CENTER); tv.setTextColor(accent?0xFFFFFFFF:0xFF1B1B1F);
    tv.setBackground(ContextCompat.getDrawable(this,accent?R.drawable.key_accent_bg:R.drawable.key_bg));
    tv.setPadding(dp(4),dp(4),dp(4),dp(4));
    wrap.addView(tv,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,LinearLayout.LayoutParams.MATCH_PARENT));
    tv.setOnTouchListener((v,e)->{
      switch(e.getActionMasked()){
        case MotionEvent.ACTION_DOWN:
          v.animate().scaleX(0.92f).scaleY(0.92f).setDuration(70).start();
          v.performHapticFeedback(HapticFeedbackConstants.KEYBOARD_TAP);
          v.setPressed(true); return true;
        case MotionEvent.ACTION_UP: case MotionEvent.ACTION_CANCEL:
          v.animate().scaleX(1f).scaleY(1f).setDuration(100)
            .setInterpolator(new OvershootInterpolator(3f)).start();
          v.setPressed(false);
          if(e.getActionMasked()==MotionEvent.ACTION_UP){
            float x=e.getX(),y=e.getY();
            if(x>=0&&y>=0&&x<=v.getWidth()&&y<=v.getHeight()) handle(action);
          } return true;
      }
      return false;
    });
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.MATCH_PARENT,def.weight);
    lp.setMargins(dp(3),0,dp(3),0); wrap.setLayoutParams(lp); return wrap;
  }
  private void applyPlugin(PluginManager.P p){
    currentPlugin=p; if(bottomBar==null) return;
    bottomBar.removeAllViews();
    for(PluginManager.K k:p.keys) bottomBar.addView(makeKey(k));
    if(canvasHost!=null){
      ViewGroup.LayoutParams lp=canvasHost.getLayoutParams();
      lp.height=dp((int)(320*p.canvasRatio)); canvasHost.setLayoutParams(lp);
    }
    try{
      if(topBar!=null) topBar.setBackgroundColor(Color.parseColor(p.barBg));
      if(candidateBar!=null) candidateBar.setBackgroundColor(Color.parseColor(p.barBg));
      if(bottomBar!=null) bottomBar.setBackgroundColor(Color.parseColor(p.barBg));
    }catch(Throwable ignored){}
    bgMgr.applyTo(root);
  }
  // ============ 动作 ============
  private void handle(String a){
    if(a==null) return;
    InputConnection ic=getCurrentInputConnection();
    if(a.startsWith("commit:")){ if(ic!=null) ic.commitText(a.substring(7),1); return; }
    switch(a){
      case "space": if(ic!=null) ic.commitText(" ",1); break;
      case "backspace": if(ic!=null){ CharSequence s=ic.getSelectedText(0);
        if(s!=null&&s.length()>0) ic.commitText("",1); else ic.deleteSurroundingText(1,0); } break;
      case "enter": {
        EditorInfo ei=getCurrentInputEditorInfo();
        int t=ei==null?0:(ei.imeOptions&EditorInfo.IME_MASK_ACTION);
        if(ic==null) break;
        if(t==EditorInfo.IME_ACTION_NONE||t==EditorInfo.IME_ACTION_UNSPECIFIED){
          ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_ENTER));
          ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_ENTER));
        } else ic.performEditorAction(t);
        break;
      }
      case "clear": canvasClear(); break;
      case "undo": if(canvas!=null) canvas.undo(); break;
      case "voice": toggleVoice(); break;
      case "clipboard": showClipPanel(); break;
      case "edit": showEditPanel(); break;
      case "plugin": showPluginPanel(); break;
    }
  }
  private void canvasClear(){ if(canvas!=null) canvas.clear(); if(candidateBar!=null) candidateBar.removeAllViews(); }
  // ============ 手写识别 ============
  @Override public void onDone(List<HandWriteCanvas.S> s){
    if(pendingRec!=null) ui.removeCallbacks(pendingRec);
    pendingRec=this::recognize; ui.postDelayed(pendingRec,420);
  }
  @Override public void onEmpty(){ if(candidateBar!=null) candidateBar.removeAllViews(); }
  private void recognize(){
    if(!ready||rec==null){ toast("手写模型未就绪"); return; }
    List<HandWriteCanvas.S> ss=canvas.getStrokes(); if(ss.isEmpty()) return;
    Ink.Builder ib=Ink.builder();
    for(HandWriteCanvas.S s:ss){
      if(s.pts.size()<2) continue;
      Ink.Stroke.Builder sb=Ink.Stroke.builder();
      for(HandWriteCanvas.P p:s.pts) sb.addPoint(Ink.Point.create(p.x,p.y,p.t));
      ib.addStroke(sb.build());
    }
    try{
      rec.recognize(ib.build()).addOnSuccessListener(r->{
        if(candidateBar==null) return; candidateBar.removeAllViews();
        List<RecognitionCandidate> cs=r.getCandidates(); int n=Math.min(cs.size(),8);
        for(int i=0;i<n;i++){
          final String t=cs.get(i).getText();
          Button b=new Button(this); b.setText(t); b.setTextSize(18); b.setAllCaps(false);
          b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(14),0,dp(14),0);
          b.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
          b.setOnClickListener(v->commitCandidate(t));
          b.setAlpha(0f); b.setScaleX(0.85f); b.setScaleY(0.85f);
          candidateBar.addView(b);
          b.animate().alpha(1f).scaleX(1f).scaleY(1f).setStartDelay(i*20L).setDuration(140).start();
        }
      }).addOnFailureListener(e->toast("识别失败"));
    }catch(Throwable t){ toast("识别异常"); }
  }
  private void commitCandidate(String t){
    InputConnection ic=getCurrentInputConnection();
    if(ic!=null&&!TextUtils.isEmpty(t)) ic.commitText(t,1);
    canvasClear();
  }
  // ============ 语音 ============
  private void toggleVoice(){
    if(voice.isListening()){ voice.stop(); voicePreview.setVisibility(View.GONE); return; }
    voicePreview.setVisibility(View.VISIBLE); voicePreview.setText("🎤 正在聆听…");
    voicePreview.setAlpha(0f); voicePreview.animate().alpha(1f).setDuration(150).start();
    voice.start(new VoiceInputHelper.Cb(){
      public void onReady(){ ui.post(()->voicePreview.setText("🎤 正在聆听…")); }
      public void onPartial(String t){ ui.post(()->voicePreview.setText("🎤 "+t)); }
      public void onFinal(ArrayList<String> cs){
        ui.post(()->{
          voicePreview.animate().alpha(0f).setDuration(180)
            .withEndAction(()->voicePreview.setVisibility(View.GONE)).start();
          if(cs.isEmpty()){ toast("没听清，请再说一遍"); return; }
          InputConnection ic=getCurrentInputConnection();
          if(ic!=null) ic.commitText(cs.get(0),1);
          if(candidateBar!=null){ candidateBar.removeAllViews();
            for(int i=1;i<Math.min(cs.size(),6);i++){
              final String t=cs.get(i);
              Button b=new Button(HandWriteIME.this); b.setText(t); b.setTextSize(16); b.setAllCaps(false);
              b.setBackground(ContextCompat.getDrawable(HandWriteIME.this,R.drawable.key_bg));
              b.setOnClickListener(v->commitCandidate(t)); candidateBar.addView(b);
            } }
        });
      }
      public void onErr(String m){
        ui.post(()->{
          voicePreview.animate().alpha(0f).setDuration(150)
            .withEndAction(()->voicePreview.setVisibility(View.GONE)).start();
          toast(m);
        });
      }
    });
  }
  // ============ 文字编辑面板 ============
  private void showEditPanel(){
    if(panelHost==null) return;
    if(panelHost.getVisibility()==View.VISIBLE&&"edit".equals(panelHost.getTag())){ hidePanel(); return; }
    panelHost.removeAllViews(); panelHost.setTag("edit"); showPanel();
    TextView t=new TextView(this); t.setTextSize(13); t.setTextColor(0xFF1B1B1F);
    t.setText("文字编辑"); t.setPadding(dp(14),dp(10),dp(14),dp(4)); panelHost.addView(t);
    // 第一行：全选 复制 剪切 粘贴 删除 开始选择
    panelHost.addView(editRow(new String[][]{
      {"全选","sel_all"},{"复制","copy"},{"剪切","cut"},
      {"粘贴","paste"},{"删除","del"},{"开始选择","sel_start"}
    }));
    // 第二行：上下左右 + 文首 + 文末
    panelHost.addView(editRow(new String[][]{
      {"←","left"},{"→","right"},{"↑","up"},{"↓","down"},
      {"⏮ 文首","home"},{"⏭ 文末","end"}
    }));
    Button close=new Button(this); close.setText("收起"); close.setAllCaps(false);
    close.setOnClickListener(v->hidePanel()); panelHost.addView(close);
    panelHost.startAnimation(AnimationUtils.loadAnimation(this,R.anim.panel_in));
  }
  private LinearLayout editRow(String[][] items){
    LinearLayout row=new LinearLayout(this); row.setOrientation(LinearLayout.HORIZONTAL);
    row.setPadding(dp(4),dp(2),dp(4),dp(2));
    for(String[] it:items){
      final String label=it[0], act=it[1];
      Button b=new Button(this); b.setText(label); b.setTextSize(12); b.setAllCaps(false);
      b.setMinWidth(0); b.setMinimumWidth(0); b.setPadding(dp(6),0,dp(6),0);
      b.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
      b.setOnClickListener(v->doEdit(act));
      LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f);
      lp.setMargins(dp(2),0,dp(2),0); row.addView(b,lp);
    }
    return row;
  }
  private void doEdit(String act){
    InputConnection ic=getCurrentInputConnection(); if(ic==null) return;
    switch(act){
      case "sel_all":   ic.performContextMenuAction(android.R.id.selectAll); break;
      case "copy":      ic.performContextMenuAction(android.R.id.copy); break;
      case "cut":       ic.performContextMenuAction(android.R.id.cut); break;
      case "paste":     ic.performContextMenuAction(android.R.id.paste); break;
      case "del": {     CharSequence s=ic.getSelectedText(0);
                        if(s!=null&&s.length()>0) ic.commitText("",1);
                        else ic.deleteSurroundingText(1,0); break; }
      case "sel_start": ic.performContextMenuAction(android.R.id.startSelectingText); break;
      case "left":  ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_LEFT));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_LEFT)); break;
      case "right": ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_RIGHT));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_RIGHT)); break;
      case "up":    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_UP));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_UP)); break;
      case "down":  ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_DPAD_DOWN));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_DPAD_DOWN)); break;
      case "home":  ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MOVE_HOME));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MOVE_HOME)); break;
      case "end":   ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_DOWN,KeyEvent.KEYCODE_MOVE_END));
                    ic.sendKeyEvent(new KeyEvent(KeyEvent.ACTION_UP,KeyEvent.KEYCODE_MOVE_END)); break;
    }
  }
  // ============ 剪贴板面板 ============
  private void showClipPanel(){
    if(panelHost==null) return;
    if(panelHost.getVisibility()==View.VISIBLE&&"clip".equals(panelHost.getTag())){ hidePanel(); return; }
    panelHost.removeAllViews(); panelHost.setTag("clip"); showPanel();
    LinearLayout h=new LinearLayout(this); h.setOrientation(LinearLayout.HORIZONTAL);
    h.setGravity(Gravity.CENTER_VERTICAL); h.setPadding(dp(12),dp(8),dp(8),dp(8));
    TextView tv=new TextView(this); tv.setTextSize(13); tv.setTextColor(0xFF1B1B1F);
    tv.setText("剪贴板 · "+clip.total()+" / 100000 字");
    h.addView(tv,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
    Button c=new Button(this); c.setText("清空"); c.setTextSize(11); c.setAllCaps(false);
    c.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
    c.setOnClickListener(v->{ clip.clear(); showClipPanel(); }); h.addView(c);
    Button x=new Button(this); x.setText("收起"); x.setTextSize(11); x.setAllCaps(false);
    x.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
    x.setOnClickListener(v->hidePanel()); h.addView(x);
    panelHost.addView(h);
    ScrollView sc=new ScrollView(this); LinearLayout list=new LinearLayout(this);
    list.setOrientation(LinearLayout.VERTICAL); sc.addView(list);
    List<String> items=clip.all();
    if(items.isEmpty()){
      TextView e=new TextView(this); e.setText("（暂无历史，复制任意文本后会出现）");
      e.setTextSize(13); e.setTextColor(0xFF6B6F78); e.setPadding(dp(14),dp(16),dp(14),dp(16));
      list.addView(e);
    } else {
      for(int i=0;i<items.size();i++){
        final String s=items.get(i); final int idx=i;
        TextView it=new TextView(this);
        String pv=s.length()>140?s.substring(0,140)+"…":s;
        it.setText(pv+"\n["+s.length()+" 字]"); it.setTextSize(13);
        it.setTextColor(0xFF1B1B1F); it.setPadding(dp(14),dp(10),dp(14),dp(10));
        it.setBackgroundColor(i%2==0?0xFFFFFFFF:0xFFF2F4F8);
        it.setOnClickListener(v->{ InputConnection ic=getCurrentInputConnection();
          if(ic!=null) ic.commitText(s,1); });
        it.setOnLongClickListener(v->{ clip.remove(idx); showClipPanel(); return true; });
        list.addView(it);
      }
    }
    panelHost.addView(sc,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
    panelHost.startAnimation(AnimationUtils.loadAnimation(this,R.anim.panel_in));
  }
  // ============ 插件面板 ============
  private void showPluginPanel(){
    if(panelHost==null) return;
    if(panelHost.getVisibility()==View.VISIBLE&&"plugin".equals(panelHost.getTag())){ hidePanel(); return; }
    panelHost.removeAllViews(); panelHost.setTag("plugin"); showPanel();
    TextView t=new TextView(this); t.setTextSize(13); t.setTextColor(0xFF1B1B1F);
    t.setText("已导入插件"); t.setPadding(dp(14),dp(10),dp(14),dp(4)); panelHost.addView(t);
    TextView tip=new TextView(this); tip.setTextSize(11); tip.setTextColor(0xFF6B6F78);
    tip.setText("去 App 设置页「导入插件 JSON」"); tip.setPadding(dp(14),0,dp(14),dp(8));
    panelHost.addView(tip);
    ScrollView sc=new ScrollView(this); LinearLayout row=new LinearLayout(this);
    row.setOrientation(LinearLayout.VERTICAL); sc.addView(row);
    Button d=new Button(this); d.setText("恢复默认布局"); d.setAllCaps(false);
    d.setOnClickListener(v->{ applyPlugin(pluginMgr.def()); toast("已恢复默认"); }); row.addView(d);
    List<File> ps=pluginMgr.list();
    if(ps.isEmpty()){
      TextView e=new TextView(this); e.setText("（还没插件）"); e.setTextSize(12);
      e.setTextColor(0xFF6B6F78); e.setPadding(dp(14),dp(10),dp(14),dp(10)); row.addView(e);
    } else for(File f:ps){
      LinearLayout it=new LinearLayout(this); it.setOrientation(LinearLayout.HORIZONTAL);
      it.setGravity(Gravity.CENTER_VERTICAL); it.setPadding(dp(14),dp(6),dp(8),dp(6));
      TextView nm=new TextView(this); nm.setText(f.getName()); nm.setTextSize(13);
      nm.setTextColor(0xFF1B1B1F);
      it.addView(nm,new LinearLayout.LayoutParams(0,LinearLayout.LayoutParams.WRAP_CONTENT,1f));
      Button use=new Button(this); use.setText("应用"); use.setTextSize(11); use.setAllCaps(false);
      use.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
      use.setOnClickListener(v->{ applyPlugin(pluginMgr.load(f)); toast("已应用 "+f.getName()); });
      it.addView(use);
      Button del=new Button(this); del.setText("删除"); del.setTextSize(11); del.setAllCaps(false);
      del.setBackground(ContextCompat.getDrawable(this,R.drawable.key_bg));
      del.setOnClickListener(v->{ pluginMgr.del(f); showPluginPanel(); }); it.addView(del);
      row.addView(it);
    }
    panelHost.addView(sc,new LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT,0,1f));
    panelHost.startAnimation(AnimationUtils.loadAnimation(this,R.anim.panel_in));
  }
  private void showPanel(){
    panelHost.setVisibility(View.VISIBLE); panelHost.setAlpha(0f); panelHost.setTranslationY(dp(30));
    panelHost.animate().alpha(1f).translationY(0).setDuration(200)
      .setInterpolator(new DecelerateInterpolator()).start();
  }
  private void hidePanel(){
    if(panelHost==null) return;
    panelHost.animate().alpha(0f).translationY(dp(30)).setDuration(160)
      .withEndAction(()->panelHost.setVisibility(View.GONE)).start();
  }
  // ============ 背景循环 ============
  private void cycleBg(){
    int t=bgMgr.type(); String v=bgMgr.val();
    List<String> as=bgMgr.listAssets(); List<File> us=bgMgr.listUser();
    int total=1+BackgroundManager.BUILTIN_NAMES.length+as.size()+us.size();
    int cur=0;
    if(t==BackgroundManager.BUILTIN&&v!=null){ try{ cur=1+Integer.parseInt(v); }catch(Throwable ignored){} }
    else if(t==BackgroundManager.ASSET&&v!=null) cur=1+BackgroundManager.BUILTIN_NAMES.length+as.indexOf(v);
    else if(t==BackgroundManager.USER&&v!=null){ for(int i=0;i<us.size();i++) if(us.get(i).getAbsolutePath().equals(v)){
      cur=1+BackgroundManager.BUILTIN_NAMES.length+as.size()+i; break; } }
    int next=(cur+1)%total;
    if(next==0){ bgMgr.clear(); toast("背景：无"); }
    else if(next<=BackgroundManager.BUILTIN_NAMES.length){
      int i=next-1; bgMgr.set(BackgroundManager.BUILTIN,String.valueOf(i));
      toast("背景："+BackgroundManager.BUILTIN_NAMES[i]);
    } else if(next<=BackgroundManager.BUILTIN_NAMES.length+as.size()){
      int i=next-1-BackgroundManager.BUILTIN_NAMES.length;
      bgMgr.set(BackgroundManager.ASSET,as.get(i)); toast("背景："+as.get(i));
    } else {
      int i=next-1-BackgroundManager.BUILTIN_NAMES.length-as.size();
      bgMgr.set(BackgroundManager.USER,us.get(i).getAbsolutePath());
      toast("背景："+us.get(i).getName());
    }
    bgMgr.applyTo(root);
  }
  // ============ 生命周期 ============
  @Override public void onStartInputView(EditorInfo info,boolean restarting){
    super.onStartInputView(info,restarting);
    try{
      ClipboardManager cm=(ClipboardManager)getSystemService(Context.CLIPBOARD_SERVICE);
      if(cm!=null&&cm.hasPrimaryClip()){
        ClipData cd=cm.getPrimaryClip();
        if(cd!=null&&cd.getItemCount()>0){
          CharSequence cs=cd.getItemAt(0).coerceToText(this);
          if(cs!=null&&cs.length()>0) clip.add(cs.toString());
        }
      }
    }catch(Throwable ignored){}
  }
  @Override public void onFinishInput(){
    super.onFinishInput();
    canvasClear(); hidePanel();
    if(voice!=null&&voice.isListening()) voice.stop();
  }
  @Override public void onDestroy(){
    super.onDestroy();
    if(voice!=null) voice.release();
  }
  private int dp(int v){ return (int)TypedValue.applyDimension(TypedValue.COMPLEX_UNIT_DIP,v,getResources().getDisplayMetrics()); }
  private void toast(String s){ ui.post(()->Toast.makeText(HandWriteIME.this,s,Toast.LENGTH_SHORT).show()); }
}
