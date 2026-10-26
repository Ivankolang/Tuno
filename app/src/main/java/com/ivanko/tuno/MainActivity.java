package com.ivanko.tuno;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
 final int green=0xff43ed90, bg=0xff0a100d;
 final Handler handler=new Handler(Looper.getMainLooper());
 volatile boolean listening=false, ticking=false;
 volatile int bpm=100, beats=4;
 TextView note, freq, guide, tempo, pulse;
 AudioRecord recorder;
 Thread listenThread, tickThread;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(dp(5),dp(8),dp(5),dp(8));return v;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.BLACK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));return b;}
 void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
 ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(12),dp(20),dp(25));root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
 add(root,label("TUNO",35,green));add(root,label("ТЮНЕР  •  МЕТРОНОМ",14,0xffa5b9aa));
 add(root,label("ХРОМАТИЧЕСКИЙ ТЮНЕР",18,green));
 note=label("—",75,Color.WHITE);add(root,note);
 freq=label("0.0 Hz",22,0xffb8c7be);add(root,freq);
 guide=label("Нажми «Включить тюнер»",16,0xffb8c7be);add(root,guide);
 Button start=button("Включить тюнер");add(root,start);start.setOnClickListener(v->{if(listening){stopTuner();start.setText("Включить тюнер");}else{if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}listening=true;start.setText("Остановить тюнер");listenThread=new Thread(this::listen);listenThread.start();}});
 add(root,label("МЕТРОНОМ",20,green));tempo=label("100 BPM",38,Color.WHITE);add(root,tempo);
 LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);root.addView(row);
 Button minus=button("−");Button plus=button("+");Button sig=button("4/4");
 for(Button x:new Button[]{minus,sig,plus})row.addView(x,new LinearLayout.LayoutParams(0,dp(55),1));
 minus.setOnClickListener(v->{bpm=Math.max(30,bpm-5);tempo.setText(bpm+" BPM");});
 plus.setOnClickListener(v->{bpm=Math.min(240,bpm+5);tempo.setText(bpm+" BPM");});
 sig.setOnClickListener(v->{beats=beats==4?3:beats==3?2:beats==2?6:4;sig.setText(beats+"/8".replace("/8",beats==6?"/8":"/4"));});
 pulse=label("●",40,0xff64796a);add(root,pulse);
 Button play=button("Запустить метроном");add(root,play);play.setOnClickListener(v->{ticking=!ticking;play.setText(ticking?"Остановить метроном":"Запустить метроном");if(ticking){tickThread=new Thread(this::tick);tickThread.start();}});
 }
 void stopTuner(){listening=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}
 void listen(){int rate=44100,n=8192;int size=Math.max(n*2,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Микрофон недоступен");short[] data=new short[n];recorder.startRecording();while(listening){int read=0;while(read<n&&listening){int got=recorder.read(data,read,n-read);if(got<=0)break;read+=got;}if(read<n)continue;double best=0;int lagBest=0;double power=0;for(short x:data)power+=x*(double)x;if(power/n<200000)continue;for(int lag=55;lag<550;lag++){double corr=0;for(int i=0;i<n-lag;i+=2)corr+=data[i]*(double)data[i+lag];if(corr>best){best=corr;lagBest=lag;}}if(lagBest==0)continue;double hz=(double)rate/lagBest;if(hz<75||hz>850)continue;int midi=(int)Math.round(69+12*Math.log(hz/440)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};String name=names[(midi%12+12)%12]+(midi/12-1);double target=440*Math.pow(2,(midi-69)/12.0);double cents=1200*Math.log(hz/target)/Math.log(2);final double shown=hz;final String nn=name;final String message=Math.abs(cents)<8?"✓ Струна настроена":cents<0?"↑ Подтяни струну":"↓ Ослабь струну";handler.post(()->{note.setText(nn);freq.setText(String.format(Locale.US,"%.1f Hz",shown));guide.setText(message);});}}catch(Exception e){handler.post(()->guide.setText("Ошибка микрофона: "+e.getMessage()));}finally{if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}}}
 void tick(){int beat=0;while(ticking){int current=beat++%beats;handler.post(()->pulse.setText(current==0?"● 1":"● "+(current+1)));int sr=22050;int len=sr/16;short[] pcm=new short[len];double phase=0;double hz=current==0?1200:850;for(int i=0;i<len;i++){phase+=2*Math.PI*hz/sr;pcm[i]=(short)(Math.sin(phase)*Math.exp(-i/(sr*.012))*15000);}try{AudioTrack t=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,len*2,AudioTrack.MODE_STATIC);t.write(pcm,0,len);t.play();Thread.sleep(Math.max(10,60000/bpm));t.stop();t.release();}catch(Exception ignored){}}handler.post(()->pulse.setText("●"));}
 protected void onDestroy(){stopTuner();ticking=false;super.onDestroy();}
}
