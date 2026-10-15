package app.scheduleplus;

import android.app.*;
import android.appwidget.*;
import android.content.*;
import android.net.Uri;
import android.os.*;
import android.view.View;
import android.widget.RemoteViews;
import java.time.*;
import java.time.format.DateTimeFormatter;
import java.util.concurrent.*;

/** Only reads local state. All item actions open the app; the widget cannot mark/delete tasks. */
public final class ScheduleWidget extends AppWidgetProvider {
    static final String REFRESH="app.scheduleplus.WIDGET_REFRESH";
    private static final ExecutorService WORK=Executors.newSingleThreadExecutor();
    public static void requestRefresh(Context context){Context c=context.getApplicationContext();c.sendBroadcast(new Intent(c,ScheduleWidget.class).setAction(REFRESH));}
    private void refresh(Context context){Context c=context.getApplicationContext();PendingResult result=goAsync();WORK.execute(()->{try{refreshNow(c);}catch(RuntimeException ignored){/* Launcher failures must not crash the app. */}finally{result.finish();}});}
    @Override public void onUpdate(Context c,AppWidgetManager manager,int[] ids){refresh(c);}
    @Override public void onAppWidgetOptionsChanged(Context c,AppWidgetManager manager,int id,Bundle options){refresh(c);}
    @Override public void onDeleted(Context c,int[] ids){refresh(c);}
    @Override public void onDisabled(Context c){c.getSystemService(AlarmManager.class).cancel(alarm(c));}
    @Override public void onReceive(Context c,Intent i){String action=i.getAction();if(REFRESH.equals(action)||Intent.ACTION_DATE_CHANGED.equals(action)||Intent.ACTION_TIME_CHANGED.equals(action)||Intent.ACTION_TIMEZONE_CHANGED.equals(action)||Intent.ACTION_BOOT_COMPLETED.equals(action)||Intent.ACTION_MY_PACKAGE_REPLACED.equals(action))refresh(c);else super.onReceive(c,i);}
    static void refreshNow(Context c){
        AppWidgetManager manager=AppWidgetManager.getInstance(c);int[] ids=manager.getAppWidgetIds(new ComponentName(c,ScheduleWidget.class));
        if(ids.length==0){c.getSystemService(AlarmManager.class).cancel(alarm(c));return;}
        WidgetData data=null;try{data=new WidgetData(new Store(c).load(),LocalDateTime.now());}catch(Exception ignored){/* Preserve original data and show a recoverable read error. */}
        for(int id:ids){manager.updateAppWidget(id,views(c,id,data));}
        manager.notifyAppWidgetViewDataChanged(ids,R.id.widget_courses);manager.notifyAppWidgetViewDataChanged(ids,R.id.widget_tasks);
        AlarmManager am=c.getSystemService(AlarmManager.class);long at=(data==null?LocalDateTime.now().plusMinutes(30):data.nextUpdate).atZone(ZoneId.systemDefault()).toInstant().toEpochMilli();
        try{if(Build.VERSION.SDK_INT<31 || am.canScheduleExactAlarms())am.setExact(AlarmManager.RTC,at,alarm(c));else am.set(AlarmManager.RTC,at,alarm(c));}catch(SecurityException denied){am.set(AlarmManager.RTC,at,alarm(c));}
    }
    private static PendingIntent alarm(Context c){return PendingIntent.getBroadcast(c,0,new Intent(c,ScheduleWidget.class).setAction(REFRESH).setData(Uri.parse("scheduleplus://widget/clock")),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
    static RemoteViews views(Context c,int id,WidgetData data){
        RemoteViews v=new RemoteViews(c.getPackageName(),R.layout.schedule_widget);
        String date=LocalDate.now().format(DateTimeFormatter.ofPattern("M月d日 E",java.util.Locale.CHINA));
        v.setTextViewText(R.id.widget_date,date+" · "+(data==null?"读取失败":data.semester));
        v.setTextViewText(R.id.widget_course_heading,c.getString(R.string.widget_courses,data==null?0:data.courses.size()));v.setTextViewText(R.id.widget_task_heading,c.getString(R.string.widget_tasks,data==null?0:data.tasks.size()));
        v.setTextViewText(R.id.widget_course_empty,data==null?"数据读取失败，点击打开 App 检查":"今天没有课程了");v.setTextViewText(R.id.widget_task_empty,data==null?"数据读取失败，点击打开 App 检查":"暂无未完成日程");
        for(int kind=0;kind<2;kind++){
            int list=kind==0?R.id.widget_courses:R.id.widget_tasks;
            Intent service=new Intent(c,WidgetListService.class).setData(Uri.parse("scheduleplus://widget/"+id+"/"+kind)).putExtra("kind",kind).putExtra(AppWidgetManager.EXTRA_APPWIDGET_ID,id);
            v.setRemoteAdapter(list,service);v.setEmptyView(list,kind==0?R.id.widget_course_empty:R.id.widget_task_empty);
            Intent open=new Intent(c,MainActivity.class).setAction("app.scheduleplus.WIDGET_OPEN").setData(Uri.parse("scheduleplus://widget/open/"+id+"/"+kind)).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP).putExtra("widgetPage",kind);
            v.setPendingIntentTemplate(list,PendingIntent.getActivity(c,0,open,PendingIntent.FLAG_UPDATE_CURRENT|(Build.VERSION.SDK_INT>=31?PendingIntent.FLAG_MUTABLE:0)));
        }
        v.setOnClickPendingIntent(R.id.widget_date,open(c,id,0,false));v.setOnClickPendingIntent(R.id.widget_course_heading,open(c,id,0,false));v.setOnClickPendingIntent(R.id.widget_task_heading,open(c,id,1,false));
        v.setOnClickPendingIntent(R.id.widget_course_empty,open(c,id,0,false));v.setOnClickPendingIntent(R.id.widget_task_empty,open(c,id,1,false));v.setOnClickPendingIntent(R.id.widget_add,open(c,id,1,true));
        v.setOnClickPendingIntent(R.id.widget_refresh,PendingIntent.getBroadcast(c,id,new Intent(c,ScheduleWidget.class).setAction(REFRESH).setData(Uri.parse("scheduleplus://widget/refresh/"+id)),PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE));return v;
    }
    private static PendingIntent open(Context c,int id,int page,boolean add){Intent i=new Intent(c,MainActivity.class).setData(Uri.parse("scheduleplus://widget/header/"+id+"/"+page+"/"+add)).putExtra("widgetPage",page).putExtra("widgetNewTask",add).addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP|Intent.FLAG_ACTIVITY_SINGLE_TOP);return PendingIntent.getActivity(c,0,i,PendingIntent.FLAG_UPDATE_CURRENT|PendingIntent.FLAG_IMMUTABLE);}
}
