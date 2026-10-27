package com.ivanko.tuno;
import android.Manifest;
import android.app.Activity;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Canvas;
import android.graphics.Paint;
import android.media.*;
import android.hardware.camera2.CameraManager;
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
 ChordBoard chordBoard;
 TextView chordResult;
 BeatLine beatLine;
 volatile int referenceHz=440;
 boolean russianNotes=false;
 TextView tunerHeading;
 boolean flashEnabled=false;
 CameraManager cameraManager;
 String flashCameraId=null;
 AudioRecord recorder;
 Thread listenThread, tickThread;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(dp(5),dp(8),dp(5),dp(8));return v;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);b.setTextColor(Color.BLACK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));return b;}
 void add(LinearLayout l,View v){l.addView(v,new LinearLayout.LayoutParams(-1,-2));}
 public void onCreate(Bundle b){super.onCreate(b);getWindow().setStatusBarColor(bg);getWindow().setNavigationBarColor(bg);
 LinearLayout root=new LinearLayout(this);root.setOrientation(1);root.setBackgroundColor(bg);setContentView(root);
 LinearLayout tabs=new LinearLayout(this);tabs.setPadding(dp(8),dp(4),dp(8),dp(4));root.addView(tabs);Button tunerTab=button("Тюнер");Button metroTab=button("Метроном");Button chordTab=button("Аккорды");tabs.addView(tunerTab,new LinearLayout.LayoutParams(0,dp(56),1));tabs.addView(metroTab,new LinearLayout.LayoutParams(0,dp(56),1));tabs.addView(chordTab,new LinearLayout.LayoutParams(0,dp(56),1));LinearLayout tunerPage=new LinearLayout(this);tunerPage.setOrientation(1);root.addView(tunerPage,new LinearLayout.LayoutParams(-1,0,1));LinearLayout metroPage=new LinearLayout(this);metroPage.setOrientation(1);root.addView(metroPage,new LinearLayout.LayoutParams(-1,0,1));metroPage.setVisibility(View.GONE);ScrollView chordScroll=new ScrollView(this);root.addView(chordScroll,new LinearLayout.LayoutParams(-1,0,1));LinearLayout chordPage=new LinearLayout(this);chordPage.setOrientation(1);chordPage.setPadding(dp(12),dp(8),dp(12),dp(8));chordScroll.addView(chordPage);chordScroll.setVisibility(View.GONE);tunerTab.setOnClickListener(v->{tunerTabActive=true;tunerPage.setVisibility(View.VISIBLE);metroPage.setVisibility(View.GONE);chordScroll.setVisibility(View.GONE);startTunerIfAllowed();});metroTab.setOnClickListener(v->{tunerTabActive=false;tunerPage.setVisibility(View.GONE);metroPage.setVisibility(View.VISIBLE);chordScroll.setVisibility(View.GONE);stopTuner();});
 chordTab.setOnClickListener(v->{tunerTabActive=false;tunerPage.setVisibility(View.GONE);metroPage.setVisibility(View.GONE);chordScroll.setVisibility(View.VISIBLE);stopTuner();});
 add(chordPage,label("ОПРЕДЕЛИТЕЛЬ АККОРДОВ",20,green));add(chordPage,label("Нажимай на струны и лады. Верхний ряд — открытая струна. Долгое нажатие — заглушить.",13,0xffaeb8b2));
 chordResult=label("Выбери лады",32,Color.WHITE);add(chordPage,chordResult);chordBoard=new ChordBoard();chordPage.addView(chordBoard,new LinearLayout.LayoutParams(-1,dp(460)));
 Button barre=button("Баррэ: выкл.");add(chordPage,barre);barre.setOnClickListener(v->{chordBoard.barre=chordBoard.barre==0?1:chordBoard.barre==7?0:chordBoard.barre+1;barre.setText(chordBoard.barre==0?"Баррэ: выкл.":"Баррэ: "+chordBoard.barre+" лад");chordBoard.invalidate();chordBoard.updateChord();});
 EditText chordInput=new EditText(this);chordInput.setSingleLine(true);chordInput.setTextColor(Color.WHITE);chordInput.setHintTextColor(0xffaab3ac);chordInput.setHint("Название аккорда: Am, F, Bm7...");add(chordPage,chordInput);
 Button findChord=button("Показать аккорд");add(chordPage,findChord);findChord.setOnClickListener(v->{String name=chordInput.getText().toString().trim();if(!chordBoard.showNamedChord(name))Toast.makeText(this,"Аккорд не найден. Попробуй C, Am, F, Bm7 и т. д.",Toast.LENGTH_LONG).show();});
 Button resetChord=button("Сбросить аппликатуру");add(chordPage,resetChord);resetChord.setOnClickListener(v->{chordBoard.frets=new int[]{0,0,0,0,0,0};chordBoard.barre=0;barre.setText("Баррэ: выкл.");chordBoard.invalidate();chordBoard.updateChord();});
 tunerHeading=label("CHROMATIC  •  STANDARD",14,0xffa9b1b7);add(tunerPage,tunerHeading);
 LinearLayout tunerOptions=new LinearLayout(this);tunerPage.addView(tunerOptions,new LinearLayout.LayoutParams(-1,dp(54)));
 Button reference=button("A4 = 440 Гц");Button language=button("Ноты: EN");
 tunerOptions.addView(reference,new LinearLayout.LayoutParams(0,-1,1));tunerOptions.addView(language,new LinearLayout.LayoutParams(0,-1,1));
 reference.setOnClickListener(v->{String[] values={"432 Гц","435 Гц","438 Гц","440 Гц","442 Гц","444 Гц","446 Гц"};new android.app.AlertDialog.Builder(this).setTitle("Опорная частота ноты Ля (A4)").setItems(values,(d,which)->{referenceHz=new int[]{432,435,438,440,442,444,446}[which];reference.setText("A4 = "+referenceHz+" Гц");}).show();});
 language.setOnClickListener(v->{russianNotes=!russianNotes;language.setText(russianNotes?"Ноты: RU":"Ноты: EN");tunerHeading.setText(russianNotes?"ХРОМАТИЧЕСКИЙ ТЮНЕР":"CHROMATIC  •  STANDARD");});
 gauge=new Gauge();tunerPage.addView(gauge,new LinearLayout.LayoutParams(-1,dp(235)));
 note=label("—",75,Color.WHITE);add(tunerPage,note);
 freq=label("0.0 Hz",22,0xffb8c7be);add(tunerPage,freq);
 guide=label("Нажми «Включить тюнер»",16,0xffb8c7be);add(tunerPage,guide);
 tunerStart=button("Включить тюнер");add(tunerPage,tunerStart);tunerStart.setOnClickListener(v->{if(listening){stopTuner();tunerStart.setText("Включить тюнер");}else startTunerIfAllowed();});


 beatLine=new BeatLine();metroPage.addView(beatLine,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout tempoRow=new LinearLayout(this);tempoRow.setGravity(Gravity.CENTER_VERTICAL);tempoRow.setBackgroundColor(0xff202020);metroPage.addView(tempoRow,new LinearLayout.LayoutParams(-1,dp(104)));
 Button minus=button("⊖");Button plus=button("⊕");for(Button btn:new Button[]{minus,plus}){btn.setTextSize(27);btn.setTextColor(0xffa5a5a5);btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff202020));}
 tempo=label("120",65,Color.WHITE);tempoRow.addView(minus,new LinearLayout.LayoutParams(dp(72),-1));tempoRow.addView(tempo,new LinearLayout.LayoutParams(0,-1,1));tempoRow.addView(plus,new LinearLayout.LayoutParams(dp(72),-1));
 bpm=120;setupTempoRepeat(minus,-1);setupTempoRepeat(plus,1);
 HorizontalScrollView presetsScroll=new HorizontalScrollView(this);presetsScroll.setHorizontalScrollBarEnabled(false);presetsScroll.setBackgroundColor(0xff252727);metroPage.addView(presetsScroll,new LinearLayout.LayoutParams(-1,dp(51)));
 LinearLayout presets=new LinearLayout(this);presetsScroll.addView(presets);
 String[] presetNames={"Largo","Adagio","Andante","Moderato","Allegro","Presto","Prestissimo"};int[] presetBpms={45,66,84,108,132,176,208};
 for(int i=0;i<presetNames.length;i++){final int v=presetBpms[i];Button preset=button(presetNames[i]);preset.setAllCaps(false);preset.setTextSize(13);preset.setTextColor(v==132?Color.WHITE:0xff858585);preset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff252727));presets.addView(preset,new LinearLayout.LayoutParams(dp(92),dp(49)));preset.setOnClickListener(w->{bpm=v;tempo.setText(""+bpm);});}
 LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(7),dp(20),dp(7),dp(20));controls.setBackgroundColor(0xff222222);metroPage.addView(controls);
 Button flash=button("🔦 Фонарик: выкл.");add(metroPage,flash);flash.setOnClickListener(v->{if(!flashEnabled){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},2);Toast.makeText(this,"Разреши доступ к камере и нажми фонарик ещё раз",Toast.LENGTH_LONG).show();return;}try{cameraManager=(CameraManager)getSystemService(CAMERA_SERVICE);flashCameraId=null;for(String id:cameraManager.getCameraIdList()){Boolean available=cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);if(Boolean.TRUE.equals(available)){flashCameraId=id;break;}}if(flashCameraId==null){Toast.makeText(this,"Фонарик недоступен",Toast.LENGTH_SHORT).show();return;}flashEnabled=true;}catch(Exception ex){Toast.makeText(this,"Ошибка фонарика",Toast.LENGTH_SHORT).show();return;}}else{flashEnabled=false;setFlash(false);}flash.setText(flashEnabled?"🔦 Фонарик: вкл.":"🔦 Фонарик: выкл.");});
 Button play=button("Start"),stop=button("Stop");play.setTextSize(21);stop.setTextSize(21);play.setTextColor(0xffeeeeee);stop.setTextColor(Color.WHITE);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff777777));stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffcf2634));
 LinearLayout.LayoutParams cp1=new LinearLayout.LayoutParams(0,dp(62),1);cp1.setMargins(0,0,dp(8),0);LinearLayout.LayoutParams cp2=new LinearLayout.LayoutParams(0,dp(62),1);cp2.setMargins(dp(8),0,0,0);controls.addView(play,cp1);controls.addView(stop,cp2);
 pulse=label("",12,green);pulse.setVisibility(View.GONE);metroPage.addView(pulse);
 play.setOnClickListener(v->{if(!ticking){ticking=true;tickThread=new Thread(this::tick);tickThread.start();}});
 stop.setOnClickListener(v->{ticking=false;setFlash(false);beatLine.invalidate();});


 }
 

 class ChordBoard extends View{
  Paint p=new Paint(3);int[] frets={0,0,0,0,0,0};int barre=0;final int[] tuning={4,9,2,7,11,4};
  ChordBoard(){super(MainActivity.this);setBackgroundColor(0xff151a17);setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){float sx=getWidth()*.12f,sy=getHeight()*.10f,dx=getWidth()*.76f/5f,dy=getHeight()*.80f/8f;int string=Math.max(0,Math.min(5,Math.round((e.getX()-sx)/dx)));int fret=Math.max(0,Math.min(7,(int)Math.floor((e.getY()-sy)/dy)));frets[string]=fret;invalidate();updateChord();return true;}return true;});}
 
 boolean showNamedChord(String raw){String s=raw.trim().replace("♯","#").replace("♭","b");if(s.isEmpty())return false;String[] roots={"C","C#","Db","D","D#","Eb","E","F","F#","Gb","G","G#","Ab","A","A#","Bb","B"};int[] pitch={0,1,1,2,3,3,4,5,6,6,7,8,8,9,10,10,11};int root=-1;String suffix="";for(int i=0;i<roots.length;i++){String r=roots[i];if(s.regionMatches(true,0,r,0,r.length())&&(root<0||r.length()>s.length()-suffix.length())){root=pitch[i];suffix=s.substring(r.length());if(r.length()==2)break;}}if(root<0)return false;
 String[] types={"","m","7","m7","maj7"};int[][] ivs={{0,4,7},{0,3,7},{0,4,7,10},{0,3,7,10},{0,4,7,11}};int type=-1;for(int i=0;i<types.length;i++)if(suffix.equalsIgnoreCase(types[i])){type=i;break;}if(type<0)return false;
 int[] best=null;int bestScore=Integer.MAX_VALUE;for(int base=0;base<=5;base++){int[] trial=new int[6];int min=99,max=0;int[] found=new int[12];for(int st=0;st<6;st++){int chosen=-1;for(int fr=base;fr<=Math.min(7,base+3);fr++){int n=(tuning[st]+fr)%12;boolean ok=false;for(int iv:ivs[type])if(n==(root+iv)%12)ok=true;if(ok){chosen=fr;break;}}trial[st]=chosen;if(chosen>=0){found[(tuning[st]+chosen)%12]++;min=Math.min(min,chosen);max=Math.max(max,chosen);}}boolean valid=true;for(int iv:ivs[type])if(found[(root+iv)%12]==0)valid=false;if(!valid)continue;int score=(max-min)*5+base*2;for(int v:trial)if(v<0)score+=4;else score+=v;if(score<bestScore){bestScore=score;best=trial;}}
 if(best==null)return false;frets=best;barre=0;invalidate();chordResult.setText(raw.trim());return true;
 }
  void updateChord(){int[] count=new int[12];int bass=-1;for(int i=0;i<6;i++){if(frets[i]<0)continue;int f=Math.max(frets[i],barre);int n=(tuning[i]+f)%12;count[n]++;if(bass<0)bass=n;}String[] names={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};String best="Неизвестный аккорд";int score=0;String[] types={"","m","dim","aug","sus2","sus4","7","m7","maj7"};int[][] intervals={{0,4,7},{0,3,7},{0,3,6},{0,4,8},{0,2,7},{0,5,7},{0,4,7,10},{0,3,7,10},{0,4,7,11}};for(int root=0;root<12;root++)for(int t=0;t<types.length;t++){int hits=0,extra=0;for(int n=0;n<12;n++)if(count[n]>0){boolean ok=false;for(int iv:intervals[t])if((root+iv)%12==n)ok=true;if(ok)hits++;else extra++;}boolean complete=true;for(int iv:intervals[t])if(count[(root+iv)%12]==0)complete=false;int s=hits*3-extra*6+(complete?8:0)+(bass==root?2:0);if(complete&&s>score){score=s;best=names[root]+types[t];}}chordResult.setText(best);}
  protected void onDraw(Canvas c){super.onDraw(c);float sx=getWidth()*.12f,sy=getHeight()*.10f,dx=getWidth()*.76f/5f,dy=getHeight()*.80f/8f;p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(14));for(int i=0;i<6;i++){float x=sx+i*dx;p.setColor(0xffb0c4b8);p.setStrokeWidth(dp(i<3?2:1));c.drawLine(x,sy+dy,x,sy+dy*8,p);p.setColor(Color.WHITE);c.drawText(new String[]{"E","A","D","G","B","e"}[i],x,sy-dp(8),p);}for(int j=1;j<=8;j++){float y=sy+j*dy;p.setColor(0xff85998b);p.setStrokeWidth(dp(j==1?4:1));c.drawLine(sx,y,sx+dx*5,y,p);p.setColor(Color.WHITE);if(j<8)c.drawText(""+j,sx-dp(23),y+dy*.55f,p);}if(barre>0){p.setColor(0xff39db8a);p.setStrokeWidth(dp(12));float y=sy+(barre+.5f)*dy;c.drawLine(sx,y,sx+5*dx,y,p);}for(int i=0;i<6;i++){int f=frets[i];if(f<0)continue;float x=sx+i*dx,y=sy+(f==0?0:(f+.5f)*dy);p.setColor(0xff39db8a);c.drawCircle(x,y,dp(8),p);}}
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
  protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f;if(ticking){float progress=Math.min(1f,(android.os.SystemClock.elapsedRealtime()-beatStart)/(60000f/bpm));float sway=(float)Math.sin(progress*Math.PI);x+=((position%2==0)?1:-1)*sway*getWidth()*.30f;postInvalidateDelayed(16);}p.setColor(0xff1ab9e6);p.setStrokeWidth(dp(12));c.drawLine(x,0,x,getHeight(),p);}
 }
 void startTunerIfAllowed(){if(listening)return;if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}listening=true;tunerStart.setText("Остановить тюнер");guide.setText("Слушаю гитару…");listenThread=new Thread(this::listen);listenThread.start();}
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&tunerTabActive)startTunerIfAllowed();}
 @Override protected void onResume(){super.onResume();handler.postDelayed(()->{if(tunerTabActive&&tunerStart!=null)startTunerIfAllowed();},350);}
 @Override protected void onPause(){stopTuner();ticking=false;setFlash(false);super.onPause();}
 void setupTempoRepeat(Button button,int direction){final Runnable[] task=new Runnable[1];final boolean[] held={false};final long[] started={0};
 task[0]=new Runnable(){public void run(){if(!held[0])return;bpm=Math.max(20,Math.min(500,bpm+direction));tempo.setText(""+bpm);long elapsed=android.os.SystemClock.uptimeMillis()-started[0];handler.postDelayed(this,elapsed>1800?35:elapsed>700?75:160);}};
 button.setOnTouchListener((v,event)->{switch(event.getAction()){case MotionEvent.ACTION_DOWN:held[0]=true;started[0]=android.os.SystemClock.uptimeMillis();bpm=Math.max(20,Math.min(500,bpm+direction));tempo.setText(""+bpm);handler.postDelayed(task[0],400);return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:held[0]=false;handler.removeCallbacks(task[0]);return true;}return false;});}
 void setFlash(boolean on){try{if(cameraManager!=null&&flashCameraId!=null)cameraManager.setTorchMode(flashCameraId,on);}catch(Exception ignored){}}
 void stopTuner(){listening=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}
 void listen(){int rate=44100,n=8192;int size=Math.max(n*2,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Микрофон недоступен");short[] data=new short[n];recorder.startRecording();while(listening){int read=0;while(read<n&&listening){int got=recorder.read(data,read,n-read);if(got<=0)break;read+=got;}if(read<n)continue;double best=0;int lagBest=0;double power=0;for(short x:data)power+=x*(double)x;if(power/n<200000)continue;for(int lag=55;lag<550;lag++){double corr=0;for(int i=0;i<n-lag;i+=2)corr+=data[i]*(double)data[i+lag];if(corr>best){best=corr;lagBest=lag;}}if(lagBest==0)continue;double hz=(double)rate/lagBest;if(hz<75||hz>850)continue;int midi=(int)Math.round(69+12*Math.log(hz/referenceHz)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};String[] ruNames={"До","До♯","Ре","Ре♯","Ми","Фа","Фа♯","Соль","Соль♯","Ля","Ля♯","Си"};int ni=(midi%12+12)%12;String name=(russianNotes?ruNames[ni]:names[ni])+(midi/12-1);double target=referenceHz*Math.pow(2,(midi-69)/12.0);double cents=1200*Math.log(hz/target)/Math.log(2);final double shown=hz;final String nn=name;final String message=Math.abs(cents)<8?"✓ Струна настроена":cents<0?"↑ Подтяни струну":"↓ Ослабь струну";handler.post(()->{note.setText(nn);freq.setText(String.format(Locale.US,"%.1f Hz",shown));guide.setText(message);gauge.cents=(float)cents;gauge.invalidate();});}}catch(Exception e){handler.post(()->guide.setText("Ошибка микрофона: "+e.getMessage()));}finally{if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}}}
 void tick(){int beat=0;final int sr=11025;short[] pcm;try{java.io.InputStream in=getAssets().open("metronome_click.gz.b64");java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)encoded.write(buf,0,n);in.close();byte[] compressed=android.util.Base64.decode(encoded.toByteArray(),android.util.Base64.DEFAULT);java.util.zip.GZIPInputStream gz=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed));java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();while((n=gz.read(buf))!=-1)raw.write(buf,0,n);gz.close();byte[] bytes=raw.toByteArray();pcm=new short[bytes.length/2];for(int i=0;i<pcm.length;i++)pcm[i]=(short)((bytes[2*i]&255)|(bytes[2*i+1]<<8));}catch(Exception ex){handler.post(()->Toast.makeText(this,"Не удалось загрузить звук метронома",Toast.LENGTH_LONG).show());return;}final int len=pcm.length;
 AudioTrack track=null;try{track=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,len*2,AudioTrack.MODE_STATIC);track.write(pcm,0,len);
 while(ticking){int current=beat++%beats;if(flashEnabled){setFlash(true);handler.postDelayed(()->setFlash(false),Math.min(70,Math.max(25,30000/bpm)));}handler.post(()->{pulse.setText("● "+(current+1));beatLine.position=current;beatLine.beatStart=android.os.SystemClock.elapsedRealtime();beatLine.invalidate();});track.stop();track.reloadStaticData();track.play();Thread.sleep(Math.max(10,60000/bpm));}
 }catch(Exception ignored){}finally{if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}setFlash(false);handler.post(()->pulse.setText("●"));}}
 protected void onDestroy(){stopTuner();ticking=false;setFlash(false);super.onDestroy();}
}
