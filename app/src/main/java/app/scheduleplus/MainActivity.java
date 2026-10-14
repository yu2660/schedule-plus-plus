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
    ScrollView pageScroll;
    HorizontalScrollView timetableScroll;
    final int[] scrollOffsets=new int[3];
    int renderedPage=-1,timetableOffset=0;
    boolean pageScrollReady=false;
    final Handler handler=new Handler(Looper.getMainLooper());
    final Runnable clockTick=new Runnable(){ public void run(){ if(ready) render(); handler.postDelayed(this,60000); }};
    @Override public void onCreate(Bundle saved) {
        super.onCreate(saved); store=new Store(this);
        getWindow().setStatusBarColor(BG); getWindow().setNavigationBarColor(Build.VERSION.SDK_INT>=27?Color.WHITE:ACCENT);
        getWindow().getDecorView().setSystemUiVisibility(View.SYSTEM_UI_FLAG_LIGHT_STATUS_BAR|(Build.VERSION.SDK_INT>=27?View.SYSTEM_UI_FLAG_LIGHT_NAVIGATION_BAR:0));
        try {
            state=store.load(); ready=true;Reminders.ensureChannel(this); week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));
            if(saved!=null) { page=saved.getInt("page",0); week=saved.getInt("week",week); showDone=saved.getBoolean("done");int[] offsets=saved.getIntArray("scrollOffsets");if(offsets!=null && offsets.length==3)System.arraycopy(offsets,0,scrollOffsets,0,3);timetableOffset=saved.getInt("timetableOffset",0); }
            render(); openIntent(getIntent());
        } catch(Exception e) { failedLoad(e); }
    }
    @Override protected void onSaveInstanceState(Bundle out) { rememberScroll();super.onSaveInstanceState(out); out.putInt("page",page);out.putInt("week",week);out.putBoolean("done",showDone);out.putIntArray("scrollOffsets",scrollOffsets.clone());out.putInt("timetableOffset",timetableOffset); }
    @Override protected void onNewIntent(Intent i) { super.onNewIntent(i); setIntent(i); openIntent(i); }
    void openIntent(Intent i) {
        if(!ready || i==null)return;
        if(i.hasExtra("taskId")) {
            String id=i.getStringExtra("taskId");i.removeExtra("taskId");
            try { state=store.load();Semester owner=state.taskOwner(id);if(owner!=null){if(detailDialog!=null)detailDialog.dismiss();if(!state.id.equals(owner.id))switchToSemester(owner.id);Task task=state.task(id);if(task!=null)editTask(task,null);} }
            catch(Exception e){toast(safeMessage(e));}
        }
        if(i.getBooleanExtra("reminderSettings",false)){i.removeExtra("reminderSettings");page=2;render();}
    }
    @Override protected void onResume() {
        super.onResume(); if(ready) { try { state=store.load();syncReminders(state);render(); } catch(Exception e) { ready=false; failedLoad(e); } }
        handler.removeCallbacks(clockTick); handler.postDelayed(clockTick,60000);
    }
    @Override protected void onPause(){ super.onPause(); handler.removeCallbacks(clockTick); }
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
        rememberScroll();renderedPage=page;pageScrollReady=false;timetableScroll=null;
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
        final int pageIndex=page,targetY=scrollOffsets[pageIndex],targetX=timetableOffset;final HorizontalScrollView horizontal=timetableScroll;
        scroll.setOnScrollChangeListener((v,x,y,oldX,oldY)->{if(pageScroll==scroll && pageScrollReady)scrollOffsets[pageIndex]=y;});
        scroll.getViewTreeObserver().addOnGlobalLayoutListener(new android.view.ViewTreeObserver.OnGlobalLayoutListener(){public void onGlobalLayout(){
            scroll.getViewTreeObserver().removeOnGlobalLayoutListener(this);if(pageScroll!=scroll)return;
            scroll.scrollTo(0,targetY);if(horizontal!=null)horizontal.scrollTo(targetX,0);pageScrollReady=true;scrollOffsets[pageIndex]=scroll.getScrollY();
        }});
    }
    void addMenu(){ new AlertDialog.Builder(this).setTitle("添加到 Schedule++").setItems(new String[]{"添加任务","添加课程"},(d,w)->{if(w==0)editTask(null,null);else editCourse(null);}).show(); }
    void timetable(){
        LinearLayout controls=row();weighted(controls,button("‹",()->{if(week>1){week--;render();}}));
        Button selector=button("第 "+week+" 周 ▾",()->{String[] choices=new String[state.weeks];for(int i=0;i<choices.length;i++)choices[i]="第 "+(i+1)+" 周";new AlertDialog.Builder(this).setTitle("选择教学周").setSingleChoiceItems(choices,week-1,(d,w)->{week=w+1;d.dismiss();render();}).show();});
        controls.addView(selector,new LinearLayout.LayoutParams(0,-2,3));weighted(controls,button("›",()->{if(week<state.weeks){week++;render();}}));body.addView(controls);
        int current=state.currentWeek(LocalDate.now());LocalDate monday=LocalDate.parse(state.monday).plusWeeks(week-1);
        LinearLayout dates=row();weighted(dates,text(monday.format(DateTimeFormatter.ofPattern("M月d日"))+" — "+monday.plusDays(6).format(DateTimeFormatter.ofPattern("M月d日")),12,MUTED));dates.addView(button("回到本周",()->{week=Math.max(1,Math.min(state.weeks,current));render();}));body.addView(dates);
        if(current<1 || current>state.weeks)body.addView(text("当前日期不在本学期内，正在查看第 "+week+" 周",12,MUTED));
        if(state.courses.isEmpty()){space(body,12);hint(body,"从第一门课开始","先在设置里调整学期和作息，再添加课程。点击右下角 ＋ 随时记录任务。");body.addView(button("添加第一门课程",()->editCourse(null)));return;}
        // A minimum column width keeps course names readable on narrow screens.
        HorizontalScrollView horizontal=((DirectionLockedScrollView)pageScroll).createHorizontalScroller();timetableScroll=horizontal;horizontal.setHorizontalScrollBarEnabled(false);
        LinearLayout table=row();table.setGravity(Gravity.TOP);
        LinearLayout grid=row();grid.setGravity(Gravity.TOP);int cellWidth=Math.max(76,(int)(getResources().getDisplayMetrics().widthPixels/getResources().getDisplayMetrics().density-76)/7);
        LinearLayout axis=column();axis.setContentDescription("固定节次与时间栏");axis.setBackgroundColor(BG);axis.addView(text("节次",11,MUTED),new LinearLayout.LayoutParams(dp(48),dp(48)));
        for(int i=0;i<state.slots.size();i++){Slot p=state.slots.get(i);TextView t=text((i+1)+"\n"+p.start+"\n"+p.end,10,MUTED);t.setGravity(Gravity.CENTER);axis.addView(t,new LinearLayout.LayoutParams(dp(48),dp(66)));}table.addView(axis,new LinearLayout.LayoutParams(dp(48),-2));
        for(int day=1;day<=7;day++){
            final int chosenDay=day;LinearLayout col=column();LocalDate date=monday.plusDays(day-1);boolean today=date.equals(LocalDate.now());
            TextView header=text(DAYS[day-1]+"\n"+date.getDayOfMonth(),12,today?ACCENT:MUTED);header.setGravity(Gravity.CENTER);col.addView(header,new LinearLayout.LayoutParams(dp(cellWidth),dp(48)));
            for(CourseBlock block:courseBlocks(state,day,week)){
                final int selectedSection=block.first;List<Course> matching=block.courses;int span=block.last-block.first+1;
                TextView cell=text("",12,INK);cell.setPadding(dp(7),dp(7),dp(5),dp(4));cell.setGravity(Gravity.TOP);cell.setMaxLines(Math.max(4,span*4));
                if(!matching.isEmpty()){
                    Course c=matching.get(0);cell.setText(getString(R.string.course_span,c.name,block.first,block.last,state.slots.get(block.first-1).start,state.slots.get(block.last-1).end,(c.location.isEmpty()?"":"\n"+c.location)+(matching.size()>1?getString(R.string.course_overlap,matching.size()):"")));cell.setTextColor(COLORS[c.color]);cell.setBackground(background(PASTELS[c.color],10));
                    cell.setContentDescription(DAYS[day-1]+" 第"+block.first+"至"+block.last+"节 "+cell.getText());cell.setOnClickListener(v->{if(matching.size()==1)courseDetail(c.id);else chooseCourses(matching);});
                } else {cell.setBackground(background(today?0xffF0F0FC:0xffEFF1F7,10));cell.setContentDescription(DAYS[day-1]+" 第"+block.first+"节，添加课程");cell.setOnClickListener(v->editCourseAt(chosenDay,selectedSection));}
                LinearLayout.LayoutParams p=new LinearLayout.LayoutParams(dp(cellWidth-4),dp(66*span-4));p.setMargins(dp(2),dp(2),dp(2),dp(2));col.addView(cell,p);
            }grid.addView(col);
        }horizontal.addView(grid);table.addView(horizontal,new LinearLayout.LayoutParams(0,-2,1));body.addView(table);space(body,12);body.addView(text("横滑看星期 · 竖滑看节次 · 点击课程查看任务",12,MUTED));
    }
    void chooseCourses(List<Course> courses){String[] names=new String[courses.size()];for(int i=0;i<names.length;i++)names[i]=courses.get(i).name;new AlertDialog.Builder(this).setTitle("这个节次有重叠课程").setItems(names,(d,w)->courseDetail(courses.get(w).id)).show();}
    void agenda(){
        LinearLayout stats=card(body);int pending=0,late=0;for(Task t:state.tasks)if(!t.done){pending++;if(t.deadline().isBefore(LocalDateTime.now()))late++;}
        stats.addView(title(pending+" 项待完成",24));stats.addView(text(late==0?"按自己的节奏，逐项完成":"其中 "+late+" 项已逾期",13,late==0?MUTED:0xffB34053));
        LinearLayout filters=row();weighted(filters,button(showDone?"待完成":"● 待完成",()->{showDone=false;render();}));weighted(filters,button(showDone?"● 已完成":"已完成",()->{showDone=true;render();}));body.addView(filters);
        List<Task> tasks=state.sortedTasks(null,showDone);String last="";
        if(tasks.isEmpty())hint(body,showDone?"还没有完成记录":"暂时没有待办","点击右下角 ＋ 添加作业、报告、考试或个人安排。");
        for(Task t:tasks){if(!t.date.equals(last)){last=t.date;label(body,dateLabel(t));}taskCard(body,t,null);}
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
    void showDetail(AlertDialog dialog){if(detailDialog!=null)detailDialog.dismiss();detailDialog=dialog;dialog.setOnDismissListener(d->{if(detailDialog==dialog)detailDialog=null;});dialog.show();}
    void keepParent(AlertDialog editor,Dialog parent,Runnable refresh){State before=state;editor.setOnDismissListener(d->{if(editorDialog==editor)editorDialog=null;if(!isFinishing() && !isDestroyed() && parent.isShowing() && state!=before && refresh!=null)refresh.run();});}
    void refreshCourseTasks(LinearLayout list,String id,boolean completed,AlertDialog owner){list.removeAllViews();List<Task> tasks=state.sortedTasks(id,completed);if(tasks.isEmpty())list.addView(text(completed?"暂无已完成记录":"这门课暂时没有未完成任务",14,MUTED));for(Task t:tasks)taskCard(list,t,owner,()->refreshCourseTasks(list,id,completed,owner));}
    String meetingLabel(Meeting m){return DAYS[m.day-1]+" · 第 "+m.first+"–"+m.last+" 节 · 周次 "+joinWeeks(m.weeks);}
    String joinWeeks(Set<Integer> weeks){StringBuilder b=new StringBuilder();for(int w:weeks){if(b.length()>0)b.append('、');b.append(w);}return b.toString();}
    LinearLayout form(){LinearLayout f=column();f.setPadding(dp(20),dp(4),dp(20),dp(20));return f;}
    EditText field(LinearLayout f,String label,String value,int lines){label(f,label);EditText e=new EditText(this);e.setText(value);e.setTextSize(15);e.setSingleLine(lines==1);if(lines>1){e.setMinLines(lines);e.setGravity(Gravity.TOP);}f.addView(e,new LinearLayout.LayoutParams(-1,-2));return e;}
    AlertDialog formDialog(String title,LinearLayout form,Runnable save){ScrollView scroll=new ScrollView(this);scroll.setFillViewport(false);scroll.addView(form);AlertDialog d=new AlertDialog.Builder(this).setTitle(title).setView(scroll).setPositiveButton("保存",null).setNegativeButton("取消",null).create();d.setOnShowListener(x->{editorDialog=d;d.getButton(-1).setOnClickListener(v->save.run());});d.setOnDismissListener(x->{if(editorDialog==d)editorDialog=null;});return d;}
    void pickDate(LocalDate initial,Consumer<LocalDate> result){new DatePickerDialog(this,(v,y,m,d)->result.accept(LocalDate.of(y,m+1,d)),initial.getYear(),initial.getMonthValue()-1,initial.getDayOfMonth()).show();}
    void pickTime(LocalTime initial,Consumer<LocalTime> result){new TimePickerDialog(this,(v,h,m)->result.accept(LocalTime.of(h,m)),initial.getHour(),initial.getMinute(),true).show();}
    void pickDateTime(LocalDateTime initial,Consumer<LocalDateTime> result){pickDate(initial.toLocalDate(),d->pickTime(initial.toLocalTime(),t->result.accept(d.atTime(t))));}
    AlertDialog editTask(Task original,String preselected){
        Task draft=new Task();if(original!=null){draft.id=original.id;draft.title=original.title;draft.courseId=original.courseId;draft.date=original.date;draft.time=original.time;draft.notes=original.notes;draft.reminder=original.reminder;draft.done=original.done;draft.reminded=original.reminded;}
        else {draft.courseId=preselected;draft.date=LocalDate.now().toString();}
        LinearLayout f=form();EditText name=field(f,"任务标题 *",draft.title,1);
        label(f,"关联课程（可选）");Spinner courses=new Spinner(this);List<String> ids=new ArrayList<>(),names=new ArrayList<>();ids.add(null);names.add("个人任务 · 不关联课程");for(Course c:state.courses){ids.add(c.id);names.add(c.name);}courses.setAdapter(new ArrayAdapter<>(this,android.R.layout.simple_spinner_dropdown_item,names));courses.setSelection(Math.max(0,ids.indexOf(draft.courseId)));f.addView(courses);
        label(f,"截止日期 * / 时间选填");Button date=button(draft.date,()->{});date.setOnClickListener(v->pickDate(LocalDate.parse(draft.date),d->{draft.date=d.toString();date.setText(draft.date);}));f.addView(date);
        LinearLayout timeRow=row();Button time=button(draft.time==null?"＋ 添加具体时间":draft.time,()->{});time.setOnClickListener(v->pickTime(draft.time==null?LocalTime.of(23,59):LocalTime.parse(draft.time),t->{draft.time=t.toString();time.setText(draft.time);}));weighted(timeRow,time);timeRow.addView(button("清除时间",()->{draft.time=null;time.setText("＋ 添加具体时间");}));f.addView(timeRow);
        EditText notes=field(f,"补充说明",draft.notes,3);
        label(f,"提醒（可选）");Button reminder=button(draft.reminder==null?"＋ 选择提醒日期和时间":draft.reminder.replace('T',' '),()->{});reminder.setOnClickListener(v->pickDateTime(draft.reminder==null?LocalDateTime.now().plusHours(1).withSecond(0).withNano(0):LocalDateTime.parse(draft.reminder),r->{draft.reminder=r.toString();draft.reminded=false;reminder.setText(draft.reminder.replace('T',' '));}));f.addView(reminder);f.addView(button("不提醒",()->{draft.reminder=null;draft.reminded=false;reminder.setText("＋ 选择提醒日期和时间");}));
        f.addView(text("未填写时间的任务，在截止日期当天结束后算逾期。",12,MUTED));
        final AlertDialog[] dialog={null};dialog[0]=formDialog(original==null?"添加任务":"编辑任务",f,()->{
            draft.title=name.getText().toString().trim();draft.notes=notes.getText().toString();draft.courseId=ids.get(courses.getSelectedItemPosition());
            if(draft.reminder!=null && !draft.reminded && draft.reminderMillis()<=System.currentTimeMillis()){toast("请选择未来的提醒时间，或关闭提醒");return;}
            if(commit(s->{s.tasks.removeIf(t->t.id.equals(draft.id));s.tasks.add(draft);})){dialog[0].dismiss();if(draft.reminder!=null && !draft.done)ensureNotifications();}
        });
        if(original!=null)f.addView(button("删除任务",()->confirm("删除这条任务？","删除后无法撤销。",()->{if(commit(s->s.tasks.removeIf(t->t.id.equals(draft.id))))dialog[0].dismiss();})));
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
        label(f,"上课时段 *（可添加多个）");LinearLayout list=column();f.addView(list);Runnable refresh=()->{list.removeAllViews();for(Meeting m:draft.meetings){LinearLayout c=card(list);c.addView(text(meetingLabel(m),13,INK));LinearLayout actions=row();weighted(actions,button("修改",()->meetingEditor(m,n->{int index=draft.meetings.indexOf(m);draft.meetings.set(index,n);refreshMeetings(list,draft);})));weighted(actions,button("移除",()->{draft.meetings.remove(m);refreshMeetings(list,draft);}));c.addView(actions);}};refresh.run();
        f.addView(button("＋ 添加上课时段",()->meetingEditor(null,m->{draft.meetings.add(m);refreshMeetings(list,draft);})));
        final AlertDialog[] dialog={null};dialog[0]=formDialog(existing?"编辑课程":"添加课程",f,()->{
            draft.name=name.getText().toString().trim();draft.location=location.getText().toString().trim();draft.teacher=teacher.getText().toString().trim();draft.color=color.getSelectedItemPosition();
            Runnable save=()->{if(commit(s->{s.courses.removeIf(c->c.id.equals(draft.id));s.courses.add(draft);}))dialog[0].dismiss();};
            boolean clash=false;for(Meeting m:draft.meetings){for(Course c:state.courses)if(!c.id.equals(draft.id))for(Meeting n:c.meetings)if(conflicts(m,n))clash=true;}
            for(int i=0;i<draft.meetings.size();i++)for(int j=i+1;j<draft.meetings.size();j++)if(conflicts(draft.meetings.get(i),draft.meetings.get(j)))clash=true;
            if(clash)confirm("检测到课程时间重叠","仍然保存吗？课表会显示重叠标记。",save);else save.run();
        });
        if(existing)f.addView(button("删除课程",()->confirm("删除「"+original.name+"」？","相关任务会保留，并转为不关联课程的个人任务。",()->{if(commit(s->{s.courses.removeIf(c->c.id.equals(draft.id));for(Task t:s.tasks)if(draft.id.equals(t.courseId))t.courseId=null;}))dialog[0].dismiss();})));
        dialog[0].show();return dialog[0];
    }
    void refreshMeetings(LinearLayout list,Course draft){list.removeAllViews();for(Meeting m:draft.meetings){LinearLayout c=card(list);c.addView(text(meetingLabel(m),13,INK));LinearLayout actions=row();weighted(actions,button("修改",()->meetingEditor(m,n->{draft.meetings.set(draft.meetings.indexOf(m),n);refreshMeetings(list,draft);})));weighted(actions,button("移除",()->{draft.meetings.remove(m);refreshMeetings(list,draft);}));c.addView(actions);}}
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
        LinearLayout backup=card(body);backup.addView(title("备份与恢复",18));space(backup,8);backup.addView(text("备份包含全部学期、课程和任务，兼容恢复旧版备份。文件为明文 JSON，请保存在你信任的位置。",13,MUTED));backup.addView(button("导出全部学期备份",()->exportPicker()));backup.addView(button("从备份恢复",()->restorePicker()));
        hint(body,"Schedule++  0.2.3","安卓原生 · 数据保存在本机\n教务导入将在后续版本考虑。\n参考：拾光课程表（Apache-2.0）；详见项目开源说明。");
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
        Semester term=new Semester();term.semester="新学期";LinearLayout f=form();EditText name=field(f,"学期名称 *",term.semester,1),note=field(f,"备注（可选）","",2);
        label(f,"第一教学周的周一");Button date=button(term.monday,()->{});date.setOnClickListener(v->pickDate(LocalDate.parse(term.monday),d->{if(d.getDayOfWeek()!=DayOfWeek.MONDAY){toast("请选择周一");return;}term.monday=d.toString();date.setText(term.monday);}));f.addView(date);EditText weeks=field(f,"教学周总数（1–60）","20",1);weeks.setInputType(InputType.TYPE_CLASS_NUMBER);
        f.addView(text("新学期的课程和任务从空白开始。保存后可在「学期与作息设置」中自定义每日节数和时间。",13,MUTED));
        final AlertDialog[] dialog={null};dialog[0]=formDialog("新建学期",f,()->{try{term.semester=name.getText().toString().trim();term.description=note.getText().toString().trim();term.weeks=Integer.parseInt(weeks.getText().toString());if(commit(s->s.addSemester(term))){dialog[0].dismiss();week=Math.max(1,Math.min(state.weeks,state.currentWeek(LocalDate.now())));showDone=false;render();}}catch(Exception e){toast(safeMessage(e));}});dialog[0].show();
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
