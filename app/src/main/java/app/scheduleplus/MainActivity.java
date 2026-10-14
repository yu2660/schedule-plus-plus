package app.scheduleplus;

import android.Manifest;
import android.app.*;
import android.content.*;
import android.content.pm.PackageManager;
import android.graphics.Color;
import android.graphics.Typeface;
import android.graphics.drawable.GradientDrawable;
import android.net.Uri;
import android.os.*;
import android.provider.Settings;
import android.text.InputType;
import android.view.*;
import android.widget.*;
import java.io.*;
import java.nio.charset.StandardCharsets;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.*;
import java.util.function.Consumer;
import static app.scheduleplus.Models.*;

/** Android-native offline V1. No WebView, remote scripts, analytics or network permission. */
public class MainActivity extends Activity {
    static final int BG=0xffF6F7FB, INK=0xff202337, MUTED=0xff74798F, ACCENT=0xff5856D6;
    static final int[] PASTELS={0xffE9E7FF,0xffDDF2EC,0xffFFECD9,0xffF8E1ED,0xffE0EEFF,0xffF1E9DA};
    static final int[] COLORS={ACCENT,0xff237B66,0xffA35F1C,0xffA94C7C,0xff346EA9,0xff7A6943};
    static final String[] DAYS={"周一","周二","周三","周四","周五","周六","周日"};
    Store store; State state; LinearLayout shell,body; int page=0,week=1; boolean showDone=false,ready=false;
    AlertDialog detailDialog,editorDialog;
    Dialog overviewDialog; OverviewView overviewView;Runnable overviewRefresh;
    ScrollView pageScroll;
    HorizontalScrollView timetableScroll;
    final int[] scrollOffsets=new int[3];
    int renderedPage=-1,timetableOffset=0;
    boolean pageScrollReady=false;
    boolean centerOnEntry;
    View timetableFocusView;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable clockTick=new Runnable(){ public void run(){ if(ready) render(); handler.postDelayed(this,60000); }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new Store(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?Color.WHITE:ACCENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=27?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        try {
            state=store.load(); ready=true;Reminders.ensureChannel(this); week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));
            centerOnEntry=saved==null && !getIntent().hasExtra("taskId") && !getIntent().getBooleanExtra("reminderSettings",false);
            if(saved!=null) { page=saved.getInt("page",0); week=saved.getInt("week",week); showDone=saved.getBoolean("done");int[] offsets=saved.getIntArray("scrollOffsets");if(offsets!=null && offsets.length==3)System.arraycopy(offsets,0,scrollOffsets,0,3);timetableOffset=saved.getInt("timetableOffset",0); }
            render(); openIntent(getIntent());if(saved!=null && saved.getBoolean("overview"))shell.post(()->showOverview());
        } catch(Exception e) { failedLoad(e); }
    }
    @Override protected void onSaveInstanceState(Bundle out) { rememberScroll();super.onSaveInstanceState(out); out.putInt("page",page);out.putInt("week",week);out.putBoolean("done",showDone);out.putIntArray("scrollOffsets",scrollOffsets.clone());out.putInt("timetableOffset",timetableOffset);out.putBoolean("overview",overviewDialog!=null && overviewDialog.isShowing()); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); openIntent(i); }
    void openIntent(Intent i) {
        if(!ready || i==null)return;
        if(i.hasExtra("taskId") || i.getBooleanExtra("reminderSettings",false))centerOnEntry=false;
        if(i.hasExtra("taskId")) {
            String id=i.getStringExtra("taskId");i.removeExtra("taskId");
            try { state=store.load();Semester owner=state.taskOwner(id);if(owner!=null){if(detailDialog!=null)detailDialog.dismiss();if(!state.id.equals(owner.id))switchToSemester(owner.id);Task task=state.task(id);if(task!=null)editTask(task,null);} }
            catch(Exception e){toast(safeMessage(e));}
        }
        if(i.getBooleanExtra("reminderSettings",false)){i.removeExtra("reminderSettings");page=2;render();}
    }
    @Override protected void onResume() {
        super.onResume(); if(ready) { try { state=store.load();if(centerOnEntry && page==0)week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));syncReminders(state);render(); } catch(Exception e) { ready=false; failedLoad(e); } }
        handler.removeCallbacks(clockTick); handler.postDelayed(clockTick,60000);
    }
    @Override protected void onPause(){ super.onPause(); handler.removeCallbacks(clockTick); }
    @Override protected void onStop(){super.onStop();if(ready && page==0 && !isChangingConfigurations() && editorDialog==null && detailDialog==null && overviewDialog==null)centerOnEntry=true;}
    @Override protected void onDestroy(){ready=false;handler.removeCallbacks(clockTick);if(overviewDialog!=null){overviewDialog.setOnDismissListener(null);overviewDialog.dismiss();}if(editorDialog!=null)editorDialog.dismiss();if(detailDialog!=null)detailDialog.dismiss();super.onDestroy();}
    void failedLoad(Exception e) {
        new AlertDialog.Builder(this).setTitle("本地数据读取失败").setMessage("原数据已保留，没有覆盖。可以退出后检查文件，或从你导出的备份恢复。\n"+safeMessage(e))
            .setCancelable(false).setPositiveButton("选择备份",(d,w)->restorePicker()).setNegativeButton("退出",(d,w)->finish()).show();
    }
    String safeMessage(Exception e) { return e.getMessage()==null?"请检查输入内容":e.getMessage(); }
    int dp(float x){ return (int)(x*getResources().getDisplayMetrics().density+0.5f); }
    GradientDrawable background(int color,int radius){ GradientDrawable d=new GradientDrawable(); d.setColor(color);d.setCornerRadius(dp(radius));return d; }
    LinearLayout column(){ LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.VERTICAL);return l; }
    LinearLayout row(){ LinearLayout l=new LinearLayout(this);l.setOrientation(LinearLayout.HORIZONTAL);l.setGravity(Gravity.CENTER_VERTICAL);return l; }
    TextView text(String value,int size,int color){ TextView t=new TextView(this);t.setText(value);t.setTextSize(size);t.setTextColor(color);t.setGravity(Gravity.CENTER_VERTICAL);return t; }
    TextView title(String s,int size){ TextView t=text(s,size,INK);t.setTypeface(Typeface.DEFAULT,Typeface.BOLD);return t; }
    void space(LinearLayout l,int h){ View v=new View(this);l.addView(v,new LinearLayout.LayoutParams(1,dp(h))); }
    void label(LinearLayout l,String s){ TextView t=text(s,12,MUTED);t.setPadding(0,dp(12),0,dp(5));l.addView(t); }
    void weighted(LinearLayout l,View v){ l.addView(v,new LinearLayout.LayoutParams(0,ViewGroup.LayoutParams.WRAP_CONTENT,1)); }
    Button button(String s,Runnable action){ Button b=new Button(this);b.setText(s);b.setTextSize(14);b.setAllCaps(false);b.setTextColor(ACCENT);b.setMinHeight(dp(48));b.setOnClickListener(v->action.run());return b; }
    LinearLayout card(LinearLayout parent){ LinearLayout l=column();l.setPadding(dp(16),dp(14),dp(16),dp(14));l.setBackground(background(Color.WHITE,18));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(-1,-2);p.bottomMargin=dp(12);parent.addView(l,p);return l; }
    void hint(LinearLayout parent,String heading,String detail){ LinearLayout c=card(parent);c.addView(title(heading,17));space(c,8);c.addView(text(detail,14,MUTED)); }
    void toast(String s){ Toast.makeText(this,s,Toast.LENGTH_LONG).show(); }
    boolean commit(Consumer<State> action) {
        try { State old=state; state=store.update(action);syncReminders(old);week=Math.max(1,Math.min(week,state.weeks));render(); return true; }
        catch(Exception e){ toast(safeMessage(e));return false; }
    }
    void syncReminders(State old) { try{Reminders.sync(this,old,state);}catch(Exception e){toast("数据已保存，但提醒安排失败；请在设置中检查提醒状态。");} }
    void rememberScroll(){if(pageScroll!=null && pageScrollReady && renderedPage>=0){scrollOffsets[renderedPage]=pageScroll.getScrollY();if(timetableScroll!=null)timetableOffset=timetableScroll.getScrollX();}}
    void render(){
        if(!ready)return;
        rememberScroll();renderedPage=page;pageScrollReady=false;timetableScroll=null;timetableFocusView=null;
        shell=column();shell.setBackgroundColor(BG);
        shell.setOnApplyWindowInsetsListener((v,in)->{ shell.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in; });
        setContentView(shell); shell.requestApplyInsets();
        LinearLayout head=column();head.setPadding(dp(20),dp(16),dp(20),dp(12));
        TextView brand=text("SCHEDULE++  /  把学习安排好",11,ACCENT);brand.setLetterSpacing(.08f);head.addView(brand);space(head,8);
        head.addView(title(page==0?"我的课程表":page==1?"日程任务":"我的设置",28));
        TextView term=text(state.semester+"  ▾  切换学期",13,ACCENT);term.setMinHeight(dp(48));term.setContentDescription("当前学期："+state.semester+"，点击切换学期");term.setOnClickListener(v->semesterPicker());head.addView(term);
        if(!state.description.isEmpty()){TextView note=text(state.description,12,MUTED);note.setMaxLines(2);head.addView(note);}shell.addView(head);
        ScrollView scroll=page==0?new DirectionLockedScrollView(this):new ScrollView(this);pageScroll=scroll;scroll.setFillViewport(true);body=column();body.setPadding(dp(16),dp(4),dp(16),dp(88));scroll.addView(body);
        FrameLayout viewport=new FrameLayout(this);viewport.addView(scroll,new FrameLayout.LayoutParams(-1,-1));
        TextView add=text("＋",30,Color.WHITE);add.setGravity(Gravity.CENTER);add.setContentDescription("添加任务或课程");add.setBackground(background(ACCENT,28));add.setElevation(dp(6));add.setOnClickListener(v->addMenu());
        FrameLayout.LayoutParams fab=new FrameLayout.LayoutParams(dp(56),dp(56),Gravity.END|Gravity.BOTTOM);fab.setMargins(dp(16),dp(16),dp(20),dp(16));viewport.addView(add,fab);shell.addView(viewport,new LinearLayout.LayoutParams(-1,0,1));
        if(page==0) timetable();else if(page==1) agenda();else settings();
        LinearLayout nav=row();nav.setBackgroundColor(Color.WHITE);String[] tabs={"▦  课程表","✓  日程","⚙  设置"};
        for(int i=0;i<3;i++){final int n=i;Button b=button(tabs[i],()->{page=n;render();});b.setTextColor(i==page?ACCENT:MUTED);b.setBackground(background(i==page?0xffEFEEFF:Color.WHITE,14));LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(0,dp(60),1);p.setMargins(dp(4),dp(6),dp(4),dp(6));nav.addView(b,p);}shell.addView(nav);
        final int pageIndex=page,targetY=scrollOffsets[pageIndex],targetX=timetableOffset;final HorizontalScrollView horizontal=timetableScroll;final View focus=timetableFocusView;
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(pageScroll==scroll && pageScrollReady)scrollOffsets[pageIndex]=y;});
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){
            scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(pageScroll!=scroll)return;
            scroll.scrollTo(0,targetY);if(horizontal!=null)horizontal.scrollTo(targetX,0);
            if(pageIndex==0 && centerOnEntry){centerOnEntry=false;if(focus!=null && horizontal!=null){
                int[] cell=new int[2],vertical=new int[2],across=new int[2];focus.getLocationOnScreen(cell);scroll.getLocationOnScreen(vertical);horizontal.getLocationOnScreen(across);
                int x=horizontal.getScrollX()+cell[0]-across[0]+focus.getWidth()/2-horizontal.getWidth()/2;
                int y=scroll.getScrollY()+cell[1]-vertical[1]+focus.getHeight()/2-scroll.getHeight()/2;
                horizontal.scrollTo(x,0);scroll.scrollTo(0,y);
            }}
            pageScrollReady=true;scrollOffsets[pageIndex]=scroll.getScrollY();if(horizontal!=null)timetableOffset=horizontal.getScrollX();
        }});
        if(overviewRefresh!=null)overviewRefresh.run();
    }
    void bottom(AlertDialog dialog){dialog.show();Window window=dialog.getWindow();if(window!=null){window.setGravity(Gravity.BOTTOM);window.setLayout(-1,-2);}}
    void addMenu(){bottom(new AlertDialog.Builder(this).setTitle("添加到 Schedule++").setItems(new String[]{"添加任务","添加课程"},(d,w)->{if(w==0)editTask(null,null);else editCourse(null);}).create());}
    boolean canReturnToCurrentWeek(){int current=state.currentWeek(LocalDate.now());return current>=1 && current<=state.weeks && week!=current;}
    void showOverview(){
        if(overviewDialog!=null && overviewDialog.isShowing())return;
        Dialog dialog=new Dialog(this,android.R.style.Theme_Material_Light_NoActionBar);overviewDialog=dialog;
        LinearLayout root=column();root.setBackgroundColor(BG);root.setOnApplyWindowInsetsListener((v,in)->{if(Build.VERSION.SDK_INT>=30){android.graphics.Insets edges=in.getInsetsIgnoringVisibility(WindowInsets.Type.systemBars()|WindowInsets.Type.displayCutout());root.setPadding(edges.left,edges.top,edges.right,edges.bottom);}else root.setPadding(in.getSystemWindowInsetLeft(),in.getSystemWindowInsetTop(),in.getSystemWindowInsetRight(),in.getSystemWindowInsetBottom());return in;});
        LinearLayout controls=row();Button previous=button("‹",()->{}),next=button("›",()->{}),select=button("第 "+week+" 周 ▾",()->{}),backToWeek=button("回到本周",()->{});
        Runnable update=()->{select.setText(getString(R.string.overview_week,week));backToWeek.setVisibility(canReturnToCurrentWeek()?View.VISIBLE:View.GONE);previous.setEnabled(week>1);next.setEnabled(week<state.weeks);overviewView.updateDescription();overviewView.invalidate();};
        previous.setContentDescription("总览上一周");next.setContentDescription("总览下一周");select.setContentDescription("总览选择周次");
        previous.setOnClickListener(v->{if(week>1){week--;update.run();}});next.setOnClickListener(v->{if(week<state.weeks){week++;update.run();}});
        select.setOnClickListener(v->{String[] names=new String[state.weeks];for(int i=0;i<names.length;i++)names[i]="第 "+(i+1)+" 周";new AlertDialog.Builder(this).setTitle("总览选择教学周").setSingleChoiceItems(names,week-1,(d,w)->{week=w+1;d.dismiss();update.run();}).setNegativeButton("取消",null).show();});
        controls.addView(previous,new LinearLayout.LayoutParams(dp(48),dp(48)));weighted(controls,select);controls.addView(next,new LinearLayout.LayoutParams(dp(48),dp(48)));controls.addView(button("关闭",dialog::dismiss));root.addView(controls);
        LinearLayout tools=row();backToWeek.setOnClickListener(v->{week=state.currentWeek(LocalDate.now());update.run();});weighted(tools,backToWeek);weighted(tools,button("一键适配",()->overviewView.fit()));root.addView(tools);
        overviewView=new OverviewView(this,(day,section)->{List<Course> found=new ArrayList<>();for(Course c:state.courses)for(Meeting m:c.meetings)if(occurs(m,day,week,section)){found.add(c);break;}if(found.size()==1)courseDetail(found.get(0).id);else if(!found.isEmpty())chooseCourses(found);else editCourseAt(day,section);});
        root.addView(overviewView,new LinearLayout.LayoutParams(-1,0,1));if(Build.VERSION.SDK_INT>=30)dialog.getWindow().setDecorFitsSystemWindows(false);dialog.getWindow().setStatusBarColor(BG);dialog.getWindow().setNavigationBarColor(BG);dialog.getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=27?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));dialog.setContentView(root);dialog.setOnDismissListener(d->{overviewDialog=null;overviewView=null;overviewRefresh=null;render();});dialog.show();dialog.getWindow().setLayout(-1,-1);root.requestApplyInsets();overviewRefresh=update;update.run();
    }
    void timetable(){
        LinearLayout controls=row();weighted(controls,button("‹",()->{if(week>1){week--;render();}}));
        Button selector=button("第 "+week+" 周 ▾",()->{String[] choices=new String[state.weeks];for(int i=0;i<choices.length;i++)choices[i]="第 "+(i+1)+" 周";new AlertDialog.Builder(this).setTitle("选择教学周").setSingleChoiceItems(choices,week-1,(d,w)->{week=w+1;d.dismiss();render();}).show();});
        controls.addView(selector,new LinearLayout.LayoutParams(0,-2,3));weighted(controls,button("›",()->{if(week<state.weeks){week++;render();}}));body.addView(controls);
        int current=state.currentWeek(LocalDate.now());LocalDate monday=LocalDate.parse(state.monday).plusWeeks(week-1);
        LinearLayout dates=row();weighted(dates,text(monday.format(DateTimeFormatter.ofPattern("M月d日"))+" — "+monday.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日")),12,MUTED));if(canReturnToCurrentWeek())dates.addView(button("回到本周",()->{week=current;render();}));body.addView(dates);
        if(current<1 || current>state.weeks)body.addView(text("当前日期不在本学期内，正在查看第 "+week+" 周",12,MUTED));
        body.addView(button("全屏课表总览",this::showOverview));
        if(state.courses.isEmpty()){space(body,12);hint(body,"从第一门课开始","先在设置里调整学期和作息，再添加课程。点击右下角 ＋ 随时记录任务。");body.addView(button("添加第一门课程",()->editCourse(null)));return;}
        // A minimum column width keeps course names readable on narrow screens.
        HorizontalScrollView horizontal=((DirectionLockedScrollView)pageScroll).createHorizontalScroller();timetableScroll=horizontal;horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout table=row();table.setGravity(Gravity.TOP);
        LinearLayout grid=row();grid.setGravity(Gravity.TOP);int cellWidth=Math.max(76,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-80)/(state.display.weekends?7:5));
        LinearLayout axis=column();axis.setContentDescription("固定节次与时间栏");axis.setBackgroundColor(BG);axis.addView(text("节次",11,MUTED),new LinearLayout.LayoutParams(dp(48),dp(48)));
        for(int i=0;i<state.slots.size();i++){Slot p=state.slots.get(i);TextView t=text((i+1)+(state.display.time?"\n"+p.start+"\n"+p.end:""),10,MUTED);t.setGravity(Gravity.CENTER);axis.addView(t,new LinearLayout.LayoutParams(dp(48),dp(66)));}table.addView(axis,new LinearLayout.LayoutParams(dp(48),-2));
        TimetableFocus focus=centerOnEntry && week==state.currentWeek(LocalDate.now())?timetableFocus(state,LocalDateTime.now(),state.display.weekends):null;
        for(int day=1;day<=(state.display.weekends?7:5);day++){
            final int chosenDay=day;LinearLayout col=column();LocalDate date=monday.plusDays(day-1);boolean today=date.equals(LocalDate.now());
            TextView header=text(DAYS[day-1]+"\n"+date.getDayOfMonth(),12,today?ACCENT:MUTED);header.setGravity(Gravity.CENTER);col.addView(header,new LinearLayout.LayoutParams(dp(cellWidth),dp(48)));
            for(CourseBlock block:courseBlocks(state,day,week)){
                final int selectedSection=block.first;List<Course> matching=block.courses;int span=block.last-block.first+1;
                LinearLayout cell=column();cell.setPadding(dp(6),dp(6),dp(4),dp(3));
                if(focus!=null && focus.day==day && focus.first==block.first && focus.last==block.last)timetableFocusView=cell;
                if(!matching.isEmpty()){
                    Course c=matching.get(0);GradientDrawable fill=background(PASTELS[c.color],10);
                    LocalTime now=LocalTime.now();if(today && !now.isBefore(LocalTime.parse(state.slots.get(block.first-1).start)) && now.isBefore(LocalTime.parse(state.slots.get(block.last-1).end)))fill.setStroke(dp(2),ACCENT);
                    cell.setBackground(fill);TextView name=title(c.name,13);name.setTextColor(COLORS[c.color]);name.setMaxLines(Math.max(1,span*2));name.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(name);
                    if(state.display.location && !c.location.isEmpty()){TextView place=text(c.location,11,MUTED);place.setMaxLines(1);place.setEllipsize(android.text.TextUtils.TruncateAt.END);cell.addView(place);}
                    if(state.display.teacher && !c.teacher.isEmpty()){TextView teacher=text(c.teacher,10,MUTED);teacher.setMaxLines(1);cell.addView(teacher);}
                    if(state.display.time){TextView duration=text(state.slots.get(block.first-1).start+"–"+state.slots.get(block.last-1).end,10,MUTED);duration.setMaxLines(1);cell.addView(duration);}
                    if(matching.size()>1)cell.addView(text("重叠 "+matching.size()+" 门",10,COLORS[c.color]));
                    cell.setContentDescription(DAYS[day-1]+" 第"+block.first+"至"+block.last+"节 "+c.name);cell.setOnClickListener(v->{if(matching.size()==1)courseDetail(c.id);else chooseCourses(matching);});
                }else{cell.setBackground(background(today?0xffE8E7F9:0xffEFF1F7,10));cell.setContentDescription(DAYS[day-1]+" 第"+block.first+"节，添加课程");cell.setOnClickListener(v->editCourseAt(chosenDay,selectedSection));}
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(cellWidth-4),dp(66*span-4));p.setMargins(dp(2),dp(2),dp(2),dp(2));col.addView(cell,p);
            }grid.addView(col);
        }horizontal.addView(grid);table.addView(horizontal,new LinearLayout.LayoutParams(0,-2,1));body.addView(table);space(body,12);body.addView(text("横滑看星期 · 竖滑看节次 · 点击课程查看任务",12,MUTED));
    }
    void chooseCourses(List<Course> courses){String[] names=new String[courses.size()];for(int i=0;i<names.length;i++)names[i]=courses.get(i).name;bottom(new AlertDialog.Builder(this).setTitle("这个节次有重叠课程").setItems(names,(d,w)->courseDetail(courses.get(w).id)).create());}
    void agenda(){
        LinearLayout stats=card(body);int pending=0,late=0;for(Task t:state.tasks)if(!t.done){pending++;if(t.deadline().isBefore(LocalDateTime.now()))late++;}
        stats.addView(title(pending+" 项待完成",24));stats.addView(text(late==0?"按自己的节奏，逐项完成":"其中 "+late+" 项已逾期",13,late==0?MUTED:0xffB34053));
        LinearLayout filters=row();weighted(filters,button(showDone?"待完成":"● 待完成",()->{showDone=false;render();}));weighted(filters,button(showDone?"● 已完成":"已完成",()->{showDone=true;render();}));body.addView(filters);
        List<Task> tasks=state.sortedTasks(null,showDone);String last="";
        if(tasks.isEmpty())hint(body,showDone?"还没有完成记录":"暂时没有待办","点击右下角 ＋ 添加作业、报告、考试或个人安排。");
        for(Task t:tasks){String group=showDone?t.date:taskGroup(t,LocalDateTime.now());if(!group.equals(last)){last=group;label(body,showDone?dateLabel(t):group);}taskCard(body,t,null);}
    }
    String dateLabel(Task t){LocalDate date=LocalDate.parse(t.date);String lead=date.equals(LocalDate.now())?"今天 · ":date.equals(LocalDate.now().plusDays(1))?"明天 · ":date.isBefore(LocalDate.now())?"已过期 · ":"";return lead+date.format(DateTimeFormatter.ofPattern("M月d日"))+" "+DAYS[date.getDayOfWeek().getValue()-1];}
    void taskCard(LinearLayout parent,Task t,Dialog owner){
        taskCard(parent,t,owner,null);
    }
    void taskCard(LinearLayout parent,Task t,Dialog owner,Runnable refresh){
        LinearLayout c=card(parent),line=row();CheckBox done=new CheckBox(this);done.setChecked(t.done);done.setContentDescription(t.done?"恢复任务："+t.title:"完成任务："+t.title);
        done.setOnClickListener(v->{boolean value=done.isChecked();if(commit(s->{Task x=s.task(t.id);if(x!=null)x.done=value;})){if(refresh!=null)refresh.run();}else done.setChecked(t.done);});line.addView(done);
        TextView name=title(t.title,16);weighted(line,name);c.addView(line);
        Course course=state.course(t.courseId);TextView meta=text((course==null?"个人任务":course.name)+"  ·  "+t.date+(t.time==null?" 截止":" "+t.time+" 截止"),12,!t.done && t.deadline().isBefore(LocalDateTime.now())?0xffB34053:MUTED);c.addView(meta);
        c.addView(text(t.done?"已完成":relativeDeadline(t,LocalDateTime.now()),12,!t.done && t.deadline().isBefore(LocalDateTime.now())?0xffB34053:MUTED));
        if(!t.notes.isEmpty()){space(c,7);TextView notes=text(t.notes,13,MUTED);notes.setMaxLines(3);c.addView(notes);}
        if(t.reminder!=null){space(c,5);c.addView(text((t.reminded?"已提醒 ":"提醒 ")+t.reminder.replace('T',' '),11,ACCENT));}
        c.setOnClickListener(v->{AlertDialog editor=editTask(t,null);if(owner!=null)keepParent(editor,owner,refresh);});
    }
    void courseDetail(String id){
        Course c=state.course(id);if(c==null)return;
        LinearLayout content=column();content.setPadding(dp(8),dp(8),dp(8),0);content.addView(text(c.location+(c.teacher.isEmpty()?"":" · "+c.teacher),14,MUTED));
        for(Meeting m:c.meetings)content.addView(text(meetingLabel(m),12,MUTED));space(content,12);
        ScrollView scroll=new ScrollView(this);scroll.addView(content);
        AlertDialog dialog=new AlertDialog.Builder(this).setTitle(c.name).setView(scroll).setPositiveButton("添加任务",null).setNeutralButton("编辑课程",null).setNegativeButton("关闭",null).create();
        content.addView(title("所有未完成任务",17));space(content,10);LinearLayout tasks=column();content.addView(tasks);refreshCourseTasks(tasks,c.id,false,dialog);
        Button completed=button("查看已完成记录",()->completedForCourse(c.id));content.addView(completed);showDetail(dialog);
        Runnable refresh=()->{if(state.course(c.id)==null)dialog.dismiss();else courseDetail(c.id);};
        dialog.getButton(AlertDialog.BUTTON_POSITIVE).setOnClickListener(v->keepParent(editTask(null,c.id),dialog,refresh));
        dialog.getButton(AlertDialog.BUTTON_NEUTRAL).setOnClickListener(v->keepParent(editCourse(state.course(c.id)),dialog,refresh));
    }
    void completedForCourse(String id){LinearLayout l=column();l.setPadding(dp(16),dp(8),dp(16),0);ScrollView scroll=new ScrollView(this);scroll.addView(l);AlertDialog d=new AlertDialog.Builder(this).setTitle("已完成记录").setView(scroll).setPositiveButton("关闭",null).setNeutralButton("返回课程",(x,w)->courseDetail(id)).create();refreshCourseTasks(l,id,true,d);showDetail(d);}
    void showDetail(AlertDialog dialog){if(detailDialog!=null)detailDialog.dismiss();detailDialog=dialog;dialog.setOnDismissListener(d->{if(detailDialog==dialog)detailDialog=null;});bottom(dialog);Window window=dialog.getWindow();if(window!=null)window.setLayout(-1,(int)(getResources().getDisplayMetrics().heightPixels*.82f));}
    void keepParent(AlertDialog editor,Dialog parent,Runnable refresh){State before=state;editor.setOnDismissListener(d->{if(editorDialog==editor)editorDialog=null;if(!isFinishing() && !isDestroyed() && parent.isShowing() && state!=before && refresh!=null)refresh.run();});}
    void refreshCourseTasks(LinearLayout list,String id,boolean completed,AlertDialog owner){list.removeAllViews();List<Task> tasks=state.sortedTasks(id,completed);if(tasks.isEmpty())list.addView(text(completed?"暂无已完成记录":"这门课暂时没有未完成任务",14,MUTED));for(Task t:tasks)taskCard(list,t,owner,()->refreshCourseTasks(list,id,completed,owner));}
    String meetingLabel(Meeting m){return DAYS[m.day-1]+" · 第 "+m.first+"–"+m.last+" 节 · 周次 "+joinWeeks(m.weeks);}
    String joinWeeks(Set<Integer> weeks){StringBuilder b=new StringBuilder();for(int w:weeks){if(b.length()>0)b.append('、');b.append(w);}return b.toString();}
    LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(4),dp(20),dp(20));return f;}
    EditText field(LinearLayout f,String label,String value,int lines){label(f,label);EditText e=new EditText(this);e.setText(value);e.setTextSize(15);e.setSingleLine(lines==1);if(lines>1){e.setMinLines(lines);e.setGravity(Gravity.TOP);}f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    AlertDialog formDialog(String title,LinearLayout form,Runnable save){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(form);AlertDialog previous=editorDialog;AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(x->{editorDialog=d;d.getWindow().setLayout(-1,-1);d.getWindow().setSoftInputMode(WindowManager.LayoutParams.SOFT_INPUT_ADJUST_RESIZE);d.getButton(-1).setOnClickListener(v->save.run());});d.setOnDismissListener(x->{if(editorDialog==d)editorDialog=previous!=null && previous.isShowing()?previous:null;});return d;}
    void pickDate(LocalDate initial,Consumer<LocalDate> result){new DatePickerDialog(this,(v,y,m,d)->result.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    void pickTime(LocalTime initial,Consumer<LocalTime> result){new TimePickerDialog(this,(v,h,m)->result.accept(LocalTime.of(h,m)),initial.getHour(),initial.getMinute(),true).show();}
    void pickDateTime(LocalDateTime initial,Consumer<LocalDateTime> result){pickDate(initial.toLocalDate(),d->pickTime(initial.toLocalTime(),t->result.accept(d.atTime(t))));}
    AlertDialog editTask(Task original,String preselected){
        Task draft=new Task();if(original!=null){draft.id=original.id;draft.title=original.title;draft.courseId=original.courseId;draft.date=original.date;draft.time=original.time;draft.notes=original.notes;draft.reminder=original.reminder;draft.done=original.done;draft.reminded=original.reminded;draft.reminderMinutes=original.reminderMinutes;}
        else{draft.courseId=preselected;draft.date=LocalDate.now().toString();}
        final Integer[] relative={draft.reminderMinutes<0?null:draft.reminderMinutes};LinearLayout f=form();EditText name=field(f,"任务标题 *",draft.title,1);
        Button template=button("使用任务模板",()->{});f.addView(template);
        label(f,"关联课程（可选）");Spinner courses=new Spinner(this);List<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add(null);names.add("个人任务 · 不关联课程");for(Course c:state.courses){ids.add(c.id);names.add(c.name);}courses.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));courses.setSelection(Math.max(0,ids.indexOf(draft.courseId)));f.addView(courses);
        Button date=button(draft.date,()->{}),time=button(draft.time==null?"＋ 添加具体时间":draft.time,()->{}),reminder=button("",()->{});
        Runnable refresh=()->{if(relative[0]!=null){String next=relativeReminder(draft.date,draft.time,relative[0]).withSecond(0).withNano(0).toString();if(!Objects.equals(next,draft.reminder))draft.reminded=false;draft.reminder=next;}date.setText(draft.date);time.setText(draft.time==null?"＋ 添加具体时间":draft.time);reminder.setText(draft.reminder==null?"＋ 自定义提醒时间":draft.reminder.replace('T',' ')+(relative[0]==null?"":"（截止前 "+relative[0]+" 分钟）"));};
        label(f,"截止日期 * / 时间选填");date.setOnClickListener(v->pickDate(LocalDate.parse(draft.date),d->{draft.date=d.toString();refresh.run();}));f.addView(date);
        LinearLayout quickDates=row();String[] labels={"今天","明天","一周后"};int[] offsets={0,1,7};for(int i=0;i<labels.length;i++){final int offset=offsets[i];weighted(quickDates,button(labels[i],()->{draft.date=LocalDate.now().plusDays(offset).toString();refresh.run();}));}f.addView(quickDates);
        LinearLayout timeRow=row();time.setOnClickListener(v->pickTime(draft.time==null?LocalTime.of(23,59):LocalTime.parse(draft.time),t->{draft.time=t.toString();refresh.run();}));weighted(timeRow,time);timeRow.addView(button("清除时间",()->{draft.time=null;refresh.run();}));f.addView(timeRow);
        EditText notes=field(f,"补充说明",draft.notes,3);
        label(f,"提醒（可选）");Runnable custom=()->pickDateTime(draft.reminder==null?LocalDateTime.now().plusHours(1).withSecond(0).withNano(0):LocalDateTime.parse(draft.reminder),r->{relative[0]=null;draft.reminder=r.toString();draft.reminded=false;refresh.run();});
        LinearLayout presets=row();weighted(presets,button("截止前1天",()->{relative[0]=1440;refresh.run();}));weighted(presets,button("前1小时",()->{relative[0]=60;refresh.run();}));weighted(presets,button("自定义",custom));f.addView(presets);reminder.setOnClickListener(v->custom.run());f.addView(reminder);
        f.addView(button("不提醒",()->{relative[0]=null;draft.reminder=null;draft.reminded=false;refresh.run();}));
        template.setOnClickListener(v->{if(state.templates.isEmpty()){toast("请先在设置中添加任务模板");return;}String[] choices=new String[state.templates.size()];for(int i=0;i<choices.length;i++)choices[i]=state.templates.get(i).name;bottom(new AlertDialog.Builder(this).setTitle("选择任务模板").setItems(choices,(d,w)->{TaskTemplate t=state.templates.get(w);Runnable apply=()->{name.setText(t.prefix);notes.setText(t.notes);relative[0]=t.reminderMinutes<0?null:t.reminderMinutes;draft.reminder=null;draft.reminded=false;refresh.run();};if(name.length()>0 || notes.length()>0)confirm("使用「"+t.name+"」？","将替换当前标题、说明和提醒选项，保留日期与关联课程。",apply);else apply.run();}).setNegativeButton("取消",null).create());});
        f.addView(text("未填写截止时间时按当天结束排序；快捷提醒按当日 23:59 计算，改动截止日期或时间会同步调整相对提醒。",12,MUTED));refresh.run();
        final AlertDialog[] dialog={null};dialog[0]=formDialog(original==null?"添加任务":"编辑任务",f,()->{
            draft.title=name.getText().toString().trim();draft.notes=notes.getText().toString();draft.courseId=ids.get(courses.getSelectedItemPosition());draft.reminderMinutes=relative[0]==null?-1:relative[0];
            if(draft.reminder!=null && !draft.reminded && draft.reminderMillis()<=System.currentTimeMillis()){toast("提醒时间已过去，请调整截止时间、提醒选项，或选择不提醒");return;}
            if(commit(s->{s.tasks.removeIf(t->t.id.equals(draft.id));s.tasks.add(draft);})){dialog[0].dismiss();if(draft.reminder!=null && !draft.done)ensureNotifications();}
        });
        f.addView(button("保存为任务模板",()->{AlertDialog parent=dialog[0];AlertDialog child=editTemplate(null,name.getText().toString(),notes.getText().toString(),relative[0]==null?-1:relative[0]);child.setOnDismissListener(d->{editorDialog=parent.isShowing()?parent:null;});}));
        if(original!=null)f.addView(button("删除任务",()->confirm("删除这条任务？","删除后无法撤销。",()->{if(commit(s->s.tasks.removeIf(t->t.id.equals(draft.id))))dialog[0].dismiss();})));
        dialog[0].show();return dialog[0];
    }
    void displayOptions(){
        String[] labels={"显示周末","显示教师","显示地点","显示课程及节次时间"};boolean[] selected={state.display.weekends,state.display.teacher,state.display.location,state.display.time};
        new AlertDialog.Builder(this).setTitle("课表显示内容").setMultiChoiceItems(labels,selected,(d,w,checked)->selected[w]=checked).setPositiveButton("保存",(d,w)->{if(commit(s->{s.display.weekends=selected[0];s.display.teacher=selected[1];s.display.location=selected[2];s.display.time=selected[3];}))if(overviewView!=null)overviewView.fit();}).setNegativeButton("取消",null).show();
    }
    void templateManager(){String[] labels=new String[state.templates.size()+1];for(int i=0;i<state.templates.size();i++)labels[i]=state.templates.get(i).name;labels[labels.length-1]="＋ 新建模板";bottom(new AlertDialog.Builder(this).setTitle("管理任务模板").setItems(labels,(d,w)->{if(w==state.templates.size())editTemplate(null,"","",-1);else{TaskTemplate t=state.templates.get(w);editTemplate(t,t.prefix,t.notes,t.reminderMinutes);}}).setNegativeButton("关闭",null).create());}
    AlertDialog editTemplate(TaskTemplate original,String prefix,String content,int minutes){
        LinearLayout f=form();EditText name=field(f,"模板名称 *",original==null?"":original.name,1),title=field(f,"标题 / 前缀",prefix,1),notes=field(f,"默认补充说明",content,3),advance=field(f,"截止前几分钟提醒（留空不提醒，最多10080）",minutes<0?"":String.valueOf(minutes),1);advance.setInputType(InputType.TYPE_CLASS_NUMBER);
        f.addView(text("模板只保存标题、说明与相对提醒习惯，不保存课程、日期或完成状态。",12,MUTED));final AlertDialog[] dialog={null};
        dialog[0]=formDialog(original==null?"新建任务模板":"编辑任务模板",f,()->{try{TaskTemplate t=new TaskTemplate();if(original!=null)t.id=original.id;t.name=name.getText().toString().trim();t.prefix=title.getText().toString();t.notes=notes.getText().toString();String value=advance.getText().toString().trim();t.reminderMinutes=value.isEmpty()?-1:Integer.parseInt(value);if(commit(s->{s.templates.removeIf(x->x.id.equals(t.id));s.templates.add(t);}))dialog[0].dismiss();}catch(Exception e){toast(safeMessage(e));}});
        if(original!=null)f.addView(button("删除模板",()->confirm("删除模板？","已创建的任务不受影响。",()->{if(commit(s->s.templates.removeIf(t->t.id.equals(original.id))))dialog[0].dismiss();})));
        dialog[0].show();return dialog[0];
    }
    void ensureNotifications(){
        if(Build.VERSION.SDK_INT>=33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS)!=PackageManager.PERMISSION_GRANTED)requestPermissions(new String[]{Manifest.permission.POST_NOTIFICATIONS},70);
        else if(!Reminders.allowed(this))toast("任务已保存，应用通知或任务提醒渠道未开启。可在设置中检查。");
        else if(Build.VERSION.SDK_INT>=31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms())toast("已保存提醒。当前为普通提醒，可能延迟；可在设置中启用准时提醒。");
    }
    @Override public void onRequestPermissionsResult(int request,String[] permissions,int[] results){super.onRequestPermissionsResult(request,permissions,results);if(request==70){if(results.length==0 || results[0]!=PackageManager.PERMISSION_GRANTED)toast("任务已保存，未获通知权限，提醒不会显示。");else if(Build.VERSION.SDK_INT>=31 && !getSystemService(AlarmManager.class).canScheduleExactAlarms())toast("通知已开启。普通提醒可能延迟，可在设置中启用准时提醒。");if(ready){syncReminders(state);render();}}}
    void editCourseAt(int day,int section){Course c=new Course();c.color=state.courses.size()%6;Meeting m=new Meeting();m.day=day;m.first=section;m.last=section;for(int i=1;i<=state.weeks;i++)m.weeks.add(i);c.meetings.add(m);editCourse(c);}
    AlertDialog editCourse(Course original){
        Course draft=new Course();draft.color=state.courses.size()%6;
        if(original!=null){draft.id=original.id;draft.name=original.name;draft.location=original.location;draft.teacher=original.teacher;draft.color=original.color;for(Meeting m:original.meetings){Meeting n=new Meeting();n.day=m.day;n.first=m.first;n.last=m.last;n.weeks.addAll(m.weeks);draft.meetings.add(n);}}
        boolean existing=state.course(draft.id)!=null;LinearLayout f=form();EditText name=field(f,"课程名称 *",draft.name,1),location=field(f,"上课地点",draft.location,1),teacher=field(f,"教师（可选）",draft.teacher,1);
        label(f,"课程颜色");Spinner color=new Spinner(this);color.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,new String[]{"鸢尾紫","薄荷绿","杏子橙","蔷薇粉","晴空蓝","燕麦棕"}));color.setSelection(draft.color);f.addView(color);
        label(f,"上课时段 *（可添加多个）");LinearLayout list=column();f.addView(list);refreshMeetings(list,draft);
        f.addView(button("＋ 添加上课时段",()->meetingEditor(draft.meetings.isEmpty()?null:draft.meetings.get(draft.meetings.size()-1),m->{draft.meetings.add(m);refreshMeetings(list,draft);})));
        final AlertDialog[] dialog={null};dialog[0]=formDialog(existing?"编辑课程":"添加课程",f,()->{
            draft.name=name.getText().toString().trim();draft.location=location.getText().toString().trim();draft.teacher=teacher.getText().toString().trim();draft.color=color.getSelectedItemPosition();
            Runnable save=()->{if(commit(s->{s.courses.removeIf(c->c.id.equals(draft.id));s.courses.add(draft);}))dialog[0].dismiss();};
            boolean clash=false;for(Meeting m:draft.meetings){for(Course c:state.courses)if(!c.id.equals(draft.id))for(Meeting n:c.meetings)if(conflicts(m,n))clash=true;}
            for(int i=0;i<draft.meetings.size();i++)for(int j=i+1;j<draft.meetings.size();j++)if(conflicts(draft.meetings.get(i),draft.meetings.get(j)))clash=true;
            if(clash)confirm("检测到课程时间重叠","仍然保存吗？课表会显示重叠标记。",save);else save.run();
        });
        if(existing)f.addView(button("复制为新课程",()->{draft.name=name.getText().toString().trim();draft.location=location.getText().toString().trim();draft.teacher=teacher.getText().toString().trim();draft.color=color.getSelectedItemPosition();Course copy=duplicateCourse(draft);copy.name=copy.name+"（副本）";dialog[0].dismiss();editCourse(copy);}));
        if(existing)f.addView(button("删除课程",()->confirm("删除「"+original.name+"」？","相关任务会保留，并转为不关联课程的个人任务。",()->{if(commit(s->{s.courses.removeIf(c->c.id.equals(draft.id));for(Task t:s.tasks)if(draft.id.equals(t.courseId))t.courseId=null;}))dialog[0].dismiss();})));
        dialog[0].show();return dialog[0];
    }
    void refreshMeetings(LinearLayout list,Course draft){list.removeAllViews();for(Meeting m:draft.meetings){LinearLayout c=card(list);c.addView(text(meetingLabel(m),13,INK));LinearLayout actions=row();weighted(actions,button("修改",()->meetingEditor(m,n->{draft.meetings.set(draft.meetings.indexOf(m),n);refreshMeetings(list,draft);})));weighted(actions,button("复制",()->meetingEditor(m,n->{draft.meetings.add(n);refreshMeetings(list,draft); })));weighted(actions,button("移除",()->{draft.meetings.remove(m);refreshMeetings(list,draft);}));c.addView(actions);}}
    Spinner spinner(LinearLayout f,String label,String[] choices,int selected){label(f,label);Spinner s=new Spinner(this);s.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,choices));s.setSelection(selected);f.addView(s);return s;}
    void meetingEditor(Meeting old,Consumer<Meeting> save){
        LinearLayout f=form();Spinner day=spinner(f,"星期",DAYS,old==null?0:old.day-1);String[] sections=new String[state.slots.size()];for(int i=0;i<sections.length;i++)sections[i]="第 "+(i+1)+" 节 · "+state.slots.get(i).start;
        Spinner first=spinner(f,"开始节次",sections,old==null?0:old.first-1),last=spinner(f,"结束节次",sections,old==null?Math.min(1,sections.length-1):old.last-1);
        label(f,"适用教学周（可逐周选择）");CheckBox[] checks=new CheckBox[state.weeks];LinearLayout quick=row();f.addView(quick);
        String[] modes={"全部","单周","双周","清空"};for(int k=0;k<modes.length;k++){final int mode=k;weighted(quick,button(modes[k],()->{for(int i=0;i<checks.length;i++)checks[i].setChecked(mode==0 || mode==1 && i%2==0 || mode==2 && i%2==1);}));}
        for(int i=0;i<state.weeks;i+=4){LinearLayout r=row();for(int j=i;j<Math.min(i+4,state.weeks);j++){checks[j]=new CheckBox(this);checks[j].setText(String.valueOf(j+1));checks[j].setChecked(old==null || old.weeks.contains(j+1));weighted(r,checks[j]);}f.addView(r);}
        final AlertDialog[] dialog={null};dialog[0]=formDialog("上课时段",f,()->{Meeting m=new Meeting();m.day=day.getSelectedItemPosition()+1;m.first=first.getSelectedItemPosition()+1;m.last=last.getSelectedItemPosition()+1;for(int i=0;i<checks.length;i++)if(checks[i].isChecked())m.weeks.add(i+1);if(m.last<m.first || m.weeks.isEmpty()){toast("结束节次不能早于开始节次，且至少选择一周");return;}save.accept(m);dialog[0].dismiss();});dialog[0].show();
    }
    void settings(){
        LinearLayout semester=card(body);semester.addView(title(state.semester,20));space(semester,6);semester.addView(text("第 1 周周一："+state.monday+"\n共 "+state.weeks+" 周 · 每日最多 "+state.slots.size()+" 节",14,MUTED));semester.addView(button("学期与作息设置",()->editSemester()));
        if(!state.description.isEmpty())semester.addView(text(state.description,13,MUTED));semester.addView(button("切换学期（共 "+state.allSemesters().size()+" 个）",()->semesterPicker()));semester.addView(button("新建学期",()->newSemester()));
        if(!state.otherSemesters.isEmpty())semester.addView(button("删除当前学期",()->confirm("删除「"+state.semester+"」？","该学期的课程、任务和提醒会一并删除，其他学期保留。请先导出备份。",()->{if(commit(State::deleteCurrentSemester)){week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));render();}})));
        LinearLayout courses=card(body);courses.addView(title("课程管理",18));for(Course c:state.courses)courses.addView(button(c.name+"  ›",()->editCourse(c)));courses.addView(button("＋ 添加课程",()->editCourse(null)));
        LinearLayout reminders=card(body);reminders.addView(title("任务提醒",18));space(reminders,8);boolean exact=Build.VERSION.SDK_INT<31 || getSystemService(AlarmManager.class).canScheduleExactAlarms();reminders.addView(text(Reminders.status(this)+"\n"+(exact?"准时提醒已开启":"未开启准时提醒，系统可能延迟送达"),13,MUTED));
        reminders.addView(button("管理通知权限",()->{Intent i=new Intent(Settings.ACTION_APP_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName());startActivity(i);}));
        if(Build.VERSION.SDK_INT>=31)reminders.addView(button("管理准时提醒权限",()->startActivity(new Intent(Settings.ACTION_REQUEST_SCHEDULE_EXACT_ALARM,Uri.parse("package:"+getPackageName())))));
        reminders.addView(button("管理任务提醒渠道",()->startActivity(new Intent(Settings.ACTION_CHANNEL_NOTIFICATION_SETTINGS).putExtra(Settings.EXTRA_APP_PACKAGE,getPackageName()).putExtra(Settings.EXTRA_CHANNEL_ID,Reminders.CHANNEL))));
        reminders.addView(button("发送测试通知",()->{if(Reminders.allowed(this)){Reminders.testNotification(this);toast("已发送测试通知，请查看通知栏。");render();}else{ensureNotifications();toast("请先开启应用通知与任务提醒渠道，然后重新点击测试。");}}));
        reminders.addView(button("安排一分钟后测试提醒",()->{Task test=new Task();test.title="一分钟提醒测试";test.date=LocalDate.now().plusDays(1).toString();test.reminder=LocalDateTime.now().plusMinutes(1).withNano(0).toString();test.notes="由提醒测试创建，可测试后删除。";if(commit(s->s.tasks.add(test))){ensureNotifications();toast("已添加测试任务，提醒时间："+test.reminder.replace('T',' '));}}));
        reminders.addView(text(Reminders.diagnostics(this),12,MUTED));
        reminders.addView(text("所有学期的提醒都会安排。重启或重新打开后，会补发过去 24 小时内未送达的提醒。系统强制停止应用后，需要重新打开。",12,MUTED));
        LinearLayout preferences=card(body);preferences.addView(title("课表与任务偏好",18));preferences.addView(button("课表显示内容",this::displayOptions));preferences.addView(button("管理任务模板",this::templateManager));
        LinearLayout backup=card(body);backup.addView(title("备份与恢复",18));space(backup,8);backup.addView(text("备份包含全部学期、课程和任务，兼容恢复旧版备份。文件为明文 JSON，请保存在你信任的位置。",13,MUTED));backup.addView(button("导出全部学期备份",()->exportPicker()));backup.addView(button("从备份恢复",()->restorePicker()));
        hint(body,"Schedule++  0.3.1","安卓原生 · 数据保存在本机\n教务导入将在后续版本考虑。\n参考：拾光课程表（Apache-2.0）；详见项目开源说明。");
        body.addView(button("开源说明与许可证",()->{
            try {
                StringBuilder notices=new StringBuilder();
                for(String asset:new String[]{"THIRD_PARTY_NOTICES.txt","APACHE-2.0.txt","MIT-android-week-view.txt"})try(InputStream in=getAssets().open(asset)){notices.append(Store.read(in)).append("\n\n");}
                TextView content=text(notices.toString(),12,INK);content.setPadding(dp(18),dp(8),dp(18),dp(8));content.setTextIsSelectable(true);ScrollView scroll=new ScrollView(this);scroll.addView(content);
                new AlertDialog.Builder(this).setTitle("开源说明与许可证").setView(scroll).setPositiveButton("关闭",null).show();
            } catch(Exception e){toast("无法读取开源说明");}
        }));
    }
    void editSemester(){
        LinearLayout f=form();EditText name=field(f,"学期名称",state.semester,1),description=field(f,"学期备注（可选）",state.description,2);final String[] monday={state.monday};label(f,"第一教学周的周一");Button start=button(monday[0],()->{});start.setOnClickListener(v->pickDate(LocalDate.parse(monday[0]),d->{if(d.getDayOfWeek()!=DayOfWeek.MONDAY){toast("请选择第一教学周的周一");return;}monday[0]=d.toString();start.setText(monday[0]);}));f.addView(start);
        EditText weeks=field(f,"教学周总数（1–60）",String.valueOf(state.weeks),1);weeks.setInputType(InputType.TYPE_CLASS_NUMBER);
        EditText count=field(f,"每日课程节数上限（1–24）",String.valueOf(state.slots.size()),1);count.setInputType(InputType.TYPE_CLASS_NUMBER);
        label(f,"每节课起止时间（按顺序设置，不可重叠）");LinearLayout times=column();f.addView(times);List<Slot> slots=new ArrayList<>();for(Slot p:state.slots)slots.add(new Slot(p.start,p.end));renderSlots(times,slots);
        f.addView(button("应用节数并编辑作息",()->{try{int n=Integer.parseInt(count.getText().toString());check(n>=1 && n<=MAX_PERIODS,"节数需为 1–24");while(slots.size()>n)slots.remove(slots.size()-1);while(slots.size()<n){LocalTime t=LocalTime.parse(slots.get(slots.size()-1).end).plusMinutes(10);slots.add(new Slot(t.toString(),t.plusMinutes(45).toString()));}renderSlots(times,slots);}catch(Exception e){toast(safeMessage(e));}}));
        f.addView(text("减少周数或节数时，若已有课程超出范围，请先修改课程，避免丢失排课。",12,MUTED));
        final AlertDialog[] dialog={null};dialog[0]=formDialog("学期与作息",f,()->{try{int n=Integer.parseInt(count.getText().toString()),w=Integer.parseInt(weeks.getText().toString());if(n!=slots.size()){toast("请先点击「应用节数并编辑作息」");return;}if(commit(s->{s.semester=name.getText().toString().trim();s.description=description.getText().toString().trim();s.monday=monday[0];s.weeks=w;s.slots.clear();s.slots.addAll(slots);}))dialog[0].dismiss();}catch(Exception e){toast(safeMessage(e));}});dialog[0].show();
    }
    void semesterPicker(){List<Semester> terms=state.allSemesters();String[] names=new String[terms.size()];for(int i=0;i<names.length;i++)names[i]=(terms.get(i).id.equals(state.id)?"当前 · ":"")+terms.get(i).semester+"（"+terms.get(i).monday+" 起）";new AlertDialog.Builder(this).setTitle("切换学期").setSingleChoiceItems(names,0,(d,w)->{d.dismiss();switchToSemester(terms.get(w).id);}).setPositiveButton("新建学期",(d,w)->newSemester()).setNegativeButton("取消",null).show();}
    void switchToSemester(String id){if(commit(s->s.switchSemester(id))){week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));showDone=false;render();}}
    void newSemester(){
        Semester term=new Semester();LinearLayout f=form();EditText name=field(f,"学期名称 *","新学期",1),note=field(f,"备注（可选）","",2);
        List<Semester> sources=state.allSemesters();String[] choices=new String[sources.size()+1];choices[0]="空白学期（默认作息）";for(int i=0;i<sources.size();i++)choices[i+1]=sources.get(i).semester;
        Spinner source=spinner(f,"复用哪个学期的作息",choices,1);CheckBox copyCourses=new CheckBox(this);copyCourses.setText("同时复制课程（不复制任务和提醒）");f.addView(copyCourses);
        label(f,"第一教学周的周一");Button date=button(term.monday,()->{});date.setOnClickListener(v->pickDate(LocalDate.parse(term.monday),d->{if(d.getDayOfWeek()!=DayOfWeek.MONDAY){toast("请选择周一");return;}term.monday=d.toString();date.setText(term.monday);}));f.addView(date);
        EditText weeks=field(f,"教学周总数（1–60）",String.valueOf(state.weeks),1);weeks.setInputType(InputType.TYPE_CLASS_NUMBER);
        source.setOnItemSelectedListener(new AdapterView.OnItemSelectedListener(){public void onNothingSelected(AdapterView<?> p){}public void onItemSelected(AdapterView<?> p,View v,int index,long id){weeks.setText(String.valueOf(index==0?20:sources.get(index-1).weeks));copyCourses.setEnabled(index!=0);if(index==0)copyCourses.setChecked(false);}});
        f.addView(text("预览后确认创建。复制课程时周次按新学期范围保留，范围外的周次和无有效周次的课程不会复制；原学期保持独立。",13,MUTED));
        final AlertDialog[] dialog={null};dialog[0]=formDialog("新建学期",f,()->{try{
            Semester from=source.getSelectedItemPosition()==0?null:sources.get(source.getSelectedItemPosition()-1);
            Semester next=reuseSemester(from,name.getText().toString().trim(),note.getText().toString().trim(),term.monday,Integer.parseInt(weeks.getText().toString()),copyCourses.isChecked());
            String preview=next.semester+"\n"+next.weeks+" 周 · 每日 "+next.slots.size()+" 节 · "+next.courses.size()+" 门课程\n不复制任务和提醒"+(from!=null && copyCourses.isChecked()?"\n原学期 "+from.courses.size()+" 门课程；超出新学期范围的周次不保留":"");
            confirm("确认创建学期？",preview,()->{if(commit(s->s.addSemester(next))){dialog[0].dismiss();week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));showDone=false;render();}});
        }catch(Exception e){toast(safeMessage(e));}});dialog[0].show();dialog[0].getButton(-1).setText("预览并创建");
    }
    void renderSlots(LinearLayout list,List<Slot> slots){list.removeAllViews();for(int i=0;i<slots.size();i++){final Slot p=slots.get(i);LinearLayout r=row();r.addView(text("第 "+(i+1)+" 节",13,MUTED));Button start=button(p.start,()->{}),end=button(p.end,()->{});start.setOnClickListener(v->pickTime(LocalTime.parse(p.start),t->{p.start=t.toString();start.setText(p.start);}));end.setOnClickListener(v->pickTime(LocalTime.parse(p.end),t->{p.end=t.toString();end.setText(p.end);}));weighted(r,start);r.addView(text("—",12,MUTED));weighted(r,end);list.addView(r);}}
    void confirm(String title,String message,Runnable yes){new AlertDialog.Builder(this).setTitle(title).setMessage(message).setPositiveButton("确认",(d,w)->yes.run()).setNegativeButton("取消",null).show();}
    void exportPicker(){Intent i=new Intent(Intent.ACTION_CREATE_DOCUMENT).setType("application/json").addCategory(Intent.CATEGORY_OPENABLE).putExtra(Intent.EXTRA_TITLE,"schedule++-"+LocalDate.now()+".json");startActivityForResult(i,90);}
    void restorePicker(){Intent i=new Intent(Intent.ACTION_OPEN_DOCUMENT).setType("*/*").addCategory(Intent.CATEGORY_OPENABLE);startActivityForResult(i,91);}
    @Override protected void onActivityResult(int request,int result,Intent data){
        super.onActivityResult(request,result,data);if(result!=RESULT_OK || data==null || data.getData()==null)return;Uri uri=data.getData();
        try{
            if(request==90){try(OutputStream out=getContentResolver().openOutputStream(uri,"wt")){if(out==null)throw new IOException("无法写入该位置");out.write(Store.encode(state).getBytes(StandardCharsets.UTF_8));}toast("备份已导出");}
            if(request==91){final State imported;try(InputStream in=getContentResolver().openInputStream(uri)){if(in==null)throw new IOException("无法读取文件");imported=Store.decode(Store.read(in));}
                int courseCount=0;for(Semester term:imported.allSemesters())courseCount+=term.courses.size();
                confirm("恢复备份？",imported.allSemesters().size()+" 个学期 · "+courseCount+" 门课程 · "+imported.allTasks().size()+" 条任务\n当前学期："+imported.semester+"\n\n将替换全部当前数据。建议先导出备份。",()->{try{State old=state;store.save(imported);state=imported;ready=true;week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));syncReminders(old==null?new State():old);render();toast("恢复完成");}catch(Exception e){toast(safeMessage(e));}});
            }
        }catch(Exception e){toast("操作未完成："+safeMessage(e));if(!ready)failedLoad(e);}
    }
}
