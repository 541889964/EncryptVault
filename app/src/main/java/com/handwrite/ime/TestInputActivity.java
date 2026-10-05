package com.handwrite.ime;
import android.os.Bundle; import android.view.Gravity; import android.view.ViewGroup;
import android.widget.*; import androidx.annotation.Nullable;
import androidx.appcompat.app.AppCompatActivity;
public class TestInputActivity extends AppCompatActivity {
  @Override protected void onCreate(@Nullable Bundle s){
    super.onCreate(s);
    LinearLayout r=new LinearLayout(this); r.setOrientation(LinearLayout.VERTICAL);
    int pad=(int)(getResources().getDisplayMetrics().density*16);
    r.setPadding(pad,pad,pad,pad);
    TextView t=new TextView(this); t.setText("点输入框调起手写·语音输入法"); r.addView(t);
    EditText e=new EditText(this); e.setHint("在这里随便写点什么…"); e.setMinLines(8);
    e.setGravity(Gravity.TOP|Gravity.START);
    LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(
      LinearLayout.LayoutParams.MATCH_PARENT,0,1f); lp.topMargin=pad;
    r.addView(e,lp);
    setContentView(r);
  }
}
