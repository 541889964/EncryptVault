package com.handwrite.ime;
import android.content.*; import android.os.Bundle; import android.speech.*;
import java.util.*;
/** 高精度语音输入：FREE_FORM + partial + 多语言 + 离线优先 + 多候选 */
public class VoiceInputHelper {
  public interface Cb {
    void onReady(); void onPartial(String t);
    void onFinal(ArrayList<String> list); void onErr(String m);
  }
  private SpeechRecognizer rec; private final Context ctx; private boolean listening=false;
  public VoiceInputHelper(Context c){ ctx=c; }
  public boolean isListening(){ return listening; }
  public void start(Cb cb){
    if(!SpeechRecognizer.isRecognitionAvailable(ctx)){ cb.onErr("设备不支持语音识别"); return; }
    if(rec==null) rec=SpeechRecognizer.createSpeechRecognizer(ctx);
    if(listening){ try{ rec.stopListening(); }catch(Throwable ignored){} }
    Intent i=new Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH);
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL,RecognizerIntent.LANGUAGE_MODEL_FREE_FORM);
    i.putExtra(RecognizerIntent.EXTRA_PARTIAL_RESULTS,true);
    i.putExtra(RecognizerIntent.EXTRA_MAX_RESULTS,10);
    i.putExtra(RecognizerIntent.EXTRA_CALLING_PACKAGE,ctx.getPackageName());
    i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_COMPLETE_SILENCE_LENGTH_MILLIS,1200);
    i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_POSSIBLY_COMPLETE_SILENCE_LENGTH_MILLIS,1200);
    i.putExtra(RecognizerIntent.EXTRA_SPEECH_INPUT_MINIMUM_LENGTH_MILLIS,800);
    Locale loc=Locale.getDefault();
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE,loc.toLanguageTag());
    i.putExtra(RecognizerIntent.EXTRA_LANGUAGE_PREFERENCE,loc.toLanguageTag());
    i.putExtra("android.speech.extra.EXTRA_ADDITIONAL_LANGUAGES",
        new String[]{ loc.toLanguageTag(),"zh-CN","en-US" });
    try{ i.putExtra(RecognizerIntent.EXTRA_PREFER_OFFLINE,true); }catch(Throwable ignored){}
    rec.setRecognitionListener(new RecognitionListener(){
      public void onReadyForSpeech(Bundle p){ listening=true; cb.onReady(); }
      public void onBeginningOfSpeech(){}
      public void onRmsChanged(float r){}
      public void onBufferReceived(byte[] b){}
      public void onEndOfSpeech(){ listening=false; }
      public void onError(int e){ listening=false; cb.onErr(err(e)); }
      public void onResults(Bundle b){
        listening=false;
        ArrayList<String> l=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        cb.onFinal(l!=null?l:new ArrayList<>());
      }
      public void onPartialResults(Bundle b){
        ArrayList<String> l=b.getStringArrayList(SpeechRecognizer.RESULTS_RECOGNITION);
        if(l!=null&&!l.isEmpty()) cb.onPartial(l.get(0));
      }
      public void onEvent(int t,Bundle p){}
    });
    try{ rec.startListening(i); }catch(Throwable t){ cb.onErr("启动失败:"+t.getMessage()); }
  }
  public void stop(){ if(rec!=null&&listening){ try{ rec.stopListening(); }catch(Throwable ignored){} } listening=false; }
  public void release(){ if(rec!=null){ try{ rec.destroy(); }catch(Throwable ignored){} rec=null; } listening=false; }
  private String err(int e){
    switch(e){
      case SpeechRecognizer.ERROR_AUDIO: return "录音错误";
      case SpeechRecognizer.ERROR_INSUFFICIENT_PERMISSIONS: return "缺少录音权限";
      case SpeechRecognizer.ERROR_NETWORK: return "网络错误";
      case SpeechRecognizer.ERROR_NETWORK_TIMEOUT: return "网络超时";
      case SpeechRecognizer.ERROR_NO_MATCH: return "没听清，请再说一次";
      case SpeechRecognizer.ERROR_SPEECH_TIMEOUT: return "等待超时";
      default: return "识别失败("+e+")";
    }
  }
}
