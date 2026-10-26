package com.ivanko.tuno;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.*;
import android.os.*;
import android.view.*;
import android.widget.*;
import java.util.Locale;

public class MainActivity extends Activity {
 final int green=0xff39db8a, bg=0xff0c0e11;
 final Handler handler=new Handler(Looper.getMainLooper());
 volatile boolean listening=false, ticking=false;
 volatile int bpm=100, beats=4;
 TextView note, freq, guide, tempo, pulse;
 Button tunerStart;
 boolean tunerTabActive=true;
 Gauge gauge;
 BeatLine beatLine;
 AudioRecord recorder;
 Thread listenThread, tickThread;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(dp(5),dp(8),dp(5),dp(8));return v;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.BLACK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));return b;}
 void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
 LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);setContentView(root);
 LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(tabs);Button tunerTab=button("Тюнер");Button metroTab=button("Метроном");tabs.addView(tunerTab,new LinearLayout.LayoutParams(0,dp(56),1));tabs.addView(metroTab,new LinearLayout.LayoutParams(0,dp(56),1));LinearLayout tunerPage=new LinearLayout(this);tunerPage.setOrientation(1);root.addView(tunerPage,new LinearLayout.LayoutParams(-1,0,1));LinearLayout metroPage=new LinearLayout(this);metroPage.setOrientation(1);root.addView(metroPage,new LinearLayout.LayoutParams(-1,0,1));metroPage.setVisibility(View.GONE);tunerTab.setOnClickListener(v->{tunerTabActive=true;tunerPage.setVisibility(View.VISIBLE);metroPage.setVisibility(View.GONE);startTunerIfAllowed();});metroTab.setOnClickListener(v->{tunerTabActive=false;tunerPage.setVisibility(View.GONE);metroPage.setVisibility(View.VISIBLE);stopTuner();});
 add(tunerPage,label("CHROMATIC  •  STANDARD",14,0xffa9b1b7));
 gauge=new Gauge();tunerPage.addView(gauge,new LinearLayout.LayoutParams(-1,dp(235)));
 note=label("—",75,Color.WHITE);add(tunerPage,note);
 freq=label("0.0 Hz",22,0xffb8c7be);add(tunerPage,freq);
 guide=label("Нажми «Включить тюнер»",16,0xffb8c7be);add(tunerPage,guide);
 tunerStart=button("Включить тюнер");add(tunerPage,tunerStart);tunerStart.setOnClickListener(v->{if(listening){stopTuner();tunerStart.setText("Включить тюнер");}else startTunerIfAllowed();});


 beatLine=new BeatLine();metroPage.addView(beatLine,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout tempoRow=new LinearLayout(this);tempoRow.setGravity(Gravity.CENTER_VERTICAL);tempoRow.setBackgroundColor(0xff202020);metroPage.addView(tempoRow,new LinearLayout.LayoutParams(-1,dp(104)));
 Button minus=button("⊖");Button plus=button("⊕");for(Button btn:new Button[]{minus,plus}){btn.setTextSize(27);btn.setTextColor(0xffa5a5a5);btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff202020));}
 tempo=label("120",65,Color.WHITE);tempoRow.addView(minus,new LinearLayout.LayoutParams(dp(72),-1));tempoRow.addView(tempo,new LinearLayout.LayoutParams(0,-1,1));tempoRow.addView(plus,new LinearLayout.LayoutParams(dp(72),-1));
 bpm=120;minus.setOnClickListener(v->{bpm=Math.max(20,bpm-1);tempo.setText(""+bpm);});plus.setOnClickListener(v->{bpm=Math.min(240,bpm+1);tempo.setText(""+bpm);});
 HorizontalScrollView presetsScroll=new HorizontalScrollView(this);presetsScroll.setHorizontalScrollBarEnabled(false);presetsScroll.setBackgroundColor(0xff252727);metroPage.addView(presetsScroll,new LinearLayout.LayoutParams(-1,dp(51)));
 LinearLayout presets=new LinearLayout(this);presetsScroll.addView(presets);
 String[] presetNames={"Largo","Adagio","Andante","Moderato","Allegro","Presto","Prestissimo"};int[] presetBpms={45,66,84,108,132,176,208};
 for(int i=0;i<presetNames.length;i++){final int v=presetBpms[i];Button preset=button(presetNames[i]);preset.setAllCaps(false);preset.setTextSize(13);preset.setTextColor(v==132?Color.WHITE:0xff858585);preset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff252727));presets.addView(preset,new LinearLayout.LayoutParams(dp(92),dp(49)));preset.setOnClickListener(w->{bpm=v;tempo.setText(""+bpm);});}
 LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(7),dp(20),dp(7),dp(20));controls.setBackgroundColor(0xff222222);metroPage.addView(controls);
 Button play=button("Start"),stop=button("Stop");play.setTextSize(21);stop.setTextSize(21);play.setTextColor(0xffeeeeee);stop.setTextColor(Color.WHITE);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff777777));stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffcf2634));
 LinearLayout.LayoutParams cp1=new LinearLayout.LayoutParams(0,dp(62),1);cp1.setMargins(0,0,dp(8),0);LinearLayout.LayoutParams cp2=new LinearLayout.LayoutParams(0,dp(62),1);cp2.setMargins(dp(8),0,0,0);controls.addView(play,cp1);controls.addView(stop,cp2);
 pulse=label("",12,green);pulse.setVisibility(View.GONE);metroPage.addView(pulse);
 play.setOnClickListener(v->{if(!ticking){ticking=true;tickThread=new Thread(this::tick);tickThread.start();}});
 stop.setOnClickListener(v->{ticking=false;beatLine.invalidate();});


 }
 
 class Gauge extends View {
  Paint p=new Paint(3);float cents=0,displayedCents=0;
  Gauge(){super(MainActivity.this);setBackgroundColor(0xff101215);}
  protected void onDraw(Canvas c){super.onDraw(c);displayedCents+=(cents-displayedCents)*.14f;if(Math.abs(cents-displayedCents)>.08f)postInvalidateDelayed(16);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h*.92f,r=Math.min(w*.43f,h*.73f);
   p.setTextAlign(Paint.Align.CENTER);p.setStrokeWidth(dp(1));p.setTextSize(dp(13));
   for(int i=-30;i<=30;i++){double a=Math.toRadians(-90+i*1.7);float x=(float)Math.cos(a),y=(float)Math.sin(a);p.setColor(i==0?0xffe4e8e9:0xff657177);p.setStrokeWidth(i%5==0?dp(2):dp(1));c.drawLine(cx+x*r,cy+y*r,cx+x*(r-(i%5==0?dp(22):dp(12))),cy+y*(r-(i%5==0?dp(22):dp(12))),p);
    if(i%10==0){p.setColor(0xffaab6ba);p.setTextSize(dp(11));c.drawText(""+i,cx+x*(r+dp(17)),cy+y*(r+dp(17))+dp(4),p);}}
   double a=Math.toRadians(-90+Math.max(-30,Math.min(30,displayedCents))*1.7);p.setColor(0xff09a9cc);p.setStrokeWidth(dp(2));c.drawLine(cx,cy,cx+(float)Math.cos(a)*r*1.07f,cy+(float)Math.sin(a)*r*1.07f,p);
   p.setColor(0xff60696c);p.setTextSize(dp(12));c.drawText("CENTS",cx,dp(32),p);
  }
 }
 class BeatLine extends View{
  Paint p=new Paint(3);int position=0;long beatStart=0;
  BeatLine(){super(MainActivity.this);setBackgroundColor(0xff101215);}
  protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f;if(ticking){float progress=Math.min(1f,(android.os.SystemClock.elapsedRealtime()-beatStart)/(60000f/bpm));float sway=(float)Math.sin(progress*Math.PI);x+=((position%2==0)?1:-1)*sway*getWidth()*.30f;postInvalidateDelayed(16);}p.setColor(0xff1ab9e6);p.setStrokeWidth(dp(6));c.drawLine(x,0,x,getHeight(),p);}
 }
 void startTunerIfAllowed(){if(listening)return;if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}listening=true;tunerStart.setText("Остановить тюнер");guide.setText("Слушаю гитару…");listenThread=new Thread(this::listen);listenThread.start();}
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&tunerTabActive)startTunerIfAllowed();}
 @Override protected void onResume(){super.onResume();handler.postDelayed(()->{if(tunerTabActive&&tunerStart!=null)startTunerIfAllowed();},350);}
 @Override protected void onPause(){stopTuner();super.onPause();}
 void stopTuner(){listening=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}
 void listen(){int rate=44100,n=8192;int size=Math.max(n*2,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Микрофон недоступен");short[] data=new short[n];recorder.startRecording();while(listening){int read=0;while(read<n&&listening){int got=recorder.read(data,read,n-read);if(got<=0)break;read+=got;}if(read<n)continue;double best=0;int lagBest=0;double power=0;for(short x:data)power+=x*(double)x;if(power/n<200000)continue;for(int lag=55;lag<550;lag++){double corr=0;for(int i=0;i<n-lag;i+=2)corr+=data[i]*(double)data[i+lag];if(corr>best){best=corr;lagBest=lag;}}if(lagBest==0)continue;double hz=(double)rate/lagBest;if(hz<75||hz>850)continue;int midi=(int)Math.round(69+12*Math.log(hz/440)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};String name=names[(midi%12+12)%12]+(midi/12-1);double target=440*Math.pow(2,(midi-69)/12.0);double cents=1200*Math.log(hz/target)/Math.log(2);final double shown=hz;final String nn=name;final String message=Math.abs(cents)<8?"✓ Струна настроена":cents<0?"↑ Подтяни струну":"↓ Ослабь струну";handler.post(()->{note.setText(nn);freq.setText(String.format(Locale.US,"%.1f Hz",shown));guide.setText(message);gauge.cents=(float)cents;gauge.invalidate();});}}catch(Exception e){handler.post(()->guide.setText("Ошибка микрофона: "+e.getMessage()));}finally{if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}}}
 void tick(){int beat=0;while(ticking){int current=beat++%beats;handler.post(()->{pulse.setText("● "+(current+1));beatLine.position=current;beatLine.beatStart=android.os.SystemClock.elapsedRealtime();beatLine.invalidate();});int sr=22050;int len=sr/16;short[] pcm=new short[len];double phase=0;double hz=current==0?1200:850;for(int i=0;i<len;i++){phase+=2*Math.PI*hz/sr;pcm[i]=(short)(Math.sin(phase)*Math.exp(-i/(sr*.012))*15000);}try{AudioTrack t=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,len*2,AudioTrack.MODE_STATIC);t.write(pcm,0,len);t.play();Thread.sleep(Math.max(10,60000/bpm));t.stop();t.release();}catch(Exception ignored){}}handler.post(()->pulse.setText("●"));}
 protected void onDestroy(){stopTuner();ticking=false;super.onDestroy();}
}
