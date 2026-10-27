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
import java.util.Random;
import java.util.ArrayList;

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
 boolean showStringNumber=false;
 volatile int instrumentMode=0;
 TextView stringInfo;
 final String[] instrumentNames={"Гитара (6 струн)","Домра малая (3 струны)","Домра альтовая (4 струны)","Укулеле (4 струны)","Бас-гитара (4 струны)"};
 final int[][] instrumentMidi={{40,45,50,55,59,64},{64,69,74},{55,62,69,76},{67,60,64,69},{28,33,38,43}};
 TextView tunerHeading;
 LinearLayout tunerSettings;
 boolean chordListening=false;
 TextView smartStrings,practiceStatus,recognitionStatus;
 volatile int[] activeTuning=null;
 boolean acceleration=false;
 int accelerationEvery=8,accelerationStep=5;
 int practiceSeconds=0;boolean practicing=false;
 long lastTap=0;final ArrayList<Long> tapIntervals=new ArrayList<>();
 android.media.AudioTrack pitchTrack;
 final Random random=new Random();
 boolean flashEnabled=false;
 CameraManager cameraManager;
 String flashCameraId=null;
 AudioRecord recorder;
 Thread listenThread, tickThread;
 int dp(int n){return (int)(n*getResources().getDisplayMetrics().density);}
 TextView label(String s,int size,int color){TextView v=new TextView(this);v.setText(s);v.setTextSize(size);v.setTextColor(color);v.setGravity(Gravity.CENTER);v.setPadding(dp(5),dp(8),dp(5),dp(8));return v;}
 Button button(String s){Button b=new Button(this);b.setText(s);b.setAllCaps(false);
 if(s.equals("Start")||s.equals("Stop")){b.setTextColor(Color.BLACK);b.setBackgroundTintList(android.content.res.ColorStateList.valueOf(green));return b;}
 b.setTextSize(14);b.setTypeface(android.graphics.Typeface.create("sans-serif-medium",android.graphics.Typeface.NORMAL));b.setTextColor(0xffd6ffe6);
 android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(0xff253a30);shape.setCornerRadius(dp(22));shape.setStroke(dp(1),0xff3c6350);
 android.graphics.drawable.RippleDrawable ripple=new android.graphics.drawable.RippleDrawable(android.content.res.ColorStateList.valueOf(0x6644d98b),shape,null);
 b.setBackground(ripple);b.setBackgroundTintList(null);b.setElevation(dp(1));b.setPadding(dp(10),dp(6),dp(10),dp(6));return b;}
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
 add(chordPage,label("БИБЛИОТЕКА И ГЕНЕРАТОР",17,green));
 LinearLayout libraryRow=new LinearLayout(this);chordPage.addView(libraryRow);
 Button library=button("Библиотека"),generate=button("Случайный");libraryRow.addView(library,new LinearLayout.LayoutParams(0,dp(52),1));libraryRow.addView(generate,new LinearLayout.LayoutParams(0,dp(52),1));
 library.setOnClickListener(v->showChordLibrary(chordInput));
 generate.setOnClickListener(v->{String[] names={"C","D","E","F","G","A","Am","Dm","Em","G7","C7","Bm","F#m","Bb"};String selected=names[random.nextInt(names.length)];chordInput.setText(selected);chordBoard.showNamedChord(selected);});
 recognitionStatus=label("Распознавание по звуку: экспериментально",13,0xffaeb8b2);add(chordPage,recognitionStatus);
 Button recognize=button("🎙 Слушать аккорд");add(chordPage,recognize);recognize.setOnClickListener(v->{if(chordListening)return;chordListening=true;recognize.setEnabled(false);recognitionStatus.setText("Сыграй аккорд и удерживай звук 2 секунды…");recognizeChord(()->{chordListening=false;recognize.setEnabled(true);});});
 Button resetChord=button("Сбросить аппликатуру");add(chordPage,resetChord);resetChord.setOnClickListener(v->{chordBoard.frets=new int[]{0,0,0,0,0,0};chordBoard.barre=0;barre.setText("Баррэ: выкл.");chordBoard.invalidate();chordBoard.updateChord();});
 tunerHeading=label("CHROMATIC  •  STANDARD",14,0xffa9b1b7);
 tunerSettings=new LinearLayout(this);tunerSettings.setOrientation(1);LinearLayout tunerOptions=new LinearLayout(this);tunerSettings.addView(tunerOptions,new LinearLayout.LayoutParams(-1,dp(54)));
 Button reference=button("A4 = 440 Гц");Button language=button("Ноты: EN");
 tunerOptions.addView(reference,new LinearLayout.LayoutParams(0,-1,1));tunerOptions.addView(language,new LinearLayout.LayoutParams(0,-1,1));
 reference.setOnClickListener(v->{String[] values={"432 Гц","435 Гц","438 Гц","440 Гц","442 Гц","444 Гц","446 Гц"};new android.app.AlertDialog.Builder(this).setTitle("Опорная частота ноты Ля (A4)").setItems(values,(d,which)->{referenceHz=new int[]{432,435,438,440,442,444,446}[which];reference.setText("A4 = "+referenceHz+" Гц");}).show();});
 language.setOnClickListener(v->{russianNotes=!russianNotes;language.setText(russianNotes?"Ноты: RU":"Ноты: EN");tunerHeading.setText(russianNotes?"ХРОМАТИЧЕСКИЙ ТЮНЕР":"CHROMATIC  •  STANDARD");});
 LinearLayout instrumentOptions=new LinearLayout(this);tunerSettings.addView(instrumentOptions,new LinearLayout.LayoutParams(-1,dp(52)));
 Button instrumentButton=button("Инструмент: Гитара");Button stringButton=button("Струна: выкл.");
 instrumentOptions.addView(instrumentButton,new LinearLayout.LayoutParams(0,-1,1));instrumentOptions.addView(stringButton,new LinearLayout.LayoutParams(0,-1,1));
 activeTuning=instrumentMidi[0].clone();
 instrumentButton.setOnClickListener(v->{
 LinearLayout chooser=new LinearLayout(this);chooser.setOrientation(1);chooser.setPadding(dp(18),dp(12),dp(18),dp(12));chooser.setBackgroundColor(0xff121c17);
 TextView heading=label("Выбор инструмента",23,green);add(chooser,heading);
 TextView description=label("Выбери инструмент и его стандартный строй",13,0xffb7c8bf);add(chooser,description);
 android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setView(chooser).create();
 String[] tunings={"E2 · A2 · D3 · G3 · B3 · E4","E4 · A4 · D5","G3 · D4 · A4 · E5","G4 · C4 · E4 · A4","E1 · A1 · D2 · G2"};
 for(int i=0;i<instrumentNames.length;i++){final int index=i;LinearLayout item=new LinearLayout(this);item.setOrientation(1);item.setPadding(dp(14),dp(8),dp(14),dp(8));
 android.graphics.drawable.GradientDrawable shape=new android.graphics.drawable.GradientDrawable();shape.setColor(i==instrumentMode?0xff27573c:0xff23372d);shape.setCornerRadius(dp(18));item.setBackground(shape);
 TextView title=label((i==instrumentMode?"✓  ":"")+instrumentNames[i],17,Color.WHITE);add(item,title);TextView subtitle=label(tunings[i],12,0xffb4d5c0);add(item,subtitle);
 LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,-2);lp.setMargins(0,dp(5),0,dp(5));chooser.addView(item,lp);
 item.setOnClickListener(w->{instrumentMode=index;activeTuning=instrumentMidi[index].clone();instrumentButton.setText(instrumentNames[index]);refreshStrings(-1,false);stringInfo.setText("Сыграй открытую струну");dialog.dismiss();});}
 dialog.show();dialog.getWindow().setBackgroundDrawableResource(android.R.color.transparent);
 });
 stringButton.setOnClickListener(v->{showStringNumber=!showStringNumber;stringButton.setText(showStringNumber?"Струна: вкл.":"Струна: выкл.");stringInfo.setVisibility(showStringNumber?View.VISIBLE:View.GONE);if(!showStringNumber)stringInfo.setText("");});
 stringInfo=label("Сыграй открытую струну",16,green);stringInfo.setVisibility(View.GONE);
 smartStrings=label("СТРУНЫ • сыграй открытую струну",13,green);
 LinearLayout tuningActions=new LinearLayout(this);tunerSettings.addView(tuningActions,new LinearLayout.LayoutParams(-1,dp(46)));
 Button favorites=button("Строи ★"),saveTuning=button("Сохранить строй");
 tuningActions.addView(favorites,new LinearLayout.LayoutParams(0,-1,1));tuningActions.addView(saveTuning,new LinearLayout.LayoutParams(0,-1,1));
 favorites.setOnClickListener(v->showTunings(instrumentButton));
 saveTuning.setOnClickListener(v->{android.widget.EditText name=new android.widget.EditText(this);name.setSingleLine(true);name.setHint("Название строя");new android.app.AlertDialog.Builder(this).setTitle("Сохранить текущий строй").setView(name).setPositiveButton("Сохранить",(d,w)->{String n=name.getText().toString().trim();if(n.isEmpty())return;StringBuilder bld=new StringBuilder();for(int x:activeTuning){if(bld.length()>0)bld.append(",");bld.append(x);}getPreferences(0).edit().putString("tuning_"+n,bld.toString()).apply();Toast.makeText(this,"Строй сохранён",Toast.LENGTH_SHORT).show();}).setNegativeButton("Отмена",null).show();});
 Button tuningFork=button("Камертон A4 ▶");add(tunerSettings,tuningFork);tuningFork.setOnClickListener(v->{if(pitchTrack!=null){stopPitch();tuningFork.setText("Камертон A4 ▶");}else{playPitch();tuningFork.setText("Камертон ■");}});
 gauge=new Gauge();tunerPage.addView(gauge,new LinearLayout.LayoutParams(-1,0,1));
 add(tunerPage,stringInfo);
 note=label("—",75,Color.WHITE);add(tunerPage,note);
 freq=label("0.0 Hz",22,0xffb8c7be);add(tunerPage,freq);
 guide=label("Нажми «Включить тюнер»",16,0xffb8c7be);add(tunerPage,guide);
 tunerStart=button("Включить тюнер");add(tunerPage,tunerStart);tunerStart.setOnClickListener(v->{if(listening){stopTuner();tunerStart.setText("Включить тюнер");}else startTunerIfAllowed();});
 add(tunerPage,smartStrings);Button tunerSettingsButton=button("⚙ Настройки тюнера");add(tunerPage,tunerSettingsButton);tunerSettings.setVisibility(View.GONE);add(tunerPage,tunerSettings);tunerSettingsButton.setOnClickListener(v->{boolean show=tunerSettings.getVisibility()!=View.VISIBLE;tunerSettings.setVisibility(show?View.VISIBLE:View.GONE);tunerSettingsButton.setText(show?"⚙ Скрыть настройки":"⚙ Настройки тюнера");});


 beatLine=new BeatLine();metroPage.addView(beatLine,new LinearLayout.LayoutParams(-1,0,1));
 LinearLayout tempoRow=new LinearLayout(this);tempoRow.setGravity(Gravity.CENTER_VERTICAL);tempoRow.setBackgroundColor(0xff202020);metroPage.addView(tempoRow,new LinearLayout.LayoutParams(-1,dp(104)));
 Button minus=button("⊖");Button plus=button("⊕");for(Button btn:new Button[]{minus,plus}){btn.setTextSize(27);btn.setTextColor(0xffa5a5a5);btn.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff202020));}
 tempo=label("120",65,Color.WHITE);tempoRow.addView(minus,new LinearLayout.LayoutParams(dp(72),-1));tempoRow.addView(tempo,new LinearLayout.LayoutParams(0,-1,1));tempoRow.addView(plus,new LinearLayout.LayoutParams(dp(72),-1));
 bpm=120;setupTempoRepeat(minus,-1);setupTempoRepeat(plus,1);
 HorizontalScrollView presetsScroll=new HorizontalScrollView(this);presetsScroll.setHorizontalScrollBarEnabled(false);presetsScroll.setBackgroundColor(0xff252727);metroPage.addView(presetsScroll,new LinearLayout.LayoutParams(-1,dp(51)));
 LinearLayout presets=new LinearLayout(this);presetsScroll.addView(presets);
 String[] presetNames={"Largo","Adagio","Andante","Moderato","Allegro","Presto","Prestissimo"};int[] presetBpms={45,66,84,108,132,176,208};
 for(int i=0;i<presetNames.length;i++){final int v=presetBpms[i];Button preset=button(presetNames[i]);preset.setAllCaps(false);preset.setTextSize(13);preset.setTextColor(v==132?Color.WHITE:0xff858585);preset.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff252727));presets.addView(preset,new LinearLayout.LayoutParams(dp(92),dp(49)));preset.setOnClickListener(w->{bpm=v;tempo.setText(""+bpm);});}
 LinearLayout metroExtras=new LinearLayout(this);metroPage.addView(metroExtras,new LinearLayout.LayoutParams(-1,dp(47)));
 Button tap=button("Tap Tempo"),accelerate=button("Ускорение: выкл.");metroExtras.addView(tap,new LinearLayout.LayoutParams(0,-1,1));metroExtras.addView(accelerate,new LinearLayout.LayoutParams(0,-1,1));
 tap.setOnClickListener(v->{long now=android.os.SystemClock.elapsedRealtime();if(lastTap>0&&now-lastTap<2500){tapIntervals.add(now-lastTap);if(tapIntervals.size()>6)tapIntervals.remove(0);long total=0;for(long interval:tapIntervals)total+=interval;int measured=(int)Math.round(60000.0/(total/(double)tapIntervals.size()));bpm=Math.max(20,Math.min(500,measured));tempo.setText(""+bpm);}else tapIntervals.clear();lastTap=now;});
 accelerate.setOnClickListener(v->{acceleration=!acceleration;accelerate.setText(acceleration?"Ускорение: +5 / 8":"Ускорение: выкл.");});
 LinearLayout controls=new LinearLayout(this);controls.setPadding(dp(7),dp(20),dp(7),dp(20));controls.setBackgroundColor(0xff222222);metroPage.addView(controls);
 Button flash=button("🔦");flash.setTextSize(19);flash.setContentDescription("Фонарик: выключен");metroExtras.addView(flash,new LinearLayout.LayoutParams(dp(54),-1));flash.setOnClickListener(v->{if(!flashEnabled){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.CAMERA)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.CAMERA},2);Toast.makeText(this,"Разреши доступ к камере и нажми фонарик ещё раз",Toast.LENGTH_LONG).show();return;}try{cameraManager=(CameraManager)getSystemService(CAMERA_SERVICE);flashCameraId=null;for(String id:cameraManager.getCameraIdList()){Boolean available=cameraManager.getCameraCharacteristics(id).get(android.hardware.camera2.CameraCharacteristics.FLASH_INFO_AVAILABLE);if(Boolean.TRUE.equals(available)){flashCameraId=id;break;}}if(flashCameraId==null){Toast.makeText(this,"Фонарик недоступен",Toast.LENGTH_SHORT).show();return;}flashEnabled=true;}catch(Exception ex){Toast.makeText(this,"Ошибка фонарика",Toast.LENGTH_SHORT).show();return;}}else{flashEnabled=false;setFlash(false);}flash.setText(flashEnabled?"🔦✓":"🔦");flash.setContentDescription(flashEnabled?"Фонарик: включён":"Фонарик: выключен");});
 Button play=button("Start"),stop=button("Stop");play.setTextSize(21);stop.setTextSize(21);play.setTextColor(0xffeeeeee);stop.setTextColor(Color.WHITE);play.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xff777777));stop.setBackgroundTintList(android.content.res.ColorStateList.valueOf(0xffcf2634));
 LinearLayout.LayoutParams cp1=new LinearLayout.LayoutParams(0,dp(62),1);cp1.setMargins(0,0,dp(8),0);LinearLayout.LayoutParams cp2=new LinearLayout.LayoutParams(0,dp(62),1);cp2.setMargins(dp(8),0,0,0);controls.addView(play,cp1);controls.addView(stop,cp2);
 LinearLayout practiceRow=new LinearLayout(this);metroPage.addView(practiceRow,new LinearLayout.LayoutParams(-1,dp(43)));
 Button practice=button("Тренировка ▶");practiceStatus=label("00:00",16,green);practiceRow.addView(practice,new LinearLayout.LayoutParams(0,-1,2));practiceRow.addView(practiceStatus,new LinearLayout.LayoutParams(0,-1,1));
 practice.setOnClickListener(v->{practicing=!practicing;practice.setText(practicing?"Тренировка ■":"Тренировка ▶");if(practicing){handler.post(new Runnable(){public void run(){if(!practicing)return;practiceSeconds++;practiceStatus.setText(String.format(Locale.US,"%02d:%02d",practiceSeconds/60,practiceSeconds%60));handler.postDelayed(this,1000);}});}else{int old=getPreferences(0).getInt("practice_total",0);String entries=getPreferences(0).getString("practice_history","");String date=new java.text.SimpleDateFormat("dd.MM.yyyy HH:mm",Locale.getDefault()).format(new java.util.Date());getPreferences(0).edit().putInt("practice_total",old+practiceSeconds).putString("practice_history",date+" — "+(practiceSeconds/60)+" мин "+(practiceSeconds%60)+" сек\n"+entries).apply();practiceSeconds=0;Toast.makeText(this,"Время занятий сохранено",Toast.LENGTH_SHORT).show();}});
 Button history=button("История");practiceRow.addView(history,new LinearLayout.LayoutParams(0,-1,1));history.setOnClickListener(v->showPracticeHistory());practiceStatus.setOnClickListener(v->showPracticeHistory());
 pulse=label("",12,green);pulse.setVisibility(View.GONE);metroPage.addView(pulse);
 play.setOnClickListener(v->{if(!ticking){ticking=true;tickThread=new Thread(this::tick);tickThread.start();}});
 stop.setOnClickListener(v->{ticking=false;setFlash(false);beatLine.invalidate();});


 }
 

 class ChordBoard extends View{
  Paint p=new Paint(3);int[] frets={-1,3,2,0,1,0};int barre=0;final int[] tuning={4,9,2,7,11,4};
  ChordBoard(){super(MainActivity.this);setBackgroundColor(0xff151a17);setOnTouchListener((v,e)->{if(e.getAction()==MotionEvent.ACTION_UP){float sx=getWidth()*.12f,sy=getHeight()*.10f,dx=getWidth()*.76f/5f,dy=getHeight()*.80f/8f;int string=Math.max(0,Math.min(5,Math.round((e.getX()-sx)/dx)));int fret=Math.max(0,Math.min(7,(int)Math.floor((e.getY()-sy)/dy)));frets[string]=fret;invalidate();updateChord();return true;}return true;});}
 
 boolean showNamedChord(String raw){
 String s=raw.trim().replace("♯","#").replace("♭","b");
 String[] names={"C","Cm","C7","Cmaj7","D","Dm","D7","Dmaj7","E","Em","E7","Emaj7","F","Fm","F7","Fmaj7","G","Gm","G7","Gmaj7","A","Am","A7","Amaj7","B","Bm","B7","Bmaj7","Bb","Bbm","Bb7","Bbmaj7","F#","F#m","F#7","F#maj7","C#","C#m","C#7","C#maj7","Eb","Ebm","Eb7","Ebmaj7","Ab","Abm","Ab7","Abmaj7"};
 String[] shapes={"x32010","x31013","x32310","x32000","xx0232","xx0231","xx0212","xx0222","022100","022000","020100","021100","133211","133111","131211","132211","320003","355333","320001","320002","x02220","x02210","x02020","x02120","x24442","x24432","x21202","x24342","x13331","x13321","x13131","x13231","244322","244222","242322","243322","x46664","x46654","x46464","x46564","x65343","x65342","x65646","x65333","466544","466444","464544","465544"};
 int idx=-1;for(int i=0;i<names.length;i++)if(names[i].equalsIgnoreCase(s)){idx=i;break;}
 if(idx<0)return false;
 String shape=shapes[idx];int[] result=new int[6];for(int i=0;i<6;i++)result[i]=shape.charAt(i)=='x'?-1:shape.charAt(i)-'0';
 frets=result;barre=0;invalidate();chordResult.setText(names[idx]+"   "+shape.replace('x','X'));return true;
 }
  void updateChord(){int[] count=new int[12];int bass=-1;for(int i=0;i<6;i++){if(frets[i]<0)continue;int f=frets[i]==0?Math.max(frets[i],barre):frets[i];int n=(tuning[i]+f)%12;count[n]++;if(bass<0)bass=n;}String[] names={"C","C#","D","D#","E","F","F#","G","G#","A","A#","B"};String best="Неизвестный аккорд";int score=0;String[] types={"","m","dim","aug","sus2","sus4","7","m7","maj7"};int[][] intervals={{0,4,7},{0,3,7},{0,3,6},{0,4,8},{0,2,7},{0,5,7},{0,4,7,10},{0,3,7,10},{0,4,7,11}};for(int root=0;root<12;root++)for(int t=0;t<types.length;t++){int hits=0,extra=0;for(int n=0;n<12;n++)if(count[n]>0){boolean ok=false;for(int iv:intervals[t])if((root+iv)%12==n)ok=true;if(ok)hits++;else extra++;}boolean complete=true;for(int iv:intervals[t])if(count[(root+iv)%12]==0)complete=false;int s=hits*3-extra*6+(complete?8:0)+(bass==root?2:0);if(complete&&extra==0&&s>score){score=s;best=names[root]+types[t];}}chordResult.setText(best);}
  protected void onDraw(Canvas c){super.onDraw(c);float sx=getWidth()*.12f,sy=getHeight()*.10f,dx=getWidth()*.76f/5f,dy=getHeight()*.80f/8f;p.setTextAlign(Paint.Align.CENTER);p.setTextSize(dp(14));for(int i=0;i<6;i++){float x=sx+i*dx;p.setColor(0xffb0c4b8);p.setStrokeWidth(dp(i<3?2:1));c.drawLine(x,sy+dy,x,sy+dy*8,p);p.setColor(Color.WHITE);c.drawText(new String[]{"E","A","D","G","B","e"}[i],x,sy-dp(8),p);}for(int j=1;j<=8;j++){float y=sy+j*dy;p.setColor(0xff85998b);p.setStrokeWidth(dp(j==1?4:1));c.drawLine(sx,y,sx+dx*5,y,p);p.setColor(Color.WHITE);if(j<8)c.drawText(""+j,sx-dp(23),y+dy*.55f,p);}if(barre>0){p.setColor(0xff39db8a);p.setStrokeWidth(dp(12));float y=sy+(barre+.5f)*dy;c.drawLine(sx,y,sx+5*dx,y,p);}for(int i=0;i<6;i++){int f=frets[i];if(f<0){p.setColor(0xfff7a4a4);c.drawText("×",sx+i*dx,sy-dp(8),p);continue;}float x=sx+i*dx,y=sy+(f==0?0:(f+.5f)*dy);p.setColor(0xff39db8a);c.drawCircle(x,y,dp(8),p);}}
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
  protected void onDraw(Canvas c){super.onDraw(c);float x=getWidth()/2f;if(ticking){float progress=Math.min(1f,(android.os.SystemClock.elapsedRealtime()-beatStart)/(60000f/bpm));float sway=(float)Math.sin(progress*Math.PI);x+=((position%2==0)?1:-1)*sway*getWidth()*.30f;postInvalidateDelayed(16);}p.setColor(0xff1ab9e6);p.setStrokeWidth(dp(17));c.drawLine(x,0,x,getHeight(),p);}
 }
 void startTunerIfAllowed(){if(listening)return;if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);return;}listening=true;tunerStart.setText("Остановить тюнер");guide.setText("Слушаю гитару…");listenThread=new Thread(this::listen);listenThread.start();}
 @Override public void onRequestPermissionsResult(int requestCode,String[] permissions,int[] results){super.onRequestPermissionsResult(requestCode,permissions,results);if(requestCode==1&&results.length>0&&results[0]==PackageManager.PERMISSION_GRANTED&&tunerTabActive)startTunerIfAllowed();}
 @Override protected void onResume(){super.onResume();handler.postDelayed(()->{if(tunerTabActive&&tunerStart!=null)startTunerIfAllowed();},350);}
 @Override protected void onPause(){stopPitch();practicing=false;stopTuner();ticking=false;setFlash(false);super.onPause();}
 void setupTempoRepeat(Button button,int direction){final Runnable[] task=new Runnable[1];final boolean[] held={false};final long[] started={0};
 task[0]=new Runnable(){public void run(){if(!held[0])return;bpm=Math.max(20,Math.min(500,bpm+direction));tempo.setText(""+bpm);long elapsed=android.os.SystemClock.uptimeMillis()-started[0];handler.postDelayed(this,elapsed>1800?35:elapsed>700?75:160);}};
 button.setOnTouchListener((v,event)->{switch(event.getAction()){case MotionEvent.ACTION_DOWN:held[0]=true;started[0]=android.os.SystemClock.uptimeMillis();bpm=Math.max(20,Math.min(500,bpm+direction));tempo.setText(""+bpm);handler.postDelayed(task[0],400);return true;case MotionEvent.ACTION_UP:case MotionEvent.ACTION_CANCEL:held[0]=false;handler.removeCallbacks(task[0]);return true;}return false;});}
 void setFlash(boolean on){try{if(cameraManager!=null&&flashCameraId!=null)cameraManager.setTorchMode(flashCameraId,on);}catch(Exception ignored){}}
 void refreshStrings(int midi,boolean tuned){if(smartStrings==null)return;int[] tuning=activeTuning;if(tuning==null)return;String[] notes={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};StringBuilder sb=new StringBuilder("СТРУНЫ  ");for(int i=tuning.length-1;i>=0;i--){int n=tuning[i];sb.append(n==midi?(tuned?"✓ ":"◉ "):"○ ").append(notes[(n%12+12)%12]).append(n/12-1).append("  ");}smartStrings.setText(sb.toString());}
 void showTunings(Button instrumentButton){String[] presets={"Стандартный","Drop D (гитара)","Open G (гитара)","DADGAD (гитара)"};java.util.Map<String,?> all=getPreferences(0).getAll();ArrayList<String> choices=new ArrayList<>();for(String p:presets)choices.add(p);for(String key:all.keySet())if(key.startsWith("tuning_"))choices.add("★ "+key.substring(7));new android.app.AlertDialog.Builder(this).setTitle("Избранные строи").setItems(choices.toArray(new String[0]),(d,which)->{String choice=choices.get(which);if(which==0)activeTuning=instrumentMidi[instrumentMode].clone();else if(which==1)activeTuning=new int[]{38,45,50,55,59,64};else if(which==2)activeTuning=new int[]{38,43,50,55,59,62};else if(which==3)activeTuning=new int[]{38,45,50,55,57,62};else{String saved=getPreferences(0).getString("tuning_"+choice.substring(2),"");String[] bits=saved.split(",");int[] values=new int[bits.length];try{for(int i=0;i<bits.length;i++)values[i]=Integer.parseInt(bits[i]);activeTuning=values;}catch(Exception ignored){return;}}instrumentButton.setText(choice);refreshStrings(-1,false);}).show();}
 void playPitch(){try{int rate=22050,length=rate;short[] pcm=new short[length];for(int i=0;i<length;i++){double t=i/(double)rate;pcm[i]=(short)(Math.sin(2*Math.PI*referenceHz*t)*10000);}pitchTrack=new AudioTrack(AudioManager.STREAM_MUSIC,rate,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,length*2,AudioTrack.MODE_STATIC);pitchTrack.write(pcm,0,length);pitchTrack.setLoopPoints(0,length,-1);pitchTrack.play();}catch(Exception ex){stopPitch();}}
 void stopPitch(){if(pitchTrack!=null){try{pitchTrack.stop();}catch(Exception ignored){}pitchTrack.release();pitchTrack=null;}}
 void showPracticeHistory(){String entries=getPreferences(0).getString("practice_history","");int total=getPreferences(0).getInt("practice_total",0);new android.app.AlertDialog.Builder(this).setTitle("Мои занятия").setMessage("Всего: "+total/3600+" ч "+(total%3600)/60+" мин\n\n"+(entries.isEmpty()?"Пока нет сохранённых тренировок":entries)).setPositiveButton("Закрыть",null).show();}
 void showChordLibrary(EditText input){String[] names={"C","Cm","C7","Cmaj7","D","Dm","D7","Dmaj7","E","Em","E7","Emaj7","F","Fm","F7","Fmaj7","G","Gm","G7","Gmaj7","A","Am","A7","Amaj7","B","Bm","B7","Bmaj7","Bb","Bbm","Bb7","Bbmaj7","F#","F#m","F#7","F#maj7","C#","C#m","C#7","C#maj7","Eb","Ebm","Eb7","Ebmaj7","Ab","Abm","Ab7","Abmaj7"};LinearLayout panel=new LinearLayout(this);panel.setOrientation(1);panel.setPadding(dp(12),dp(10),dp(12),dp(10));panel.setBackgroundColor(0xff101c15);EditText search=new EditText(this);search.setSingleLine(true);search.setHint("Поиск: Am, F#, Cmaj7…");search.setTextColor(Color.WHITE);search.setHintTextColor(0xff9eaca4);add(panel,search);ScrollView scroll=new ScrollView(this);panel.addView(scroll,new LinearLayout.LayoutParams(-1,dp(380)));LinearLayout rows=new LinearLayout(this);rows.setOrientation(1);scroll.addView(rows);android.app.AlertDialog dialog=new android.app.AlertDialog.Builder(this).setTitle("Библиотека аккордов").setView(panel).setNegativeButton("Закрыть",null).create();Runnable update=()->{rows.removeAllViews();String filter=search.getText().toString().trim().toLowerCase(Locale.ROOT);for(String name:names){if(!name.toLowerCase(Locale.ROOT).contains(filter))continue;Button item=button(name+"    •    "+(name.contains("maj7")?"Мажорный септаккорд":name.endsWith("m7")?"Минорный септаккорд":name.endsWith("7")?"Септаккорд":name.endsWith("m")?"Минор":"Мажор"));LinearLayout.LayoutParams lp=new LinearLayout.LayoutParams(-1,dp(50));lp.setMargins(0,dp(3),0,dp(3));rows.addView(item,lp);item.setOnClickListener(v->{input.setText(name);chordBoard.showNamedChord(name);dialog.dismiss();});}};search.addTextChangedListener(new android.text.TextWatcher(){public void beforeTextChanged(CharSequence s,int st,int count,int after){}public void onTextChanged(CharSequence s,int st,int before,int count){update.run();}public void afterTextChanged(android.text.Editable e){}});update.run();dialog.show();}
 void recognizeChord(Runnable finished){if(Build.VERSION.SDK_INT>=23&&checkSelfPermission(Manifest.permission.RECORD_AUDIO)!=PackageManager.PERMISSION_GRANTED){requestPermissions(new String[]{Manifest.permission.RECORD_AUDIO},1);recognitionStatus.setText("Разреши микрофон и повтори");finished.run();return;}stopTuner();new Thread(()->{AudioRecord rec=null;try{int rate=22050,n=16384;int buffer=Math.max(n*2,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));rec=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,buffer);if(rec.getState()!=AudioRecord.STATE_INITIALIZED)throw new Exception("Микрофон недоступен");rec.startRecording();short[] samples=new short[n];int offset=0;while(offset<n){int got=rec.read(samples,offset,n-offset);if(got<=0)throw new Exception("Нет звука");offset+=got;}double energy=0;for(short v:samples)energy+=(double)v*v;energy=Math.sqrt(energy/n);if(energy<150)throw new Exception("Слишком тихо — сыграй громче");double[] chroma=new double[12];for(int midi=40;midi<=83;midi++){double hz=440*Math.pow(2,(midi-69)/12.0),re=0,im=0;double step=2*Math.PI*hz/rate;for(int i=0;i<n;i+=2){double window=.5-.5*Math.cos(2*Math.PI*i/(n-1));double angle=step*i;re+=samples[i]*window*Math.cos(angle);im-=samples[i]*window*Math.sin(angle);}chroma[midi%12]+=Math.hypot(re,im);}double max=0;for(double v:chroma)max=Math.max(max,v);if(max<=0)throw new Exception("Не удалось выделить ноты");int bestRoot=0,bestType=0;double best=-1e100,runnerUp=-1e100;int[][] intervals={{0,4,7},{0,3,7},{0,4,7,10},{0,3,7,10},{0,4,7,11}};for(int root=0;root<12;root++)for(int type=0;type<intervals.length;type++){double score=0;for(int pitch=0;pitch<12;pitch++){boolean match=false;for(int iv:intervals[type])if((root+iv)%12==pitch)match=true;score+=chroma[pitch]*(match?1:-.6);}if(score>best){runnerUp=best;best=score;bestRoot=root;bestType=type;}else if(score>runnerUp)runnerUp=score;}String[] roots={"C","C#","D","Eb","E","F","F#","G","Ab","A","Bb","B"},suffix={"","m","7","m7","maj7"};String result=roots[bestRoot]+suffix[bestType];boolean confident=(best-runnerUp)>max*.12;handler.post(()->{recognitionStatus.setText((confident?"Возможно: ":"Неуверенно: ")+result+" • проверь аппликатуру");if(confident)chordBoard.showNamedChord(result);finished.run();});}catch(Exception ex){handler.post(()->{recognitionStatus.setText("Ошибка: "+ex.getMessage());finished.run();});}finally{if(rec!=null){try{rec.stop();}catch(Exception ignored){}rec.release();}}}).start();}
 void stopTuner(){listening=false;if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}}}
 void listen(){int rate=44100,n=8192;int size=Math.max(n*2,AudioRecord.getMinBufferSize(rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT));try{recorder=new AudioRecord(MediaRecorder.AudioSource.MIC,rate,AudioFormat.CHANNEL_IN_MONO,AudioFormat.ENCODING_PCM_16BIT,size);if(recorder.getState()!=AudioRecord.STATE_INITIALIZED)throw new IllegalStateException("Микрофон недоступен");short[] data=new short[n];recorder.startRecording();while(listening){int read=0;while(read<n&&listening){int got=recorder.read(data,read,n-read);if(got<=0)break;read+=got;}if(read<n)continue;double best=0;int lagBest=0;double power=0;for(short x:data)power+=x*(double)x;if(power/n<200000)continue;for(int lag=55;lag<550;lag++){double corr=0;for(int i=0;i<n-lag;i+=2)corr+=data[i]*(double)data[i+lag];if(corr>best){best=corr;lagBest=lag;}}if(lagBest==0)continue;double hz=(double)rate/lagBest;if(hz<75||hz>850)continue;int midi=(int)Math.round(69+12*Math.log(hz/referenceHz)/Math.log(2));String[] names={"C","C♯","D","D♯","E","F","F♯","G","G♯","A","A♯","B"};String[] ruNames={"До","До♯","Ре","Ре♯","Ми","Фа","Фа♯","Соль","Соль♯","Ля","Ля♯","Си"};int ni=(midi%12+12)%12;String name=(russianNotes?ruNames[ni]:names[ni])+(midi/12-1);double target=referenceHz*Math.pow(2,(midi-69)/12.0);double cents=1200*Math.log(hz/target)/Math.log(2);final double shown=hz;final String nn=name;final int[] openStrings=activeTuning==null?instrumentMidi[instrumentMode]:activeTuning;int closest=0;int minDistance=Integer.MAX_VALUE;for(int i=0;i<openStrings.length;i++){int distance=Math.abs(openStrings[i]-midi);if(distance<minDistance){minDistance=distance;closest=i;}}final int stringIndex=closest;final boolean closeToString=minDistance<=2;final String stringLabel=closeToString?"Струна №"+(openStrings.length-stringIndex)+" • "+instrumentNames[instrumentMode]:"Нота вне строя открытых струн";final String message=Math.abs(cents)<8?"✓ Струна настроена":cents<0?"↑ Подтяни струну":"↓ Ослабь струну";handler.post(()->{note.setText(showStringNumber?nn:nn.replaceAll("[0-9]+$",""));if(showStringNumber){stringInfo.setVisibility(View.VISIBLE);stringInfo.setText(stringLabel);}else{stringInfo.setVisibility(View.GONE);stringInfo.setText("");}freq.setText(String.format(Locale.US,"%.1f Hz",shown));guide.setText(message);refreshStrings(midi,Math.abs(cents)<8);gauge.cents=(float)cents;gauge.invalidate();});}}catch(Exception e){handler.post(()->guide.setText("Ошибка микрофона: "+e.getMessage()));}finally{if(recorder!=null){try{recorder.stop();}catch(Exception ignored){}recorder.release();recorder=null;}}}
 void tick(){int beat=0;final int sr=11025;short[] pcm;try{java.io.InputStream in=getAssets().open("metronome_click.gz.b64");java.io.ByteArrayOutputStream encoded=new java.io.ByteArrayOutputStream();byte[] buf=new byte[4096];int n;while((n=in.read(buf))!=-1)encoded.write(buf,0,n);in.close();byte[] compressed=android.util.Base64.decode(encoded.toByteArray(),android.util.Base64.DEFAULT);java.util.zip.GZIPInputStream gz=new java.util.zip.GZIPInputStream(new java.io.ByteArrayInputStream(compressed));java.io.ByteArrayOutputStream raw=new java.io.ByteArrayOutputStream();while((n=gz.read(buf))!=-1)raw.write(buf,0,n);gz.close();byte[] bytes=raw.toByteArray();pcm=new short[bytes.length/2];for(int i=0;i<pcm.length;i++)pcm[i]=(short)((bytes[2*i]&255)|(bytes[2*i+1]<<8));}catch(Exception ex){final int rate=11025;pcm=new short[rate/12];for(int i=0;i<pcm.length;i++){double t=i/(double)rate;double wave=Math.sin(2*Math.PI*260*t)*Math.exp(-t*95);pcm[i]=(short)(wave*17000);}}final int len=pcm.length;
 AudioTrack track=null;try{track=new AudioTrack(AudioManager.STREAM_MUSIC,sr,AudioFormat.CHANNEL_OUT_MONO,AudioFormat.ENCODING_PCM_16BIT,len*2,AudioTrack.MODE_STATIC);track.write(pcm,0,len);
 while(ticking){int current=beat++%beats;if(acceleration&&beat%(beats*accelerationEvery)==0){bpm=Math.min(500,bpm+accelerationStep);handler.post(()->tempo.setText(""+bpm));}if(flashEnabled){setFlash(true);handler.postDelayed(()->setFlash(false),Math.min(70,Math.max(25,30000/bpm)));}handler.post(()->{pulse.setText("● "+(current+1));beatLine.position=current;beatLine.beatStart=android.os.SystemClock.elapsedRealtime();beatLine.invalidate();});track.stop();track.reloadStaticData();track.play();Thread.sleep(Math.max(10,60000/bpm));}
 }catch(Exception ignored){}finally{if(track!=null){try{track.stop();}catch(Exception ignored){}track.release();}setFlash(false);handler.post(()->pulse.setText("●"));}}
 protected void onDestroy(){stopPitch();practicing=false;stopTuner();ticking=false;setFlash(false);super.onDestroy();}
}
