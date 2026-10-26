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
 Gauge gauge;
 BeatLine beatLine;
 AudioRecord recorder;
 Thread listenThread, tickThread;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(dp(5),dp(8),dp(5),dp(8));return v;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.BLACK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));return b;}
 void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
 ScrollView scroll=new ScrollView(this);LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setPadding(dp(20),dp(12),dp(20),dp(25));root.setBackgroundColor(bg);scroll.addView(root);setContentView(scroll);
 add(root,label("TUNO",35,green));add(root,label("ТЮНЕР  •  МЕТРОНОМ",14,0xffa5b9aa));
 LinearLayout tabs=new LinearLayout(this);root.addView(tabs);Button tunerTab=button("Тюнер");Button metroTab=button("Метроном");tabs.addView(tunerTab,new LinearLayout.LayoutParams(0,dp(56),1));tabs.addView(metroTab,new LinearLayout.LayoutParams(0,dp(56),1));LinearLayout tunerPage=new LinearLayout(this);tunerPage.setOrientation(1);root.addView(tunerPage);LinearLayout metroPage=new LinearLayout(this);metroPage.setOrientation(1);root.addView(metroPage);metroPage.setVisibility(View.GONE);tunerTab.setOnClickListener(v->{tunerPage.setVisibility(View.VISIBLE);metroPage.setVisibility(View.GONE);});metroTab.setOnClickListener(v->{tunerPage.setVisibility(View.GONE);metroPage.setVisibility(View.VISIBLE);});
 add(tunerPage,label("CHROMATIC  •  STANDARD",14,0xffa9b1b7));
 gauge=new Gauge();tunerPage.addView(gauge,new LinearLayout.LayoutParams(-1,dp(235)));
 note=label("—",75,Color.WHITE);add(tunerPage,note);
 freq=label("0.0 Hz",22,0xffb8c7be);add(tunerPage,freq);
 guide=label("Нажми «Включить тюнер»",16,0xffb8c7be);add(tunerPage,guide);
 Button start=button("Включить тюнер");add(tunerPage,start);start.setOnClickListener(v->{if(listening){stopTuner();start.setText("Включить тюнер");}else{if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}listening=true;start.setText("Остановить тюнер");listenThread=new Thread(this::listen);listenThread.start();}});
 add(metroPage,label("TEMPO  •  METRONOME",14,0xffa9b1b7));beatLine=new BeatLine();metroPage.addView(beatLine,new LinearLayout.LayoutParams(-1,dp(255)));tempo=label("100",64,Color.WHITE);add(metroPage,tempo);add(metroPage,label("BPM",15,0xff9ca4ac));
 LinearLayout row=new LinearLayout(this);row.setGravity(Gravity.CENTER);metroPage.addView(row);
 Button minus=button("−");Button plus=button("+");Button sig=button("4/4");
 for(Button x:new Button[]{minus,sig,plus})row.addView(x,new LinearLayout.LayoutParams(0,dp(55),1));
 minus.setOnClickListener(v->{bpm=Math.max(30,bpm-5);tempo.setText(""+bpm);});
 plus.setOnClickListener(v->{bpm=Math.min(240,bpm+5);tempo.setText(bpm+" BPM");});
 sig.setOnClickListener(v->{beats=beats==4?3:beats==3?2:beats==2?6:4;sig.setText(beats+(beats==6?"/8":"/4"));});
 pulse=label("●",26,green);add(metroPage,pulse);
 Button play=button("Запустить метроном");add(metroPage,play);play.setOnClickListener(v->{ticking=!ticking;play.setText(ticking?"Остановить метроном":"Запустить метроном");if(ticking){tickThread=new Thread(this::tick);tickThread.start();}});
 }
 
 class Gauge extends View {
  Paint p=new Paint(3);float cents=0;
  Gauge(){super(MainActivity.this);setBackgroundColor(0xfff5f5f4);}
  protected void onDraw(Canvas c){super.onDraw(c);float w=getWidth(),h=getHeight(),cx=w/2f,cy=h*.92f,r=Math.min(w*.43f,h*.73f);
   p.setTextAlign(Paint.Align.CENTER);p.setStrokeWidth(dp(1));p.setTextSize(dp(13));
   for(int i=-30;i<=30;i++){double a=Math.toRadians(-90+i*1.7);float x=(float)Math.cos(a),y=(float)Math.sin(a);p.setColor(i==0?0xff24292c:0xff929a9c);p.setStrokeWidth(i%5==0?dp(2):dp(1));c.drawLine(cx+x*r,cy+y*r,cx+x*(r-(i%5==0?dp(22):dp(12))),cy+y*(r-(i%5==0?dp(22):dp(12))),p);
    if(i%10==0){p.setColor(0xff60696c);p.setTextSize(dp(11));c.drawText(""+i,cx+x*(r+dp(17)),cy+y*(r+dp(17))+dp(4),p);}}
   double a=Math.toRadians(-90+Math.max(-30,Math.min(30,cents))*1.7);p.setColor(0xff09a9cc);p.setStrokeWidth(dp(2));c.drawLine(cx,cy,cx+(float)Math.cos(a)*r*1.07f,cy+(float)Math.sin(a)*r*1.07f,p);
   p.setColor(0xff60696c);p.setTextSize(dp(12));c.drawText("CENTS",cx,dp(32),p);
  }
 }
 class BeatLine extends View{
  Paint p=new Paint(3);int position=0;
  BeatLine(){super(MainActivity.this);setBackgroundColor(0xff101215);}
  protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f; p.setColor(0xff1ab9e6);p.setStrokeWidth(dp(6));c.drawLine(x,dp(12),x,getHeight()-dp(12),p);}
 }
 void stopTuner(){listening=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}
 void listen(){int rate=44100,n=8192;int size=Math.max(n*2,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Микрофон недоступен");short[] data=new short[n];recorder.startRecording();while(listening){int read=0;while(read<n&&listening){int got=recorder.read(data,read,n-read);if(got<=0)break;read+=got;}if(read<n)continue;double best=0;int lagBest=0;double power=0;for(short x:data)power+=x*(double)x;if(power/n<200000)continue;for(int lag=55;lag<550;lag++){double corr=0;for(int i=0;i<n-lag;i+=2)corr+=data[i]*(double)data[i+lag];if(corr>best){best=corr;lagBest=lag;}}if(lagBest==0)continue;double hz=(double)rate/lagBest;if(hz<75||hz>850)continue;int midi=(int)Math.round(69+12*Math.log(hz/440)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};String name=names[(midi%12+12)%12]+(midi/12-1);double target=440*Math.pow(2,(midi-69)/12.0);double cents=1200*Math.log(hz/target)/Math.log(2);final double shown=hz;final String nn=name;final String message=Math.abs(cents)<8?"✓ Струна настроена":cents<0?"↑ Подтяни струну":"↓ Ослабь струну";handler.post(()->{note.setText(nn);freq.setText(String.format(Locale.US,"%.1f Hz",shown));guide.setText(message);gauge.cents=(float)cents;gauge.invalidate();});}}catch(Exception e){handler.post(()->guide.setText("Ошибка микрофона: "+e.getMessage()));}finally{if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}}}
 void tick(){int beat=0;while(ticking){int current=beat++%beats;handler.post(()->{pulse.setText("● "+(current+1));beatLine.position=current;beatLine.invalidate();});int sr=22050;int len=sr/16;short[] pcm=new short[len];double phase=0;double hz=current==0?1200:850;for(int i=0;i<len;i++){phase+=2*Math.PI*hz/sr;pcm[i]=(short)(Math.sin(phase)*Math.exp(-i/(sr*.012))*15000);}try{AudioTrack t=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,len*2,AudioTrack.MODE_STATIC);t.write(pcm,0,len);t.play();Thread.sleep(Math.max(10,60000/bpm));t.stop();t.release();}catch(Exception ignored){}}handler.post(()->pulse.setText("●"));}
 protected void onDestroy(){stopTuner();ticking=false;super.onDestroy();}
}
